package com.sonusid.legit.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sonusid.legit.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*

fun Application.configureSecurity() {
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString() ?: "legit-super-secret-change-in-production"
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "legit-platform"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "legit-users"
    val jwtRealm = environment.config.propertyOrNull("jwt.realm")?.getString() ?: "legit"

    install(Sessions) {
        cookie<UserSession>("LEGIT_SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400 // 24 hours
            cookie.httpOnly = true
            cookie.secure = false // Set to true in production with HTTPS
            cookie.extensions["SameSite"] = "lax"
        }
    }

    authentication {
        // Primary JWT auth for regular users
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.subject
                val username = credential.payload.getClaim("username")?.asString()
                val role = credential.payload.getClaim("role")?.asString()
                val type = credential.payload.getClaim("type")?.asString()

                if (userId != null && username != null && role != null && type == "access") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse.error<Unit>("Authentication required. Please provide a valid JWT token.")
                )
            }
        }

        // Service provider JWT auth — same mechanism, but we check the role
        jwt("auth-service-provider") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val role = credential.payload.getClaim("role")?.asString()
                val type = credential.payload.getClaim("type")?.asString()

                if (type == "access" && (role == UserRole.SERVICE_PROVIDER.name || role == UserRole.ADMIN.name)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse.error<Unit>("Access denied. Service provider or admin role required.")
                )
            }
        }

        // Admin-only JWT auth
        jwt("auth-admin") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val role = credential.payload.getClaim("role")?.asString()
                val type = credential.payload.getClaim("type")?.asString()

                if (type == "access" && role == UserRole.ADMIN.name) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse.error<Unit>("Access denied. Admin role required.")
                )
            }
        }

        // Session-based auth as a fallback / secondary mechanism
        session<UserSession>("auth-session") {
            validate { session ->
                val sessionMaxAge = 24 * 60 * 60 * 1000L
                val age = System.currentTimeMillis() - session.createdAt
                if (age < sessionMaxAge) {
                    session
                } else {
                    null
                }
            }
            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse.error<Unit>("Session expired or invalid. Please log in again.")
                )
            }
        }
    }
}

// ========================
// Extension helpers to extract user info from JWT principal
// ========================

fun JWTPrincipal.getUserId(): String = payload.subject
fun JWTPrincipal.getUsername(): String = payload.getClaim("username").asString()
fun JWTPrincipal.getRole(): UserRole = UserRole.valueOf(payload.getClaim("role").asString())
