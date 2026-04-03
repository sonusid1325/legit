package com.sonusid.legit.services

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.sonusid.legit.db.MongoDB
import com.sonusid.legit.models.*
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DocumentService(
    private val encryptionSecret: String
) {

    private val logger = LoggerFactory.getLogger(DocumentService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val PBKDF2_ITERATIONS = 65536
        private const val SALT_LENGTH = 16
    }

    // ========================
    // UPLOAD DOCUMENT
    // ========================

    suspend fun uploadDocument(userId: String, request: DocumentUploadRequest): Result<DocumentResponse> {
        // Validate the upload request
        val validationErrors = validateDocumentUpload(request)
        if (validationErrors.isNotEmpty()) {
            return Result.failure(ValidationException(validationErrors))
        }

        // Check if this exact document already exists for this user
        val existing = MongoDB.documents.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("documentType", request.documentType.name),
                Filters.eq("documentNumber", request.documentNumber.trim())
            )
        ).toList()

        if (existing.isNotEmpty()) {
            return Result.failure(
                ConflictException(
                    code = ErrorCodes.DOCUMENT_ALREADY_EXISTS,
                    message = "A ${request.documentType.name} with this number is already stored in your vault"
                )
            )
        }

        // Hash the raw data for integrity verification
        val dataHash = sha256Hash(request.rawData)

        // Encrypt the raw document data — this never leaves our server
        val encryptedData = encrypt(request.rawData)

        val document = Document(
            userId = userId,
            documentType = request.documentType,
            documentNumber = request.documentNumber.trim(),
            documentName = request.documentName.trim(),
            metadata = request.metadata,
            dataHash = dataHash,
            encryptedData = encryptedData,
            status = DocumentStatus.PENDING,
            issuedBy = request.issuedBy?.trim(),
            issuedAt = request.issuedAt,
            expiresAt = request.expiresAt
        )

        val insertResult = MongoDB.documents.insertOne(document)
        val documentId = insertResult.insertedId?.asObjectId()?.value?.toHexString()
            ?: return Result.failure(
                LegitException(
                    code = ErrorCodes.DOCUMENT_UPLOAD_FAILED,
                    message = "Failed to store document"
                )
            )

        // Anchor document hash on Polygon blockchain
        try {
            BlockchainService.anchorDocument(dataHash)
        } catch (e: Exception) {
            // Non-fatal — document saved regardless
            logger.warn("Document anchoring failed (non-fatal): ${e.message}")
        }

        val savedDocument = getDocumentById(documentId)
            ?: return Result.failure(
                LegitException(
                    code = ErrorCodes.INTERNAL_ERROR,
                    message = "Document saved but could not be retrieved"
                )
            )

        return Result.success(savedDocument.toResponse())
    }

    // ========================
    // GET DOCUMENTS
    // ========================

    suspend fun getDocumentById(documentId: String): Document? {
        return try {
            MongoDB.documents.find(
                Filters.eq("_id", ObjectId(documentId))
            ).toList().firstOrNull()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    suspend fun getDocumentsByUserId(userId: String): DocumentListResponse {
        val documents = MongoDB.documents.find(
            Filters.eq("userId", userId)
        ).toList()

        return DocumentListResponse(
            documents = documents.map { it.toSummary() },
            total = documents.size
        )
    }

    suspend fun getDocumentByUserAndType(userId: String, documentType: DocumentType): List<Document> {
        return MongoDB.documents.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("documentType", documentType.name)
            )
        ).toList()
    }

    suspend fun getDocumentResponse(userId: String, documentId: String): Result<DocumentResponse> {
        val document = getDocumentById(documentId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.DOCUMENT_NOT_FOUND,
                    message = "Document not found"
                )
            )

        // Ensure the requesting user owns this document
        if (document.userId != userId) {
            return Result.failure(
                AuthorizationException(
                    message = "You do not have access to this document"
                )
            )
        }

        return Result.success(document.toResponse())
    }

    // ========================
    // UPDATE DOCUMENT
    // ========================

    suspend fun updateDocumentStatus(
        documentId: String,
        status: DocumentStatus,
        userId: String? = null
    ): Result<DocumentResponse> {
        val document = getDocumentById(documentId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.DOCUMENT_NOT_FOUND,
                    message = "Document not found"
                )
            )

        // If userId is provided, make sure it matches (ownership check)
        if (userId != null && document.userId != userId) {
            return Result.failure(
                AuthorizationException(
                    message = "You do not have access to this document"
                )
            )
        }

        MongoDB.documents.updateOne(
            Filters.eq("_id", ObjectId(documentId)),
            Updates.combine(
                Updates.set("status", status.name),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )

        val updated = getDocumentById(documentId)!!
        return Result.success(updated.toResponse())
    }

    // ========================
    // DELETE DOCUMENT
    // ========================

    suspend fun deleteDocument(userId: String, documentId: String): Result<Unit> {
        val document = getDocumentById(documentId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.DOCUMENT_NOT_FOUND,
                    message = "Document not found"
                )
            )

        if (document.userId != userId) {
            return Result.failure(
                AuthorizationException(
                    message = "You do not have access to this document"
                )
            )
        }

        MongoDB.documents.deleteOne(
            Filters.eq("_id", ObjectId(documentId))
        )

        return Result.success(Unit)
    }

    // ========================
    // VERIFICATION SUPPORT
    // These methods are used by the DataPipeline
    // to verify documents WITHOUT exposing them
    // ========================

    /**
     * Decrypts and returns the raw data for internal verification only.
     * This should NEVER be exposed through any API endpoint.
     * Only the DataPipeline service uses this during contract execution.
     */
    suspend fun getDecryptedDataForVerification(documentId: String): Result<String> {
        val document = getDocumentById(documentId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.DOCUMENT_NOT_FOUND,
                    message = "Document not found"
                )
            )

        return try {
            val decrypted = decrypt(document.encryptedData)
            Result.success(decrypted)
        } catch (e: Exception) {
            Result.failure(
                LegitException(
                    code = ErrorCodes.INTERNAL_ERROR,
                    message = "Failed to decrypt document data for verification"
                )
            )
        }
    }

    /**
     * Verifies document integrity by comparing the stored hash with a fresh hash of the decrypted data.
     */
    suspend fun verifyDocumentIntegrity(documentId: String): Result<Boolean> {
        val document = getDocumentById(documentId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.DOCUMENT_NOT_FOUND,
                    message = "Document not found"
                )
            )

        return try {
            val decrypted = decrypt(document.encryptedData)
            val currentHash = sha256Hash(decrypted)
            Result.success(currentHash == document.dataHash)
        } catch (e: Exception) {
            Result.failure(
                LegitException(
                    code = ErrorCodes.INTERNAL_ERROR,
                    message = "Failed to verify document integrity"
                )
            )
        }
    }

    /**
     * Check if a user has all the required document types uploaded and in valid status.
     */
    suspend fun hasRequiredDocuments(userId: String, requiredTypes: List<DocumentType>): Pair<Boolean, List<DocumentType>> {
        val userDocuments = MongoDB.documents.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.`in`("status", listOf(DocumentStatus.VERIFIED.name, DocumentStatus.PENDING.name))
            )
        ).toList()

        val availableTypes = userDocuments.map { it.documentType }.toSet()
        val missingTypes = requiredTypes.filter { it !in availableTypes }

        return Pair(missingTypes.isEmpty(), missingTypes)
    }

    /**
     * Get document metadata for a specific user and document type.
     * Used during pipeline verification — returns metadata only, never raw data.
     */
    suspend fun getDocumentMetadata(userId: String, documentType: DocumentType): DocumentMetadata? {
        val document = MongoDB.documents.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("documentType", documentType.name)
            )
        ).toList().firstOrNull()

        return document?.metadata
    }

    // ========================
    // ENCRYPTION / DECRYPTION
    // AES-256-GCM with PBKDF2 key derivation
    // ========================

    private fun encrypt(plainText: String): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val key = deriveKey(encryptionSecret, salt)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Format: base64(salt + iv + ciphertext)
        val combined = salt + iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encryptedText: String): String {
        val combined = Base64.getDecoder().decode(encryptedText)

        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, combined.size)

        val key = deriveKey(encryptionSecret, salt)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        return factory.generateSecret(spec).encoded
    }

    // ========================
    // HASHING
    // ========================

    private fun sha256Hash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // ========================
    // VALIDATION
    // ========================

    private fun validateDocumentUpload(request: DocumentUploadRequest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (request.documentNumber.isBlank()) {
            errors.add(ValidationError("documentNumber", "Document number is required"))
        }

        if (request.documentName.isBlank()) {
            errors.add(ValidationError("documentName", "Document name is required"))
        }

        if (request.rawData.isBlank()) {
            errors.add(ValidationError("rawData", "Document data is required"))
        }

        // Type-specific validation
        when (request.documentType) {
            DocumentType.AADHAAR_CARD -> {
                val cleaned = request.documentNumber.replace(Regex("[\\s-]"), "")
                if (cleaned.length != 12 || !cleaned.all { it.isDigit() }) {
                    errors.add(ValidationError("documentNumber", "Aadhaar number must be 12 digits"))
                }
            }
            DocumentType.PAN_CARD -> {
                val cleaned = request.documentNumber.trim().uppercase()
                if (!cleaned.matches(Regex("^[A-Z]{5}[0-9]{4}[A-Z]$"))) {
                    errors.add(ValidationError("documentNumber", "PAN must be in format ABCDE1234F"))
                }
            }
            DocumentType.PASSPORT -> {
                val cleaned = request.documentNumber.trim().uppercase()
                if (!cleaned.matches(Regex("^[A-Z][0-9]{7}$"))) {
                    errors.add(ValidationError("documentNumber", "Invalid passport number format"))
                }
            }
            DocumentType.DRIVING_LICENSE -> {
                if (request.documentNumber.trim().length < 5) {
                    errors.add(ValidationError("documentNumber", "Driving license number seems too short"))
                }
            }
            else -> {
                // Generic validation — just ensure it's not unreasonably short
                if (request.documentNumber.trim().length < 3) {
                    errors.add(ValidationError("documentNumber", "Document number is too short"))
                }
            }
        }

        // Metadata validation
        if (request.metadata.fullName.isNullOrBlank()) {
            errors.add(ValidationError("metadata.fullName", "Full name in metadata is required"))
        }

        // Expiry check
        if (request.expiresAt != null && request.expiresAt < System.currentTimeMillis()) {
            errors.add(ValidationError("expiresAt", "Document has already expired"))
        }

        return errors
    }
}
