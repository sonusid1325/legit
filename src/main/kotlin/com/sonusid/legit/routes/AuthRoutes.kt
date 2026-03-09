package com.sonusid.legit.routes

import com.sonusid.legit.models.*
import com.sonusid.legit.plugins.getUserId
import com.sonusid.legit.plugins.getRole
import com.sonusid.legit.plugins.getUsername
import com.sonusid.legit.services.UserService
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
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class UpdateProfileRequest(
    val fullName: String? = null,
    val phoneNumber: String? = null
)

fun Route.authRoutes(userService: UserService) {

    route("/api/v1/auth") {

        // ========================
        // POST /api/v1/auth/register
        // Public — create a new user account
        // ========================
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val result = userService.register(request)

            result.fold(
                onSuccess = { authResponse ->
                    // Also create a session cookie
                    val session = userService.createSession(
                        userId = authResponse.userId,
                        username = authResponse.username,
                        role = authResponse.role
                    )
                    call.sessions.set(session)

                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(authResponse, "Account created successfully")
                    )
                },
                onFailure = { error ->
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
                        is ConflictException -> {
                            call.respond(
                                HttpStatusCode.Conflict,
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
                                    code = ErrorCodes.INTERNAL_ERROR,
                                    message = error.message ?: "Registration failed"
                                )
                            )
                        }
                    }
                }
            )
        }

        // ========================
        // POST /api/v1/auth/login
        // Public — authenticate and get tokens
        // ========================
        post("/login") {
            val request = call.receive<LoginRequest>()
            val result = userService.login(request)

            result.fold(
                onSuccess = { authResponse ->
                    // Create session
                    val session = userService.createSession(
                        userId = authResponse.userId,
                        username = authResponse.username,
                        role = authResponse.role
                    )
                    call.sessions.set(session)

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(authResponse, "Login successful")
                    )
                },
                onFailure = { error ->
                    when (error) {
                        is AuthenticationException -> {
                            call.respond(
                                HttpStatusCode.Unauthorized,
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
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiError(
                                    code = ErrorCodes.INTERNAL_ERROR,
                                    message = error.message ?: "Login failed"
                                )
                            )
                        }
                    }
                }
            )
        }

        // ========================
        // POST /api/v1/auth/refresh
        // Public — exchange refresh token for new access token
        // ========================
        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            val result = userService.refreshToken(request.refreshToken)

            result.fold(
                onSuccess = { authResponse ->
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(authResponse, "Token refreshed successfully")
                    )
                },
                onFailure = { error ->
                    when (error) {
                        is AuthenticationException -> {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ApiError(
                                    code = error.code,
                                    message = error.message
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
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiError(
                                    code = ErrorCodes.INTERNAL_ERROR,
                                    message = error.message ?: "Token refresh failed"
                                )
                            )
                        }
                    }
                }
            )
        }

        // ========================
        // POST /api/v1/auth/logout
        // Authenticated — clear session
        // ========================
        authenticate("auth-jwt", "auth-session", strategy = AuthenticationStrategy.FirstSuccessful) {
            post("/logout") {
                call.sessions.clear<UserSession>()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.ok("Logged out successfully")
                )
            }
        }
    }

    // ========================
    // AUTHENTICATED USER ROUTES
    // ========================
    route("/api/v1/user") {
        authenticate("auth-jwt", "auth-session", strategy = AuthenticationStrategy.FirstSuccessful) {

            // ========================
            // GET /api/v1/user/me
            // Get current user profile
            // ========================
            get("/me") {
                val userId = extractUserId(call) ?: return@get

                val result = userService.getUserProfile(userId)

                result.fold(
                    onSuccess = { profile ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(profile, "Profile retrieved successfully")
                        )
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiError(
                                code = ErrorCodes.USER_NOT_FOUND,
                                message = error.message ?: "User not found"
                            )
                        )
                    }
                )
            }

            // ========================
            // PUT /api/v1/user/me
            // Update current user profile
            // ========================
            put("/me") {
                val userId = extractUserId(call) ?: return@put

                val request = call.receive<UpdateProfileRequest>()
                val result = userService.updateProfile(
                    userId = userId,
                    fullName = request.fullName,
                    phoneNumber = request.phoneNumber
                )

                result.fold(
                    onSuccess = { profile ->
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(profile, "Profile updated successfully")
                        )
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError(
                                code = ErrorCodes.INVALID_USER_DATA,
                                message = error.message ?: "Update failed"
                            )
                        )
                    }
                )
            }

            // ========================
            // POST /api/v1/user/change-password
            // Change current user's password
            // ========================
            post("/change-password") {
                val userId = extractUserId(call) ?: return@post

                val request = call.receive<ChangePasswordRequest>()
                val result = userService.changePassword(
                    userId = userId,
                    currentPassword = request.currentPassword,
                    newPassword = request.newPassword
                )

                result.fold(
                    onSuccess = {
                        // Clear session so user needs to re-login with new password
                        call.sessions.clear<UserSession>()
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.ok("Password changed successfully. Please log in again.")
                        )
                    },
                    onFailure = { error ->
                        when (error) {
                            is AuthenticationException -> {
                                call.respond(
                                    HttpStatusCode.Unauthorized,
                                    ApiError(
                                        code = error.code,
                                        message = error.message
                                    )
                                )
                            }
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
                            else -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ApiError(
                                        code = ErrorCodes.INTERNAL_ERROR,
                                        message = error.message ?: "Password change failed"
                                    )
                                )
                            }
                        }
                    }
                )
            }

            // ========================
            // GET /api/v1/user/session
            // Get current session info
            // ========================
            get("/session") {
                val session = call.sessions.get<UserSession>()
                if (session != null && userService.validateSession(session)) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(session, "Session active")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            code = ErrorCodes.SESSION_EXPIRED,
                            message = "No active session found. Please log in."
                        )
                    )
                }
            }
        }
    }
}

/**
 * Extracts userId from either JWT principal or session.
 * Returns null and sends an error response if neither is available.
 */
private suspend fun extractUserId(call: ApplicationCall): String? {
    // Try JWT first
    val jwtPrincipal = call.principal<JWTPrincipal>()
    if (jwtPrincipal != null) {
        return jwtPrincipal.getUserId()
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
