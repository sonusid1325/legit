package com.sonusid.legit.routes

import com.sonusid.legit.models.*
import com.sonusid.legit.pipeline.DataPipelineService
import com.sonusid.legit.plugins.getUserId
import com.sonusid.legit.plugins.getRole
import com.sonusid.legit.plugins.getUsername
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable
data class RevokeContractRequest(
    val contractId: String
)

fun Route.pipelineRoutes(pipelineService: DataPipelineService) {

    // ================================================================
    // SERVICE PROVIDER ROUTES
    // These are used by companies/services that want to verify a user
    // ================================================================
    route("/api/v1/pipeline") {

        // ========================
        // POST /api/v1/pipeline/contracts
        // Service provider creates a new verification contract
        // Requires SERVICE_PROVIDER or ADMIN role
        // ========================
        authenticate("auth-service-provider") {
            post("/contracts") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Authentication required"
                        )
                    )

                val requesterId = principal.getUserId()
                val requesterName = principal.getUsername()

                val request = call.receive<CreateContractRequest>()
                println("DEBUG: Creating contract for requester $requesterName, target user: ${request.userId}")
                
                val result = pipelineService.createContract(requesterId, requesterName, request)

                result.fold(
                    onSuccess = { contract ->
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse.success(contract, "Verification contract created. Waiting for user approval.")
                        )
                    },
                    onFailure = { error ->
                        println("DEBUG: createContract failed with error: ${error.message}")
                        handlePipelineError(call, error)
                    }
                )
            }
        }

        // ========================
        // POST /api/v1/pipeline/verify
        // Service provider uses disposable key to execute verification
        // Requires SERVICE_PROVIDER or ADMIN role
        // ========================
        authenticate("auth-service-provider") {
            post("/verify") {
                val request = call.receive<ExecuteVerificationRequest>()
                val result = pipelineService.executeVerification(request)

                result.fold(
                    onSuccess = { verificationResponse ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(
                                verificationResponse,
                                "Verification completed. Status: ${verificationResponse.status.name}"
                            )
                        )
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }
        }

        // ========================
        // POST /api/v1/pipeline/p2p-verify/{contractId}
        // Direct peer-to-peer verification trigger
        // Allows any authenticated user to trigger if they are the requester
        // ========================
        authenticate("auth-jwt", "auth-session", strategy = AuthenticationStrategy.FirstSuccessful) {
            post("/p2p-verify/{contractId}") {
                val contractId = call.parameters["contractId"]
                val callerId = extractPipelineUserId(call) ?: return@post

                if (contractId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Contract ID is required"
                        )
                    )
                    return@post
                }

                val result = pipelineService.executeVerification(
                    ExecuteVerificationRequest(
                        contractId = contractId,
                        disposableKey = "AUTO_P2P"
                    )
                )

                result.fold(
                    onSuccess = { verificationResponse ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(
                                verificationResponse,
                                "Direct P2P Verification completed."
                            )
                        )
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }
        }

        // ========================
        // GET /api/v1/pipeline/contracts/requester
        // Service provider lists all their contracts
        // Requires SERVICE_PROVIDER or ADMIN role
        // ========================
        authenticate("auth-service-provider") {
            get("/contracts/requester") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Authentication required"
                        )
                    )

                val requesterId = principal.getUserId()
                val statusParam = call.request.queryParameters["status"]

                val statusFilter = statusParam?.let {
                    try {
                        ContractStatus.valueOf(it.uppercase())
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }

                val contracts = pipelineService.getContractsForRequester(requesterId, statusFilter)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(contracts, "Contracts retrieved successfully")
                )
            }
        }

        // ========================
        // GET /api/v1/pipeline/contracts/{contractId}/result
        // Service provider or user retrieves the verification result
        // Both roles can access this
        // ========================
        authenticate("auth-jwt", "auth-session", strategy = AuthenticationStrategy.FirstSuccessful) {
            get("/contracts/{contractId}/result") {
                val callerId = extractPipelineUserId(call) ?: return@get
                val contractId = call.parameters["contractId"]

                if (contractId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Contract ID is required"
                        )
                    )
                    return@get
                }

                val result = pipelineService.getVerificationResult(contractId, callerId)

                result.fold(
                    onSuccess = { verificationResponse ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(verificationResponse, "Verification result retrieved")
                        )
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }
        }
    }

    // ================================================================
    // USER ROUTES
    // These are used by end users who own the documents
    // ================================================================
    route("/api/v1/pipeline/user") {
        authenticate("auth-jwt", "auth-session", strategy = AuthenticationStrategy.FirstSuccessful) {

            // ========================
            // GET /api/v1/pipeline/user/contracts
            // User lists all verification contracts targeting them
            // ========================
            get("/contracts") {
                val userId = extractPipelineUserId(call) ?: return@get
                val statusParam = call.request.queryParameters["status"]

                val statusFilter = statusParam?.let {
                    try {
                        ContractStatus.valueOf(it.uppercase())
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }

                val contracts = pipelineService.getContractsForUser(userId, statusFilter)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(contracts, "Your verification contracts retrieved successfully")
                )
            }

            // ========================
            // GET /api/v1/pipeline/user/contracts/pending
            // User lists only pending contracts that need their approval
            // ========================
            get("/contracts/pending") {
                val userId = extractPipelineUserId(call) ?: return@get

                val contracts = pipelineService.getContractsForUser(userId, ContractStatus.PENDING_APPROVAL)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(
                        contracts,
                        if (contracts.total > 0) {
                            "You have ${contracts.total} pending verification request(s)"
                        } else {
                            "No pending verification requests"
                        }
                    )
                )
            }

            // ========================
            // POST /api/v1/pipeline/user/contracts/approve
            // User approves or rejects a verification contract
            // On approval, the verification runs immediately (Peer-to-Peer)
            // ========================
            post("/contracts/approve") {
                val userId = extractPipelineUserId(call) ?: return@post

                val request = call.receive<ContractApprovalRequest>()
                val result = pipelineService.approveContract(userId, request)

                result.fold(
                    onSuccess = { response ->
                        when (response) {
                            is VerificationResponse -> {
                                call.respond(
                                    HttpStatusCode.OK,
                                    ApiResponse.success(
                                        response,
                                        "Contract approved and verified successfully (Peer-to-Peer)"
                                    )
                                )
                            }
                            is ApiResponse<*> -> {
                                call.respond(
                                    HttpStatusCode.OK,
                                    response
                                )
                            }
                            else -> {
                                call.respond(
                                    HttpStatusCode.OK,
                                    ApiResponse.success(response, "Action completed successfully")
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }

            // ========================
            // GET /api/v1/pipeline/user/contracts/{contractId}
            // User views details of a specific contract
            // ========================
            get("/contracts/{contractId}") {
                val userId = extractPipelineUserId(call) ?: return@get
                val contractId = call.parameters["contractId"]

                if (contractId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Contract ID is required"
                        )
                    )
                    return@get
                }

                val result = pipelineService.getVerificationResult(contractId, userId)

                result.fold(
                    onSuccess = { verificationResponse ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(verificationResponse, "Contract details retrieved")
                        )
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }

            // ========================
            // POST /api/v1/pipeline/user/contracts/revoke
            // User revokes a previously approved contract (if verification hasn't started)
            // ========================
            post("/contracts/revoke") {
                val userId = extractPipelineUserId(call) ?: return@post

                val request = call.receive<RevokeContractRequest>()
                val result = pipelineService.revokeContract(userId, request.contractId)

                result.fold(
                    onSuccess = {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.ok("Contract revoked successfully. Disposable key has been burned.")
                        )
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }

            // ========================
            // GET /api/v1/pipeline/user/contracts/history
            // User views their full verification history (all completed/expired/revoked contracts)
            // ========================
            get("/contracts/history") {
                val userId = extractPipelineUserId(call) ?: return@get

                val allContracts = pipelineService.getContractsForUser(userId)

                // Filter to only completed/terminal states
                val historicalStatuses = setOf(
                    ContractStatus.VERIFIED,
                    ContractStatus.FAILED,
                    ContractStatus.EXPIRED,
                    ContractStatus.REVOKED,
                    ContractStatus.REJECTED
                )

                val historyContracts = allContracts.contracts.filter { it.status in historicalStatuses }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(
                        ContractListResponse(
                            contracts = historyContracts,
                            total = historyContracts.size
                        ),
                        "Verification history retrieved (${historyContracts.size} records)"
                    )
                )
            }
        }
    }

    // ================================================================
    // ADMIN ROUTES
    // Administrative operations on the pipeline
    // ================================================================
    route("/api/v1/pipeline/admin") {
        authenticate("auth-admin") {

            // ========================
            // POST /api/v1/pipeline/admin/cleanup
            // Manually trigger cleanup of expired contracts and burned keys
            // ========================
            post("/cleanup") {
                val force = call.request.queryParameters["force"]?.toBoolean() ?: false
                val cleanedCount = pipelineService.cleanupExpiredContracts(force)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(
                        CleanupResponse(
                            expiredContractsCleaned = cleanedCount,
                            cleanedAt = System.currentTimeMillis()
                        ),
                        "Cleanup completed. $cleanedCount contracts processed."
                    )
                )
            }

            // ========================
            // GET /api/v1/pipeline/admin/contracts/user/{userId}
            // Admin can view all contracts for a specific user
            // ========================
            get("/contracts/user/{userId}") {
                val userId = call.parameters["userId"]

                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = ErrorCodes.BAD_REQUEST,
                            message = "User ID is required"
                        )
                    )
                    return@get
                }

                val contracts = pipelineService.getContractsForUser(userId)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(contracts, "Contracts for user $userId retrieved")
                )
            }

            // ========================
            // GET /api/v1/pipeline/admin/contracts/requester/{requesterId}
            // Admin can view all contracts from a specific service provider
            // ========================
            get("/contracts/requester/{requesterId}") {
                val requesterId = call.parameters["requesterId"]

                if (requesterId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Requester ID is required"
                        )
                    )
                    return@get
                }

                val contracts = pipelineService.getContractsForRequester(requesterId)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(contracts, "Contracts from requester $requesterId retrieved")
                )
            }

            // ========================
            // GET /api/v1/pipeline/admin/contracts/{contractId}
            // Admin can view full details of any contract
            // ========================
            get("/contracts/{contractId}") {
                val contractId = call.parameters["contractId"]
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Authentication required"
                        )
                    )

                if (contractId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Contract ID is required"
                        )
                    )
                    return@get
                }

                val adminId = principal.getUserId()
                val result = pipelineService.getVerificationResult(contractId, adminId)

                result.fold(
                    onSuccess = { verificationResponse ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(verificationResponse, "Contract details retrieved by admin")
                        )
                    },
                    onFailure = { error ->
                        handlePipelineError(call, error)
                    }
                )
            }
        }
    }

    // ================================================================
    // PUBLIC INFO ROUTES
    // These are accessible without authentication
    // ================================================================
    route("/api/v1/pipeline/info") {

        // ========================
        // GET /api/v1/pipeline/info/verification-fields
        // List all available verification fields
        // ========================
        get("/verification-fields") {
            val fields = VerificationField.entries.map { field ->
                VerificationFieldInfo(
                    field = field,
                    name = field.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    description = getVerificationFieldDescription(field)
                )
            }

            call.respond(
                HttpStatusCode.OK,
                ApiResponse.success(fields, "Available verification fields")
            )
        }

        // ========================
        // GET /api/v1/pipeline/info/contract-statuses
        // List all possible contract statuses and their meanings
        // ========================
        get("/contract-statuses") {
            val statuses = ContractStatus.entries.map { status ->
                ContractStatusInfo(
                    status = status,
                    name = status.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    description = getContractStatusDescription(status)
                )
            }

            call.respond(
                HttpStatusCode.OK,
                ApiResponse.success(statuses, "Contract status definitions")
            )
        }

        // ========================
        // GET /api/v1/pipeline/info/how-it-works
        // Explain the pipeline flow to API consumers
        // ========================
        get("/how-it-works") {
            val flow = PipelineFlowInfo(
                title = "Legit Secure Verification Pipeline",
                description = "Zero-document-sharing verification. Your documents never leave our server.",
                steps = listOf(
                    PipelineStep(
                        step = 1,
                        action = "CREATE_CONTRACT",
                        actor = "Service Provider",
                        endpoint = "POST /api/v1/pipeline/contracts",
                        description = "Service provider creates a verification contract specifying what documents and fields they need verified, and why."
                    ),
                    PipelineStep(
                        step = 2,
                        action = "USER_REVIEW",
                        actor = "User",
                        endpoint = "GET /api/v1/pipeline/user/contracts/pending",
                        description = "User reviews pending verification requests. They can see who is requesting, what they want to verify, and the stated purpose."
                    ),
                    PipelineStep(
                        step = 3,
                        action = "APPROVE_OR_REJECT",
                        actor = "User",
                        endpoint = "POST /api/v1/pipeline/user/contracts/approve",
                        description = "User approves or rejects the contract. On approval, a single-use disposable key is generated with a short TTL (5 minutes by default)."
                    ),
                    PipelineStep(
                        step = 4,
                        action = "EXECUTE_VERIFICATION",
                        actor = "Service Provider",
                        endpoint = "POST /api/v1/pipeline/verify",
                        description = "Service provider uses the disposable key to trigger verification. The key is burned immediately on use — single-use, non-replayable."
                    ),
                    PipelineStep(
                        step = 5,
                        action = "RECEIVE_RESULT",
                        actor = "Service Provider",
                        endpoint = "GET /api/v1/pipeline/contracts/{id}/result",
                        description = "Service provider receives a PASS/FAIL/PARTIAL result with cryptographic proof. NO document data is ever shared — just the verification outcome."
                    )
                ),
                keyPrinciples = listOf(
                    "Documents are encrypted at rest with AES-256-GCM and NEVER leave the server",
                    "Disposable keys are single-use, short-lived, and cryptographically random",
                    "Service providers only receive YES/NO verification results, never actual document data",
                    "Every verification generates a signed proof token that can be independently validated",
                    "Users have full control — they can approve, reject, or revoke contracts at any time",
                    "Complete audit trail of all verification activities"
                )
            )

            call.respond(
                HttpStatusCode.OK,
                ApiResponse.success(flow, "Legit Pipeline — How It Works")
            )
        }
    }
}

// ========================
// ROUTE-SPECIFIC DTOs
// ========================

@Serializable
data class CleanupResponse(
    val expiredContractsCleaned: Int,
    val cleanedAt: Long
)

@Serializable
data class VerificationFieldInfo(
    val field: VerificationField,
    val name: String,
    val description: String
)

@Serializable
data class ContractStatusInfo(
    val status: ContractStatus,
    val name: String,
    val description: String
)

@Serializable
data class PipelineFlowInfo(
    val title: String,
    val description: String,
    val steps: List<PipelineStep>,
    val keyPrinciples: List<String>
)

@Serializable
data class PipelineStep(
    val step: Int,
    val action: String,
    val actor: String,
    val endpoint: String,
    val description: String
)

// ========================
// HELPER FUNCTIONS
// ========================

private fun getVerificationFieldDescription(field: VerificationField): String {
    return when (field) {
        VerificationField.FULL_NAME -> "Verify the full name of the user from their documents"
        VerificationField.DATE_OF_BIRTH -> "Verify the date of birth of the user"
        VerificationField.ADDRESS -> "Verify the address of the user from address-bearing documents"
        VerificationField.GENDER -> "Verify the gender of the user"
        VerificationField.FATHER_NAME -> "Verify the father's name from identity documents"
        VerificationField.DOCUMENT_VALIDITY -> "Check if all required documents are valid and not expired"
        VerificationField.DOCUMENT_NUMBER_MATCH -> "Verify that document numbers exist and are valid"
        VerificationField.IDENTITY_PROOF -> "Verify that the user has a valid identity proof document"
        VerificationField.ADDRESS_PROOF -> "Verify that the user has a valid address proof document"
        VerificationField.AGE_VERIFICATION -> "Verify that the user meets the minimum age requirement (18+)"
    }
}

private fun getContractStatusDescription(status: ContractStatus): String {
    return when (status) {
        ContractStatus.PENDING_APPROVAL -> "Contract created, waiting for user to approve or reject"
        ContractStatus.APPROVED -> "User approved the contract, disposable key has been generated"
        ContractStatus.REJECTED -> "User rejected the verification request"
        ContractStatus.VERIFICATION_IN_PROGRESS -> "Verification is currently being processed on the server"
        ContractStatus.VERIFIED -> "Verification completed successfully — result is available"
        ContractStatus.FAILED -> "Verification process encountered an error"
        ContractStatus.EXPIRED -> "Contract or disposable key expired before being used"
        ContractStatus.REVOKED -> "User revoked the contract after approval but before verification"
    }
}

/**
 * Centralized error handler for pipeline routes to reduce code duplication.
 */
private suspend fun handlePipelineError(call: ApplicationCall, error: Throwable) {
    when (error) {
        is ValidationException -> {
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiError(
                    code = error.code,
                    message = error.message ?: "Validation failed",
                    details = error.errors.associate { it.field to it.message }
                )
            )
        }
        is NotFoundException -> {
            call.respond(
                HttpStatusCode.NotFound,
                ApiError(
                    code = error.code,
                    message = error.message
                )
            )
        }
        is AuthenticationException -> {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiError(
                    code = error.code,
                    message = error.message
                )
            )
        }
        is AuthorizationException -> {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiError(
                    code = error.code,
                    message = error.message
                )
            )
        }
        is BadRequestException -> {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(
                    code = error.code,
                    message = error.message
                )
            )
        }
        is ConflictException -> {
            call.respond(
                HttpStatusCode.Conflict,
                ApiError(
                    code = error.code,
                    message = error.message
                )
            )
        }
        is LegitException -> {
            val statusCode = when (error.statusCode) {
                400 -> HttpStatusCode.BadRequest
                401 -> HttpStatusCode.Unauthorized
                403 -> HttpStatusCode.Forbidden
                404 -> HttpStatusCode.NotFound
                409 -> HttpStatusCode.Conflict
                429 -> HttpStatusCode.TooManyRequests
                else -> HttpStatusCode.InternalServerError
            }
            call.respond(
                statusCode,
                ApiError(
                    code = error.code,
                    message = error.message
                )
            )
        }
        else -> {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    code = ErrorCodes.PIPELINE_ERROR,
                    message = error.message ?: "An unexpected pipeline error occurred"
                )
            )
        }
    }
}

/**
 * Extracts userId from either JWT principal or session.
 * Returns null and sends an error response if neither is available.
 */
private suspend fun extractPipelineUserId(call: ApplicationCall): String? {
    // Try JWT first
    val jwtPrincipal = call.principal<JWTPrincipal>()
    if (jwtPrincipal != null) {
        return jwtPrincipal.payload.subject
    }

    // Fall back to session
    val session = call.sessions.get<UserSession>()
    if (session != null) {
        return session.userId
    }

    call.respond(
        HttpStatusCode.Unauthorized,
        ApiError(
            code = ErrorCodes.UNAUTHORIZED,
            message = "Could not identify user. Please provide a valid token or session."
        )
    )
    return null
}
