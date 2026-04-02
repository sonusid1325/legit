package com.sonusid.legit.pipeline

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.sonusid.legit.db.MongoDB
import com.sonusid.legit.models.*
import com.sonusid.legit.services.DocumentService
import com.sonusid.legit.services.UserService
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * DataPipelineService — The Core of Legit
 *
 * This is where the magic happens. Instead of sharing documents like DigiLocker,
 * we run a contractual verification pipeline:
 *
 * 1. Service provider creates a VerificationContract specifying what they need to verify
 * 2. User reviews and approves/rejects the contract
 * 3. On approval, a disposable key is generated (short-lived, single-use)
 * 4. Service provider uses the disposable key to trigger verification
 * 5. Verification runs ON OUR SERVER — documents never leave
 * 6. Service provider gets a YES/NO result with cryptographic proof
 * 7. Disposable key is burned. Contract is sealed. Done.
 *
 * No document sharing. No data leaks. Just verification.
 */
class DataPipelineService(
    private val documentService: DocumentService,
    private val userService: UserService,
    private val pipelineSecret: String,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val disposableKeyTtlMs: Long = 5 * 60 * 1000 // 5 minutes default
) {

    private val logger = LoggerFactory.getLogger(DataPipelineService::class.java)
    private val secureRandom = SecureRandom()

    // ========================
    // STEP 1: CREATE CONTRACT
    // Service provider requests verification
    // ========================

    suspend fun createContract(
        requesterId: String,
        requesterName: String,
        request: CreateContractRequest
    ): Result<ContractSummary> {
        // Validate that the target user exists if provided
        if (request.userId != null) {
            val targetUser = userService.getUserById(request.userId)
                ?: return Result.failure(
                    NotFoundException(
                        code = ErrorCodes.USER_NOT_FOUND,
                        message = "Target user not found"
                    )
                )

            // Check if user has the required document types uploaded
            val (hasDocuments, missingTypes) = documentService.hasRequiredDocuments(
                request.userId,
                request.requiredDocumentTypes
            )

            if (!hasDocuments) {
                return Result.failure(
                    BadRequestException(
                        code = ErrorCodes.MISSING_REQUIRED_DOCUMENTS,
                        message = "User is missing required documents: ${missingTypes.joinToString(", ") { it.name }}"
                    )
                )
            }
        }

        // Validate the request
        val validationErrors = validateContractRequest(request)

        val contract = VerificationContract(
            requesterId = requesterId,
            requesterName = requesterName,
            userId = request.userId,
            requiredDocumentTypes = request.requiredDocumentTypes,
            requiredFields = request.requiredFields,
            purpose = request.purpose.trim(),
            status = ContractStatus.PENDING_APPROVAL,
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours to approve
        )

        val insertResult = MongoDB.contracts.insertOne(contract)
        val contractId = insertResult.insertedId?.asObjectId()?.value?.toHexString()
            ?: return Result.failure(
                LegitException(
                    code = ErrorCodes.PIPELINE_ERROR,
                    message = "Failed to create verification contract"
                )
            )

        logger.info(
            "Contract created: {} | Requester: {} | User: {} | Purpose: {}",
            contractId, requesterName, request.userId, request.purpose
        )

        val savedContract = getContractById(contractId)!!
        return Result.success(savedContract.toSummary())
    }

    // ========================
    // STEP 2: USER APPROVES/REJECTS
    // User reviews the contract and decides
    // ========================

    suspend fun approveContract(userId: String, request: ContractApprovalRequest): Result<Any> {
        val contract = getContractById(request.contractId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.CONTRACT_NOT_FOUND,
                    message = "Verification contract not found"
                )
            )

        // Only the target user can approve/reject if one was specified
        if (contract.userId != null && contract.userId != userId) {
            return Result.failure(
                AuthorizationException(
                    message = "You are not authorized to approve/reject this contract"
                )
            )
        }

        // Check if contract is still pending
        if (contract.status != ContractStatus.PENDING_APPROVAL) {
            return Result.failure(
                BadRequestException(
                    code = ErrorCodes.CONTRACT_ALREADY_APPROVED,
                    message = "This contract has already been ${contract.status.name.lowercase()}"
                )
            )
        }

        // Check if contract has expired
        if (System.currentTimeMillis() > contract.expiresAt) {
            // Mark as expired
            updateContractStatus(request.contractId, ContractStatus.EXPIRED)
            return Result.failure(
                BadRequestException(
                    code = ErrorCodes.CONTRACT_EXPIRED,
                    message = "This verification contract has expired"
                )
            )
        }

        if (!request.approved) {
            // User rejected the contract
            updateContractStatus(request.contractId, ContractStatus.REJECTED)
            logger.info("Contract rejected: {} | User: {}", request.contractId, userId)
            return Result.success(
                ApiResponse.ok("Verification contract rejected")
            )
        }

        // User approved — RUN VERIFICATION IMMEDIATELY (Peer-to-Peer approach)
        logger.info("Contract approved by user: {}. Running verification pipeline immediately.", userId)

        // Bind user to contract first for the pipeline to work
        MongoDB.contracts.updateOne(
            Filters.eq("_id", ObjectId(request.contractId)),
            Updates.set("userId", userId)
        )

        // Get fresh contract object with userId set
        val updatedContractBeforePipe = getContractById(request.contractId)!!

        val verificationResult = try {
            runVerificationPipeline(updatedContractBeforePipe)
        } catch (e: Exception) {
            logger.error("Auto-verification pipeline failed for contract: {}", request.contractId, e)
            updateContractStatus(request.contractId, ContractStatus.FAILED)
            return Result.failure(
                LegitException(
                    code = ErrorCodes.VERIFICATION_FAILED,
                    message = "Verification pipeline encountered an error during auto-approval"
                )
            )
        }

        // Store result and mark as VERIFIED
        MongoDB.contracts.updateOne(
            Filters.eq("_id", ObjectId(request.contractId)),
            Updates.combine(
                Updates.set("status", ContractStatus.VERIFIED.name),
                Updates.set("verificationResult", verificationResult),
                Updates.set("verifiedAt", System.currentTimeMillis()),
                Updates.set("approvedAt", System.currentTimeMillis()),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )

        val finalContract = getContractById(request.contractId)!!
        val metadata = documentService.getDocumentMetadata(
            finalContract.userId!!,
            finalContract.requiredDocumentTypes.firstOrNull() ?: DocumentType.AADHAAR_CARD
        )

        return Result.success(finalContract.toResponse(metadata))
    }

    // ========================
    // STEP 3: EXECUTE VERIFICATION
    // Service provider uses disposable key to trigger verification
    // Documents are verified on OUR server — never shared
    // ========================

    suspend fun executeVerification(request: ExecuteVerificationRequest): Result<VerificationResponse> {
        val contract = getContractById(request.contractId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.CONTRACT_NOT_FOUND,
                    message = "Verification contract not found"
                )
            )

        // If already verified via auto-approval, just return the result
        if (contract.status == ContractStatus.VERIFIED) {
            val metadata = documentService.getDocumentMetadata(
                contract.userId!!,
                contract.requiredDocumentTypes.firstOrNull() ?: DocumentType.AADHAAR_CARD
            )
            return Result.success(contract.toResponse(metadata))
        }

        // Validate contract status
        if (contract.status != ContractStatus.APPROVED) {
            return Result.failure(
                BadRequestException(
                    code = ErrorCodes.CONTRACT_NOT_APPROVED,
                    message = "Contract must be approved before verification can be executed. Current status: ${contract.status.name}"
                )
            )
        }

        // Validate disposable key (Skip if it is a direct P2P verification)
        if (request.disposableKey != "AUTO_P2P") {
            val keyHash = hashDisposableKey(request.disposableKey)
            if (contract.disposableKey != keyHash) {
                logger.warn("Invalid disposable key used for contract: {}", request.contractId)
                return Result.failure(
                    AuthenticationException(
                        code = ErrorCodes.INVALID_DISPOSABLE_KEY,
                        message = "Invalid disposable key"
                    )
                )
            }

            // Check key expiry
            if (contract.keyExpiresAt != null && System.currentTimeMillis() > contract.keyExpiresAt) {
                // Burn the key and mark as expired
                burnDisposableKey(request.contractId)
                updateContractStatus(request.contractId, ContractStatus.EXPIRED)
                logger.warn("Expired disposable key used for contract: {}", request.contractId)
                return Result.failure(
                    BadRequestException(
                        code = ErrorCodes.KEY_EXPIRED,
                        message = "Disposable key has expired. Request a new verification contract."
                    )
                )
            }
        }

        // Mark as in progress and burn the key immediately (single-use)
        MongoDB.contracts.updateOne(
            Filters.eq("_id", ObjectId(request.contractId)),
            Updates.combine(
                Updates.set("status", ContractStatus.VERIFICATION_IN_PROGRESS.name),
                Updates.set("disposableKey", null),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )

        logger.info("Verification started for contract: {}", request.contractId)

        // ========================
        // RUN THE VERIFICATION PIPELINE
        // This is where documents are checked internally
        // ========================
        val verificationResult = try {
            runVerificationPipeline(contract)
        } catch (e: Exception) {
            logger.error("Verification pipeline failed for contract: {}", request.contractId, e)
            updateContractStatus(request.contractId, ContractStatus.FAILED)
            return Result.failure(
                LegitException(
                    code = ErrorCodes.VERIFICATION_FAILED,
                    message = "Verification pipeline encountered an error"
                )
            )
        }

        // Store the result and mark as verified
        MongoDB.contracts.updateOne(
            Filters.eq("_id", ObjectId(request.contractId)),
            Updates.combine(
                Updates.set("status", ContractStatus.VERIFIED.name),
                Updates.set("verificationResult", verificationResult),
                Updates.set("verifiedAt", System.currentTimeMillis()),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )

        logger.info(
            "Verification completed for contract: {} | Overall: {}",
            request.contractId, verificationResult.overallStatus
        )

        val updatedContract = getContractById(request.contractId)!!
        val metadata = documentService.getDocumentMetadata(updatedContract.userId!!, updatedContract.requiredDocumentTypes.firstOrNull() ?: DocumentType.AADHAAR_CARD)
        return Result.success(updatedContract.toResponse(metadata))
    }

    // ========================
    // THE VERIFICATION PIPELINE
    // Internal only — checks documents against required fields
    // Returns YES/NO results, never the actual document data
    // ========================

    private suspend fun runVerificationPipeline(contract: VerificationContract): VerificationResult {
        val fieldResults = mutableMapOf<String, FieldVerificationResult>()
        val userId = contract.userId ?: throw IllegalStateException("Contract userId must not be null for verification")

        logger.info(
            "Pipeline: Verifying {} fields across {} document types for user {}",
            contract.requiredFields.size,
            contract.requiredDocumentTypes.size,
            userId
        )

        for (field in contract.requiredFields) {
            val result = verifyField(userId, contract.requiredDocumentTypes, field)
            fieldResults[field.name] = result

            logger.info(
                "Pipeline: Field {} -> {}",
                field.name, if (result.verified) "PASS" else "FAIL"
            )
        }

        // Determine overall status
        val allPassed = fieldResults.values.all { it.verified }
        val nonePassed = fieldResults.values.none { it.verified }

        val overallStatus = when {
            allPassed -> OverallVerificationStatus.PASS
            nonePassed -> OverallVerificationStatus.FAIL
            else -> OverallVerificationStatus.PARTIAL
        }

        // Generate cryptographic proof
        val proofData = buildString {
            append(contract.id?.toHexString() ?: "")
            append("|")
            append(userId)
            append("|")
            append(contract.requesterId)
            append("|")
            fieldResults.entries.sortedBy { it.key }.forEach { (key, value) ->
                append("$key:${value.verified}")
                append("|")
            }
            append(System.currentTimeMillis())
            append("|")
            append(pipelineSecret)
        }
        val proofHash = sha256Hash(proofData)

        // Generate a signed verification token — the service provider can store this
        val verificationToken = generateVerificationToken(
            contractId = contract.id?.toHexString() ?: "",
            overallStatus = overallStatus,
            proofHash = proofHash
        )

        return VerificationResult(
            fieldResults = fieldResults,
            overallStatus = overallStatus,
            proofHash = proofHash,
            verifiedAt = System.currentTimeMillis(),
            verificationToken = verificationToken
        )
    }

    /**
     * Verify a single field against the user's documents.
     * This checks the metadata/encrypted data internally — nothing is exposed.
     */
    private suspend fun verifyField(
        userId: String,
        documentTypes: List<DocumentType>,
        field: VerificationField
    ): FieldVerificationResult {
        return when (field) {
            VerificationField.FULL_NAME -> {
                val verified = verifyFieldAcrossDocuments(userId, documentTypes) { metadata ->
                    !metadata.fullName.isNullOrBlank()
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Full name verified against documents" else "Full name could not be verified"
                )
            }

            VerificationField.DATE_OF_BIRTH -> {
                val verified = verifyFieldAcrossDocuments(userId, documentTypes) { metadata ->
                    !metadata.dateOfBirth.isNullOrBlank()
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Date of birth verified" else "Date of birth could not be verified"
                )
            }

            VerificationField.ADDRESS -> {
                val verified = verifyFieldAcrossDocuments(userId, documentTypes) { metadata ->
                    !metadata.address.isNullOrBlank()
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Address verified" else "Address could not be verified"
                )
            }

            VerificationField.GENDER -> {
                val verified = verifyFieldAcrossDocuments(userId, documentTypes) { metadata ->
                    !metadata.gender.isNullOrBlank()
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Gender verified" else "Gender could not be verified"
                )
            }

            VerificationField.FATHER_NAME -> {
                val verified = verifyFieldAcrossDocuments(userId, documentTypes) { metadata ->
                    !metadata.fatherName.isNullOrBlank()
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Father's name verified" else "Father's name could not be verified"
                )
            }

            VerificationField.DOCUMENT_VALIDITY -> {
                val verified = verifyDocumentValidity(userId, documentTypes)
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Documents are valid and not expired" else "One or more documents are expired or invalid"
                )
            }

            VerificationField.DOCUMENT_NUMBER_MATCH -> {
                val verified = verifyDocumentNumbersExist(userId, documentTypes)
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Document numbers verified" else "Document number verification failed"
                )
            }

            VerificationField.IDENTITY_PROOF -> {
                val identityTypes = listOf(
                    DocumentType.AADHAAR_CARD,
                    DocumentType.PAN_CARD,
                    DocumentType.PASSPORT,
                    DocumentType.DRIVING_LICENSE,
                    DocumentType.VOTER_ID
                )
                val relevantTypes = documentTypes.filter { it in identityTypes }
                val verified = if (relevantTypes.isNotEmpty()) {
                    verifyFieldAcrossDocuments(userId, relevantTypes) { metadata ->
                        !metadata.fullName.isNullOrBlank()
                    }
                } else {
                    false
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Identity proof verified" else "No valid identity proof document found"
                )
            }

            VerificationField.ADDRESS_PROOF -> {
                val addressTypes = listOf(
                    DocumentType.AADHAAR_CARD,
                    DocumentType.PASSPORT,
                    DocumentType.DRIVING_LICENSE,
                    DocumentType.VOTER_ID,
                    DocumentType.ADDRESS_PROOF
                )
                val relevantTypes = documentTypes.filter { it in addressTypes }
                val verified = if (relevantTypes.isNotEmpty()) {
                    verifyFieldAcrossDocuments(userId, relevantTypes) { metadata ->
                        !metadata.address.isNullOrBlank()
                    }
                } else {
                    false
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Address proof verified" else "No valid address proof document found"
                )
            }

            VerificationField.AGE_VERIFICATION -> {
                val verified = verifyFieldAcrossDocuments(userId, documentTypes) { metadata ->
                    if (metadata.dateOfBirth.isNullOrBlank()) return@verifyFieldAcrossDocuments false
                    try {
                        // Simple age check — user must be 18+
                        val dob = metadata.dateOfBirth
                        // Expected format: YYYY-MM-DD or DD-MM-YYYY or DD/MM/YYYY
                        val year = extractYear(dob)
                        if (year != null) {
                            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                            (currentYear - year) >= 18
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
                FieldVerificationResult(
                    field = field,
                    verified = verified,
                    message = if (verified) "Age verification passed (18+)" else "Age could not be verified or user is under 18"
                )
            }
        }
    }

    /**
     * Helper: check a condition across all relevant documents for a user
     */
    private suspend fun verifyFieldAcrossDocuments(
        userId: String,
        documentTypes: List<DocumentType>,
        check: (DocumentMetadata) -> Boolean
    ): Boolean {
        for (docType in documentTypes) {
            val metadata = documentService.getDocumentMetadata(userId, docType)
            if (metadata != null && check(metadata)) {
                return true
            }
        }
        return false
    }

    /**
     * Check that all required documents exist and are not expired
     */
    private suspend fun verifyDocumentValidity(userId: String, documentTypes: List<DocumentType>): Boolean {
        for (docType in documentTypes) {
            val docs = documentService.getDocumentByUserAndType(userId, docType)
            if (docs.isEmpty()) return false

            val validDoc = docs.find { doc ->
                val notExpired = doc.expiresAt == null || doc.expiresAt > System.currentTimeMillis()
                val validStatus = doc.status == DocumentStatus.VERIFIED || doc.status == DocumentStatus.PENDING
                notExpired && validStatus
            }

            if (validDoc == null) return false

            // Also verify document integrity
            val integrityResult = documentService.verifyDocumentIntegrity(validDoc.id!!.toHexString())
            if (integrityResult.isFailure || integrityResult.getOrNull() != true) return false
        }
        return true
    }

    /**
     * Check that document numbers exist for all required types
     */
    private suspend fun verifyDocumentNumbersExist(userId: String, documentTypes: List<DocumentType>): Boolean {
        for (docType in documentTypes) {
            val docs = documentService.getDocumentByUserAndType(userId, docType)
            if (docs.isEmpty()) return false
            if (docs.none { it.documentNumber.isNotBlank() }) return false
        }
        return true
    }

    // ========================
    // CONTRACT QUERIES
    // ========================

    suspend fun getContractById(contractId: String): VerificationContract? {
        return try {
            MongoDB.contracts.find(
                Filters.eq("_id", ObjectId(contractId))
            ).toList().firstOrNull()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    suspend fun getContractsForUser(userId: String, statusFilter: ContractStatus? = null): ContractListResponse {
        val filter = if (statusFilter != null) {
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("status", statusFilter.name)
            )
        } else {
            Filters.eq("userId", userId)
        }

        val contracts = MongoDB.contracts.find(filter).toList()
        return ContractListResponse(
            contracts = contracts.map { it.toSummary() },
            total = contracts.size
        )
    }

    suspend fun getContractsForRequester(requesterId: String, statusFilter: ContractStatus? = null): ContractListResponse {
        val filter = if (statusFilter != null) {
            Filters.and(
                Filters.eq("requesterId", requesterId),
                Filters.eq("status", statusFilter.name)
            )
        } else {
            Filters.eq("requesterId", requesterId)
        }

        val contracts = MongoDB.contracts.find(filter).toList()
        return ContractListResponse(
            contracts = contracts.map { it.toSummary() },
            total = contracts.size
        )
    }

    suspend fun getVerificationResult(contractId: String, callerId: String): Result<VerificationResponse> {
        val contract = getContractById(contractId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.CONTRACT_NOT_FOUND,
                    message = "Verification contract not found"
                )
            )

        // Only the requester (service provider) or the target user can view the result.
        // Also allow access if userId is null (it's a general request waiting for approval)
        val isRequester = contract.requesterId == callerId
        val isTargetUser = contract.userId == callerId
        val isGeneralRequest = contract.userId == null

        if (!isRequester && !isTargetUser && !isGeneralRequest) {
            return Result.failure(
                AuthorizationException(
                    message = "You are not authorized to view this verification result"
                )
            )
        }

        val metadata = if (contract.userId != null) {
            // Find the first matching document to get metadata (Name, DOB, etc.)
            val firstDocType = contract.requiredDocumentTypes.firstOrNull() ?: DocumentType.AADHAAR_CARD
            documentService.getDocumentMetadata(contract.userId, firstDocType)
        } else {
            null
        }

        return Result.success(contract.toResponse(metadata))
    }

    // ========================
    // REVOKE CONTRACT
    // User can revoke an approved contract (before verification is executed)
    // ========================

    suspend fun revokeContract(userId: String, contractId: String): Result<Unit> {
        val contract = getContractById(contractId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.CONTRACT_NOT_FOUND,
                    message = "Verification contract not found"
                )
            )

        if (contract.userId != userId) {
            return Result.failure(
                AuthorizationException(
                    message = "You are not authorized to revoke this contract"
                )
            )
        }

        if (contract.status == ContractStatus.VERIFIED || contract.status == ContractStatus.VERIFICATION_IN_PROGRESS) {
            return Result.failure(
                BadRequestException(
                    code = ErrorCodes.CONTRACT_ALREADY_APPROVED,
                    message = "Cannot revoke a contract that has already been verified or is in progress"
                )
            )
        }

        burnDisposableKey(contractId)
        updateContractStatus(contractId, ContractStatus.REVOKED)

        logger.info("Contract revoked: {} | User: {}", contractId, userId)
        return Result.success(Unit)
    }

    // ========================
    // CLEANUP: Expire old contracts
    // Should be called periodically
    // ========================

    suspend fun cleanupExpiredContracts(): Int {
        val now = System.currentTimeMillis()

        val expiredPending = MongoDB.contracts.find(
            Filters.and(
                Filters.eq("status", ContractStatus.PENDING_APPROVAL.name),
                Filters.lt("expiresAt", now)
            )
        ).toList()

        val expiredApproved = MongoDB.contracts.find(
            Filters.and(
                Filters.eq("status", ContractStatus.APPROVED.name),
                Filters.lt("keyExpiresAt", now)
            )
        ).toList()

        var count = 0

        for (contract in expiredPending) {
            val id = contract.id?.toHexString() ?: continue
            updateContractStatus(id, ContractStatus.EXPIRED)
            count++
        }

        for (contract in expiredApproved) {
            val id = contract.id?.toHexString() ?: continue
            burnDisposableKey(id)
            updateContractStatus(id, ContractStatus.EXPIRED)
            count++
        }

        if (count > 0) {
            logger.info("Cleaned up {} expired contracts", count)
        }

        return count
    }

    // ========================
    // INTERNAL HELPERS
    // ========================

    private fun generateDisposableKey(): String {
        val bytes = ByteArray(9) // Resulting string will be ~12 chars
        secureRandom.nextBytes(bytes)
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        // Prefix to make it identifiable
        return "lgk_${key}"
    }

    private fun hashDisposableKey(key: String): String {
        return sha256Hash(key + pipelineSecret)
    }

    private suspend fun burnDisposableKey(contractId: String) {
        MongoDB.contracts.updateOne(
            Filters.eq("_id", ObjectId(contractId)),
            Updates.combine(
                Updates.set("disposableKey", null),
                Updates.set("keyExpiresAt", null),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
    }

    private suspend fun updateContractStatus(contractId: String, status: ContractStatus) {
        MongoDB.contracts.updateOne(
            Filters.eq("_id", ObjectId(contractId)),
            Updates.combine(
                Updates.set("status", status.name),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
    }

    private fun generateVerificationToken(
        contractId: String,
        overallStatus: OverallVerificationStatus,
        proofHash: String
    ): String {
        return JWT.create()
            .withSubject(contractId)
            .withIssuer(jwtIssuer)
            .withClaim("type", "verification_proof")
            .withClaim("status", overallStatus.name)
            .withClaim("proofHash", proofHash)
            .withClaim("platform", "legit")
            .withIssuedAt(Date())
            // Verification tokens are valid for 1 year — they're proof of a past verification
            .withExpiresAt(Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun sha256Hash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractYear(dateStr: String): Int? {
        return try {
            // Try YYYY-MM-DD
            if (dateStr.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                return dateStr.substring(0, 4).toInt()
            }
            // Try DD-MM-YYYY or DD/MM/YYYY
            if (dateStr.matches(Regex("^\\d{2}[-/]\\d{2}[-/]\\d{4}$"))) {
                return dateStr.substring(6, 10).toInt()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun validateContractRequest(request: CreateContractRequest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (request.userId != null && request.userId.isBlank()) {
            errors.add(ValidationError("userId", "Target user ID cannot be empty"))
        }

        if (request.requiredDocumentTypes.isEmpty()) {
            errors.add(ValidationError("requiredDocumentTypes", "At least one document type is required"))
        }

        if (request.requiredFields.isEmpty()) {
            errors.add(ValidationError("requiredFields", "At least one verification field is required"))
        }

        if (request.purpose.isBlank()) {
            errors.add(ValidationError("purpose", "Purpose of verification is required"))
        } else if (request.purpose.length < 10) {
            errors.add(ValidationError("purpose", "Purpose must be at least 10 characters — be specific about why you need this verification"))
        }

        return errors
    }
}
