package com.sonusid.legit

import com.sonusid.legit.db.MongoDB
import com.sonusid.legit.gateway.ApiGateway
import com.sonusid.legit.pipeline.DataPipelineService
import com.sonusid.legit.plugins.configureMonitoring
import com.sonusid.legit.plugins.configureSecurity
import com.sonusid.legit.plugins.configureSerialization
import com.sonusid.legit.plugins.configureStatusPages
import com.sonusid.legit.services.BlockchainService
import com.sonusid.legit.services.DocumentService
import com.sonusid.legit.services.FirebaseService
import com.sonusid.legit.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.coroutines.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // ========================
    // 0. FIREBASE INITIALIZATION
    // Initialize Admin SDK for push notifications
    // ========================
    FirebaseService.init()

    // ========================
    // 0.1. BLOCKCHAIN INITIALIZATION
    // Initialize Polygon Amoy testnet for audit logging
    // ========================
    val blockchainRpc = environment.config.propertyOrNull("blockchain.rpcUrl")?.getString() ?: "https://rpc-amoy.polygon.technology"
    val blockchainKey = environment.config.propertyOrNull("blockchain.privateKey")?.getString() ?: ""
    val auditContract = environment.config.propertyOrNull("blockchain.auditLogContract")?.getString() ?: ""
    val reputationContract = environment.config.propertyOrNull("blockchain.reputationContract")?.getString() ?: ""
    BlockchainService.init(blockchainRpc, blockchainKey, auditContract, reputationContract)

    // ========================
    // 1. CORE PLUGINS
    // Order matters — serialization and status pages first,
    // then security, then monitoring
    // ========================
    configureSerialization()
    configureStatusPages()
    configureSecurity()
    configureMonitoring()
    configureCORS()

    // ========================
    // 2. DATABASE
    // Initialize MongoDB connection and create indexes
    // ========================
    MongoDB.init(this)

    // ========================
    // 3. READ CONFIGURATION
    // Pull secrets and config from application.yaml
    // ========================
    val jwtSecret = environment.config
        .propertyOrNull("jwt.secret")?.getString()
        ?: "legit-super-secret-change-in-production"

    val jwtIssuer = environment.config
        .propertyOrNull("jwt.issuer")?.getString()
        ?: "legit-platform"

    val jwtAudience = environment.config
        .propertyOrNull("jwt.audience")?.getString()
        ?: "legit-users"

    val jwtExpirationMs = environment.config
        .propertyOrNull("jwt.expirationMs")?.getString()?.toLongOrNull()
        ?: 3600000L // 1 hour

    val refreshExpirationMs = environment.config
        .propertyOrNull("jwt.refreshExpirationMs")?.getString()?.toLongOrNull()
        ?: 604800000L // 7 days

    val encryptionSecret = environment.config
        .propertyOrNull("legit.encryptionSecret")?.getString()
        ?: "legit-encryption-key-change-in-production-must-be-long"

    val pipelineSecret = environment.config
        .propertyOrNull("legit.pipelineSecret")?.getString()
        ?: "legit-pipeline-secret-change-in-production"

    val disposableKeyTtlMs = environment.config
        .propertyOrNull("legit.disposableKeyTtlMs")?.getString()?.toLongOrNull()
        ?: (5 * 60 * 1000L) // 5 minutes

    // ========================
    // 4. INITIALIZE SERVICES
    // Wire up all service instances with their dependencies
    // ========================
    val userService = UserService(
        jwtSecret = jwtSecret,
        jwtIssuer = jwtIssuer,
        jwtAudience = jwtAudience,
        jwtExpirationMs = jwtExpirationMs,
        refreshExpirationMs = refreshExpirationMs
    )

    val documentService = DocumentService(
        encryptionSecret = encryptionSecret
    )

    val pipelineService = DataPipelineService(
        documentService = documentService,
        userService = userService,
        pipelineSecret = pipelineSecret,
        jwtSecret = jwtSecret,
        jwtIssuer = jwtIssuer,
        disposableKeyTtlMs = disposableKeyTtlMs
    )

    // ========================
    // 5. API GATEWAY
    // Single entry point that aggregates all service routes
    // ========================
    val gateway = ApiGateway(
        userService = userService,
        documentService = documentService,
        pipelineService = pipelineService
    )

    with(gateway) {
        configureGateway()
    }

    // ========================
    // 6. BACKGROUND TASKS
    // Periodic cleanup of expired contracts and burned keys
    // ========================
    val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    cleanupScope.launch {
        while (isActive) {
            try {
                delay(15 * 60 * 1000L) // Every 15 minutes
                val cleaned = pipelineService.cleanupExpiredContracts()
                if (cleaned > 0) {
                    log.info("Scheduled cleanup: $cleaned expired contracts processed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Scheduled cleanup failed", e)
            }
        }
    }

    // Cancel cleanup job when application stops
    monitor.subscribe(ApplicationStopped) {
        cleanupScope.cancel()
    }

    // ========================
    // STARTUP LOG
    // ========================
    log.info("=".repeat(60))
    log.info("  LEGIT — Secure Data Verification Pipeline")
    log.info("  Version: ${ApiGateway.VERSION}")
    log.info("  Port: ${environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"}")
    log.info("  ")
    log.info("  Services:")
    log.info("    ✓ UserService        — JWT Auth, Sessions, User Management")
    log.info("    ✓ DocumentService    — Encrypted Document Vault (AES-256-GCM)")
    log.info("    ✓ DataPipeline       — Contractual Verification with Disposable Keys")
    log.info("    ✓ API Gateway        — Route Aggregation & Health Monitoring")
    log.info("    ${if (FirebaseService.isInitialized()) "✓" else "○"} Firebase          — Push Notifications ${if (FirebaseService.isInitialized()) "(LIVE)" else "(OFFLINE)"}")
    log.info("    ${if (BlockchainService.isInitialized()) "✓" else "○"} BlockchainService   — Blockchain Audit Trail ${if (BlockchainService.isInitialized()) "(LIVE)" else "(SIMULATION)"}")
    log.info("  ")
    log.info("  Your documents never leave. Only verification does.")
    log.info("=".repeat(60))
}

/**
 * Configure CORS for cross-origin requests.
 * In production, restrict this to your actual frontend domains.
 */
fun Application.configureCORS() {
    install(CORS) {
        // HTTP Methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // Headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.AccessControlRequestMethod)
        allowHeader(HttpHeaders.AccessControlRequestHeaders)
        allowHeader("X-Requested-With")

        // Allow credentials (cookies/sessions)
        allowCredentials = true
        allowNonSimpleContentTypes = true

        // Development policy: reflect common local and tunnel origins.
        allowOrigins { origin ->
            origin.startsWith("http://localhost:") ||
                origin.startsWith("https://localhost:") ||
                origin.startsWith("http://127.0.0.1:") ||
                origin.startsWith("https://127.0.0.1:") ||
                origin.endsWith(".ngrok-free.app") ||
                origin.endsWith(".ngrok.app")
        }

        // Max age for preflight cache
        maxAgeInSeconds = 3600
    }
}
