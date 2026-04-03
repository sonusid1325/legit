package com.sonusid.legit.gateway

import com.sonusid.legit.db.MongoDB
import com.sonusid.legit.models.ApiResponse
import com.sonusid.legit.pipeline.DataPipelineService
import com.sonusid.legit.services.FirebaseService
import com.sonusid.legit.routes.authRoutes
import com.sonusid.legit.routes.documentRoutes
import com.sonusid.legit.routes.pipelineRoutes
import com.sonusid.legit.services.DocumentService
import com.sonusid.legit.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthCheckResponse(
    val status: String,
    val version: String,
    val uptime: Long,
    val services: Map<String, ServiceStatus>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ServiceStatus(
    val name: String,
    val status: String,
    val message: String? = null
)

@Serializable
data class GatewayInfo(
    val name: String,
    val version: String,
    val description: String,
    val endpoints: List<EndpointGroup>,
    val documentation: String
)

@Serializable
data class EndpointGroup(
    val group: String,
    val basePath: String,
    val description: String,
    val endpoints: List<EndpointInfo>
)

@Serializable
data class EndpointInfo(
    val method: String,
    val path: String,
    val description: String,
    val auth: String,
    val roles: List<String> = emptyList()
)

class ApiGateway(
    private val userService: UserService,
    private val documentService: DocumentService,
    private val pipelineService: DataPipelineService
) {
    private val startTime = System.currentTimeMillis()

    companion object {
        const val VERSION = "1.0.0"
        const val API_NAME = "Legit — Secure Data Verification Pipeline"
    }

    /**
     * Registers all routes through the API Gateway.
     * This is the single entry point that aggregates all microservice routes.
     */
    fun Application.configureGateway() {
        routing {
            // ========================
            // GATEWAY ROOT
            // ========================
            get("/") {
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(
                        mapOf(
                            "name" to API_NAME,
                            "version" to VERSION,
                            "message" to "Welcome to Legit. Your documents never leave. Only verification does.",
                            "health" to "/api/v1/gateway/health",
                            "info" to "/api/v1/gateway/info",
                            "docs" to "/api/v1/gateway/endpoints"
                        ),
                        "Legit API Gateway is running"
                    )
                )
            }

            // ========================
            // GATEWAY MANAGEMENT ROUTES
            // ========================
            route("/api/v1/gateway") {

                // Health check endpoint
                get("/health") {
                    val mongoStatus = try {
                        if (MongoDB.isInitialized()) {
                            ServiceStatus(
                                name = "MongoDB",
                                status = "UP",
                                message = "Connected and operational"
                            )
                        } else {
                            ServiceStatus(
                                name = "MongoDB",
                                status = "DOWN",
                                message = "Not initialized"
                            )
                        }
                    } catch (e: Exception) {
                        ServiceStatus(
                            name = "MongoDB",
                            status = "DOWN",
                            message = e.message ?: "Connection failed"
                        )
                    }

                    val userServiceStatus = ServiceStatus(
                        name = "UserService",
                        status = "UP",
                        message = "Authentication & user management operational"
                    )

                    val documentServiceStatus = ServiceStatus(
                        name = "DocumentService",
                        status = "UP",
                        message = "Document vault operational"
                    )

                    val pipelineServiceStatus = ServiceStatus(
                        name = "DataPipelineService",
                        status = "UP",
                        message = "Verification pipeline operational"
                    )

                    val firebaseStatus = ServiceStatus(
                        name = "Firebase",
                        status = if (FirebaseService.isInitialized()) "UP" else "DOWN",
                        message = if (FirebaseService.isInitialized()) "Firebase Admin SDK initialized" else "Firebase not initialized (check service_provider_creds.json)"
                    )

                    val allServicesUp = mongoStatus.status == "UP"

                    val health = HealthCheckResponse(
                        status = if (allServicesUp) "HEALTHY" else "DEGRADED",
                        version = VERSION,
                        uptime = System.currentTimeMillis() - startTime,
                        services = mapOf(
                            "mongodb" to mongoStatus,
                            "user_service" to userServiceStatus,
                            "document_service" to documentServiceStatus,
                            "pipeline_service" to pipelineServiceStatus,
                            "firebase" to firebaseStatus
                        )
                    )

                    val statusCode = if (allServicesUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
                    call.respond(statusCode, ApiResponse.success(health, "Health check complete"))
                }

                // Detailed API info
                get("/info") {
                    val info = GatewayInfo(
                        name = API_NAME,
                        version = VERSION,
                        description = buildString {
                            append("Legit is a secure data verification pipeline. ")
                            append("Unlike DigiLocker which shares actual documents, Legit uses a contractual verification model ")
                            append("where service providers request verification, users approve it, ")
                            append("and a disposable single-use key triggers server-side verification. ")
                            append("Documents NEVER leave the platform — only YES/NO results with cryptographic proof are shared.")
                        },
                        endpoints = buildEndpointGroups(),
                        documentation = "/api/v1/pipeline/info/how-it-works"
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(info, "Legit API Gateway Information")
                    )
                }

                // List all endpoints
                get("/endpoints") {
                    val groups = buildEndpointGroups()

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(groups, "All available API endpoints")
                    )
                }

                // Uptime
                get("/uptime") {
                    val uptimeMs = System.currentTimeMillis() - startTime
                    val uptimeSeconds = uptimeMs / 1000
                    val hours = uptimeSeconds / 3600
                    val minutes = (uptimeSeconds % 3600) / 60
                    val seconds = uptimeSeconds % 60

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(
                            mapOf(
                                "uptimeMs" to uptimeMs.toString(),
                                "uptimeFormatted" to "${hours}h ${minutes}m ${seconds}s",
                                "startedAt" to startTime.toString()
                            ),
                            "Server uptime"
                        )
                    )
                }

                // Ping/pong for liveness probes
                get("/ping") {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.ok("pong")
                    )
                }
            }

            // ========================
            // REGISTER ALL SERVICE ROUTES
            // ========================
            authRoutes(userService)
            documentRoutes(documentService)
            pipelineRoutes(pipelineService)
        }
    }

    /**
     * Builds a complete map of all API endpoints grouped by service.
     * This serves as self-documenting API reference.
     */
    private fun buildEndpointGroups(): List<EndpointGroup> = listOf(

        // Auth endpoints
        EndpointGroup(
            group = "Authentication",
            basePath = "/api/v1/auth",
            description = "User registration, login, token management, and session handling",
            endpoints = listOf(
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/auth/register",
                    description = "Create a new user account",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/auth/login",
                    description = "Authenticate with email and password, receive JWT tokens",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/auth/refresh",
                    description = "Exchange a refresh token for a new access token",
                    auth = "None (requires valid refresh token in body)"
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/auth/logout",
                    description = "Clear the current session",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN", "SERVICE_PROVIDER")
                )
            )
        ),

        // User management endpoints
        EndpointGroup(
            group = "User Management",
            basePath = "/api/v1/user",
            description = "User profile management and session info",
            endpoints = listOf(
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/user/me",
                    description = "Get the current user's profile",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN", "SERVICE_PROVIDER")
                ),
                EndpointInfo(
                    method = "PUT",
                    path = "/api/v1/user/me",
                    description = "Update the current user's profile (name, phone)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN", "SERVICE_PROVIDER")
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/user/change-password",
                    description = "Change the current user's password",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN", "SERVICE_PROVIDER")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/user/session",
                    description = "Get current session information",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN", "SERVICE_PROVIDER")
                )
            )
        ),

        // Document vault endpoints
        EndpointGroup(
            group = "Document Vault",
            basePath = "/api/v1/documents",
            description = "Upload, manage, and verify documents in your encrypted vault. Documents are encrypted with AES-256-GCM and never leave the server.",
            endpoints = listOf(
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/documents",
                    description = "Upload a new document (Aadhaar, PAN, Passport, etc.) to your vault",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/documents",
                    description = "List all documents in your vault (masked numbers, no raw data)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/documents/{id}",
                    description = "Get details of a specific document (masked, no raw data)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/documents/type/{type}",
                    description = "List documents filtered by type (e.g., AADHAAR_CARD, PAN_CARD)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "PATCH",
                    path = "/api/v1/documents/{id}/status",
                    description = "Update a document's status",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "DELETE",
                    path = "/api/v1/documents/{id}",
                    description = "Delete a document from your vault",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/documents/{id}/verify-integrity",
                    description = "Verify that a stored document's data has not been tampered with (hash check)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/documents/types/supported",
                    description = "List all supported document types with descriptions",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN", "SERVICE_PROVIDER")
                )
            )
        ),

        // Pipeline — Service Provider endpoints
        EndpointGroup(
            group = "Verification Pipeline (Service Providers)",
            basePath = "/api/v1/pipeline",
            description = "Service providers use these endpoints to create verification contracts and execute verifications using disposable keys.",
            endpoints = listOf(
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/pipeline/contracts",
                    description = "Create a new verification contract for a user",
                    auth = "JWT",
                    roles = listOf("SERVICE_PROVIDER", "ADMIN")
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/pipeline/verify",
                    description = "Execute verification using a disposable key (single-use, short-lived)",
                    auth = "JWT",
                    roles = listOf("SERVICE_PROVIDER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/contracts/requester",
                    description = "List all contracts created by the current service provider",
                    auth = "JWT",
                    roles = listOf("SERVICE_PROVIDER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/contracts/{contractId}/result",
                    description = "Get the verification result for a contract (YES/NO + proof, no documents)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "SERVICE_PROVIDER", "ADMIN")
                )
            )
        ),

        // Pipeline — User endpoints
        EndpointGroup(
            group = "Verification Pipeline (Users)",
            basePath = "/api/v1/pipeline/user",
            description = "Users review, approve/reject, and manage verification contracts. Full control over who can verify their data.",
            endpoints = listOf(
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/user/contracts",
                    description = "List all verification contracts targeting the current user",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/user/contracts/pending",
                    description = "List only pending contracts that need your approval",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/pipeline/user/contracts/approve",
                    description = "Approve or reject a verification contract. Approval generates a disposable key.",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/user/contracts/{contractId}",
                    description = "View details of a specific contract",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/pipeline/user/contracts/revoke",
                    description = "Revoke a previously approved contract (burns the disposable key)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/user/contracts/history",
                    description = "View full verification history (completed, expired, revoked contracts)",
                    auth = "JWT or Session",
                    roles = listOf("USER", "ADMIN")
                )
            )
        ),

        // Pipeline — Admin endpoints
        EndpointGroup(
            group = "Pipeline Administration",
            basePath = "/api/v1/pipeline/admin",
            description = "Administrative operations for managing the verification pipeline",
            endpoints = listOf(
                EndpointInfo(
                    method = "POST",
                    path = "/api/v1/pipeline/admin/cleanup",
                    description = "Manually trigger cleanup of expired contracts and burned keys",
                    auth = "JWT",
                    roles = listOf("ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/admin/contracts/user/{userId}",
                    description = "View all contracts for a specific user",
                    auth = "JWT",
                    roles = listOf("ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/admin/contracts/requester/{requesterId}",
                    description = "View all contracts from a specific service provider",
                    auth = "JWT",
                    roles = listOf("ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/admin/contracts/{contractId}",
                    description = "View full details of any contract",
                    auth = "JWT",
                    roles = listOf("ADMIN")
                )
            )
        ),

        // Document Admin endpoints
        EndpointGroup(
            group = "Document Administration",
            basePath = "/api/v1/admin/documents",
            description = "Admin operations for document management",
            endpoints = listOf(
                EndpointInfo(
                    method = "PATCH",
                    path = "/api/v1/admin/documents/{id}/status",
                    description = "Update any document's status (e.g., mark as verified after manual review)",
                    auth = "JWT",
                    roles = listOf("ADMIN")
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/admin/documents/user/{userId}",
                    description = "View documents for any user",
                    auth = "JWT",
                    roles = listOf("ADMIN")
                )
            )
        ),

        // Pipeline info (public)
        EndpointGroup(
            group = "Pipeline Information (Public)",
            basePath = "/api/v1/pipeline/info",
            description = "Public informational endpoints about the verification pipeline",
            endpoints = listOf(
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/info/how-it-works",
                    description = "Detailed explanation of the Legit verification pipeline flow",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/info/verification-fields",
                    description = "List all available verification fields and their descriptions",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/pipeline/info/contract-statuses",
                    description = "List all contract statuses and their meanings",
                    auth = "None"
                )
            )
        ),

        // Gateway endpoints
        EndpointGroup(
            group = "API Gateway",
            basePath = "/api/v1/gateway",
            description = "Gateway health, status, and discovery endpoints",
            endpoints = listOf(
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/gateway/health",
                    description = "Health check with service statuses",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/gateway/info",
                    description = "API information and complete endpoint listing",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/gateway/endpoints",
                    description = "List all available API endpoints grouped by service",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/gateway/uptime",
                    description = "Server uptime information",
                    auth = "None"
                ),
                EndpointInfo(
                    method = "GET",
                    path = "/api/v1/gateway/ping",
                    description = "Liveness probe — returns pong",
                    auth = "None"
                )
            )
        )
    )
}
