package com.sonusid.legit.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.sonusid.legit.db.MongoDB
import com.sonusid.legit.models.*
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*

class UserService(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val jwtExpirationMs: Long = 3600000, // 1 hour
    private val refreshExpirationMs: Long = 604800000 // 7 days
) {

    private val logger = LoggerFactory.getLogger(UserService::class.java)
    private val secureRandom = SecureRandom()

    // ========================
    // REGISTRATION
    // ========================

    suspend fun register(request: RegisterRequest): Result<AuthResponse> {
        val validationErrors = validateRegistration(request)
        if (validationErrors.isNotEmpty()) {
            return Result.failure(ValidationException(validationErrors))
        }

        val normalizedEmail = request.email.lowercase().trim()
        val normalizedUsername = request.username.lowercase().trim()

        if (getUserByEmail(normalizedEmail) != null) {
            return Result.failure(
                ConflictException(
                    code = ErrorCodes.EMAIL_ALREADY_TAKEN,
                    message = "Email is already registered"
                )
            )
        }

        if (getUserByUsername(normalizedUsername) != null) {
            return Result.failure(
                ConflictException(
                    code = ErrorCodes.USER_ALREADY_EXISTS,
                    message = "Username is already taken"
                )
            )
        }

        // Hash password
        val passwordHash = hashPassword(request.password)

        // Create user
        val username = normalizedUsername
        val suffix = generateRandomSuffix(4)
        val user = User(
            username = username,
            legitId = "$username.$suffix@legit",
            email = normalizedEmail,
            passwordHash = passwordHash,
            fullName = request.fullName.trim(),
            phoneNumber = request.phoneNumber?.trim(),
            isVerified = false,
            role = UserRole.USER
        )

        val insertResult = MongoDB.users.insertOne(user)
        val userId = insertResult.insertedId?.asObjectId()?.value?.toHexString()
            ?: return Result.failure(
                LegitException(
                    code = ErrorCodes.INTERNAL_ERROR,
                    message = "Failed to create user account"
                )
            )

        // Auto-repair legacy accounts: Persist legitId if missing
        val finalLegitId = if (user.legitId.isBlank()) {
            val generated = "${user.username}.legacy@legit"
            MongoDB.users.updateOne(
                Filters.eq("_id", user.id),
                Updates.set("legitId", generated)
            )
            generated
        } else {
            user.legitId
        }

        // Generate tokens
        val token = generateAccessToken(userId, user.username, finalLegitId, user.role)
        val refreshToken = generateRefreshToken(userId)

        return Result.success(
            AuthResponse(
                token = token,
                refreshToken = refreshToken,
                userId = userId,
                username = user.username,
                legitId = finalLegitId,
                role = user.role,
                expiresIn = jwtExpirationMs / 1000
            )
        )
    }

    private fun generateRandomSuffix(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    // ========================
    // LOGIN
    // ========================

    suspend fun login(request: LoginRequest): Result<AuthResponse> {
        if (request.email.isBlank() || request.password.isBlank()) {
            return Result.failure(
                BadRequestException(
                    code = ErrorCodes.INVALID_CREDENTIALS,
                    message = "Email and password are required"
                )
            )
        }

        // Find user by email
        val user = MongoDB.users.find(
            Filters.eq("email", request.email.lowercase().trim())
        ).firstOrNull()
            ?: return Result.failure(
                AuthenticationException(
                    code = ErrorCodes.INVALID_CREDENTIALS,
                    message = "Invalid email or password"
                )
            )

        // Verify password
        if (!verifyPassword(request.password, user.passwordHash)) {
            return Result.failure(
                AuthenticationException(
                    code = ErrorCodes.INVALID_CREDENTIALS,
                    message = "Invalid email or password"
                )
            )
        }

        val userId = user.id?.toHexString()
            ?: return Result.failure(
                LegitException(
                    code = ErrorCodes.INTERNAL_ERROR,
                    message = "User data corrupted"
                )
            )

        // Auto-repair legacy accounts: Persist legitId if missing
        val finalLegitId = if (user.legitId.isBlank()) {
            val generated = "${user.username}.legacy@legit"
            MongoDB.users.updateOne(
                Filters.eq("_id", user.id),
                Updates.set("legitId", generated)
            )
            generated
        } else {
            user.legitId
        }

        // Generate tokens
        val token = generateAccessToken(userId, user.username, finalLegitId, user.role)
        val refreshToken = generateRefreshToken(userId)

        return Result.success(
            AuthResponse(
                token = token,
                refreshToken = refreshToken,
                userId = userId,
                username = user.username,
                legitId = finalLegitId,
                role = user.role,
                expiresIn = jwtExpirationMs / 1000
            )
        )
    }

    // ========================
    // TOKEN REFRESH
    // ========================

    suspend fun refreshToken(refreshToken: String): Result<AuthResponse> {
        val decoded = try {
            JWT.require(Algorithm.HMAC256(jwtSecret))
                .withIssuer(jwtIssuer)
                .withClaim("type", "refresh")
                .build()
                .verify(refreshToken)
        } catch (e: Exception) {
            return Result.failure(
                AuthenticationException(
                    code = ErrorCodes.TOKEN_EXPIRED,
                    message = "Invalid or expired refresh token"
                )
            )
        }

        val userId = decoded.subject
            ?: return Result.failure(
                AuthenticationException(
                    code = ErrorCodes.TOKEN_EXPIRED,
                    message = "Invalid refresh token"
                )
            )

        val user = getUserById(userId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.USER_NOT_FOUND,
                    message = "User not found"
                )
            )

        val userIdStr = user.id?.toHexString() ?: userId

        val newAccessToken = generateAccessToken(userIdStr, user.username, user.legitId, user.role)
        val newRefreshToken = generateRefreshToken(userIdStr)

        return Result.success(
            AuthResponse(
                token = newAccessToken,
                refreshToken = newRefreshToken,
                userId = userIdStr,
                username = user.username,
                legitId = if (user.legitId.isBlank()) "${user.username}.legacy@legit" else user.legitId,
                role = user.role,
                expiresIn = jwtExpirationMs / 1000
            )
        )
    }

    // ========================
    // USER MANAGEMENT
    // ========================

    suspend fun getUserById(userId: String): User? {
        return try {
            MongoDB.users.find(
                Filters.eq("_id", ObjectId(userId))
            ).firstOrNull()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    suspend fun getUserByEmail(email: String): User? {
        return MongoDB.users.find(
            Filters.eq("email", email.lowercase().trim())
        ).firstOrNull()
    }

    suspend fun getUserByUsername(username: String): User? {
        return MongoDB.users.find(
            Filters.eq("username", username.lowercase().trim())
        ).firstOrNull()
    }

    suspend fun getUserByLegitId(legitId: String): User? {
        val identifier = legitId.lowercase().trim()
        logger.info("getUserByLegitId: Searching for identifier: {}", identifier)
        
        // 1. Try finding by the actual legitId field first (exact match)
        val user = MongoDB.users.find(
            Filters.eq("legitId", identifier)
        ).firstOrNull()
        
        if (user != null) {
            logger.info("getUserByLegitId: Found exact match for {}", identifier)
            return user
        }
        
        // 2. If not found and it ends with @legit, try extracting username
        if (identifier.endsWith("@legit")) {
            // Remove @legit
            val prefix = identifier.removeSuffix("@legit")
            logger.info("getUserByLegitId: Prefix extracted: {}", prefix)
            
            // Try 1: The whole prefix is the username (e.g. "john.doe@legit")
            val user1 = getUserByUsername(prefix)
            if (user1 != null) {
                logger.info("getUserByLegitId: Found user by prefix as username: {}", prefix)
                return user1
            }
            
            // Try 2: Prefix contains a suffix (e.g. "frutus.x92b@legit" or "john.doe.legacy@legit")
            if (prefix.contains(".")) {
                val username = prefix.substringBeforeLast(".")
                logger.info("getUserByLegitId: Extracted username from dotted prefix: {}", username)
                val user2 = getUserByUsername(username)
                if (user2 != null) {
                    logger.info("getUserByLegitId: Found user by extracted username: {}", username)
                    return user2
                }
            }
        }
        
        logger.warn("getUserByLegitId: No user found for identifier: {}", identifier)
        return null
    }

    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> {
        val user = getUserById(userId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.USER_NOT_FOUND,
                    message = "User not found"
                )
            )
        return Result.success(user.toProfileResponse())
    }

    suspend fun updateProfile(
        userId: String,
        fullName: String? = null,
        phoneNumber: String? = null
    ): Result<UserProfileResponse> {
        val user = getUserById(userId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.USER_NOT_FOUND,
                    message = "User not found"
                )
            )

        val updates = mutableListOf(
            Updates.set("updatedAt", System.currentTimeMillis())
        )

        if (!fullName.isNullOrBlank()) {
            updates.add(Updates.set("fullName", fullName.trim()))
        }
        if (phoneNumber != null) {
            updates.add(Updates.set("phoneNumber", phoneNumber.trim()))
        }

        MongoDB.users.updateOne(
            Filters.eq("_id", ObjectId(userId)),
            Updates.combine(updates)
        )

        val updatedUser = getUserById(userId)!!
        return Result.success(updatedUser.toProfileResponse())
    }

    suspend fun updateFcmToken(userId: String, token: String): Result<Unit> {
        try {
            val result = MongoDB.users.updateOne(
                Filters.eq("_id", ObjectId(userId)),
                Updates.combine(
                    Updates.set("fcmToken", token),
                    Updates.set("updatedAt", System.currentTimeMillis())
                )
            )
            
            if (result.matchedCount == 0L) {
                return Result.failure(
                    NotFoundException(
                        code = ErrorCodes.USER_NOT_FOUND,
                        message = "User not found"
                    )
                )
            }
            
            return Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to update FCM token for user $userId", e)
            return Result.failure(e)
        }
    }

    suspend fun changePassword(
        userId: String,
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        val user = getUserById(userId)
            ?: return Result.failure(
                NotFoundException(
                    code = ErrorCodes.USER_NOT_FOUND,
                    message = "User not found"
                )
            )

        if (!verifyPassword(currentPassword, user.passwordHash)) {
            return Result.failure(
                AuthenticationException(
                    code = ErrorCodes.INVALID_CREDENTIALS,
                    message = "Current password is incorrect"
                )
            )
        }

        val passwordErrors = validatePassword(newPassword)
        if (passwordErrors.isNotEmpty()) {
            return Result.failure(ValidationException(passwordErrors))
        }

        val newHash = hashPassword(newPassword)

        MongoDB.users.updateOne(
            Filters.eq("_id", ObjectId(userId)),
            Updates.combine(
                Updates.set("passwordHash", newHash),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )

        return Result.success(Unit)
    }

    // ========================
    // SESSION MANAGEMENT
    // ========================

    fun createSession(userId: String, username: String, legitId: String, role: UserRole): UserSession {
        val sessionId = generateSessionId()
        return UserSession(
            userId = userId,
            username = username,
            legitId = legitId,
            role = role,
            sessionId = sessionId
        )
    }

    fun validateSession(session: UserSession): Boolean {
        val sessionMaxAge = 24 * 60 * 60 * 1000L // 24 hours
        val age = System.currentTimeMillis() - session.createdAt
        return age < sessionMaxAge
    }

    // ========================
    // JWT TOKEN GENERATION
    // ========================

    private fun generateAccessToken(userId: String, username: String, legitId: String, role: UserRole): String {
        return JWT.create()
            .withSubject(userId)
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("username", username)
            .withClaim("legitId", legitId)
            .withClaim("role", role.name)
            .withClaim("type", "access")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + jwtExpirationMs))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun generateRefreshToken(userId: String): String {
        return JWT.create()
            .withSubject(userId)
            .withIssuer(jwtIssuer)
            .withClaim("type", "refresh")
            .withClaim("jti", UUID.randomUUID().toString())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + refreshExpirationMs))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    // ========================
    // HASHING & SECURITY
    // ========================

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }

    private fun generateSessionId(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // ========================
    // VALIDATION
    // ========================

    private fun validateRegistration(request: RegisterRequest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (request.username.isBlank()) {
            errors.add(ValidationError("username", "Username is required"))
        } else if (request.username.length < 3) {
            errors.add(ValidationError("username", "Username must be at least 3 characters"))
        } else if (request.username.length > 30) {
            errors.add(ValidationError("username", "Username must be at most 30 characters"))
        } else if (!request.username.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
            errors.add(ValidationError("username", "Username can only contain letters, numbers, dots, hyphens, and underscores"))
        }

        if (request.email.isBlank()) {
            errors.add(ValidationError("email", "Email is required"))
        } else if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            errors.add(ValidationError("email", "Invalid email format"))
        }

        errors.addAll(validatePassword(request.password))

        if (request.fullName.isBlank()) {
            errors.add(ValidationError("fullName", "Full name is required"))
        } else if (request.fullName.length < 2) {
            errors.add(ValidationError("fullName", "Full name must be at least 2 characters"))
        }

        if (request.phoneNumber != null && request.phoneNumber.isNotBlank()) {
            if (!request.phoneNumber.matches(Regex("^\\+?[0-9]{10,15}$"))) {
                errors.add(ValidationError("phoneNumber", "Invalid phone number format"))
            }
        }

        return errors
    }

    private fun validatePassword(password: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (password.isBlank()) {
            errors.add(ValidationError("password", "Password is required"))
        } else {
            if (password.length < 8) {
                errors.add(ValidationError("password", "Password must be at least 8 characters"))
            }
            if (!password.any { it.isUpperCase() }) {
                errors.add(ValidationError("password", "Password must contain at least one uppercase letter"))
            }
            if (!password.any { it.isLowerCase() }) {
                errors.add(ValidationError("password", "Password must contain at least one lowercase letter"))
            }
            if (!password.any { it.isDigit() }) {
                errors.add(ValidationError("password", "Password must contain at least one digit"))
            }
            if (!password.any { !it.isLetterOrDigit() }) {
                errors.add(ValidationError("password", "Password must contain at least one special character"))
            }
        }

        return errors
    }
}
