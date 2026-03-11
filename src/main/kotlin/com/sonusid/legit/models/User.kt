package com.sonusid.legit.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class User(
    @SerialName("_id")
    @Contextual
    val id: ObjectId? = null,
    val username: String,
    val email: String,
    val passwordHash: String,
    val fullName: String,
    val phoneNumber: String? = null,
    val isVerified: Boolean = false,
    val role: UserRole = UserRole.USER,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class UserRole {
    USER,
    ADMIN,
    SERVICE_PROVIDER
}

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val phoneNumber: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val role: UserRole,
    val expiresIn: Long
)

@Serializable
data class UserSession(
    val userId: String,
    val username: String,
    val role: UserRole,
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserProfileResponse(
    val id: String,
    val username: String,
    val email: String,
    val fullName: String,
    val phoneNumber: String?,
    val isVerified: Boolean,
    val role: UserRole,
    val createdAt: Long
)

fun User.toProfileResponse(): UserProfileResponse = UserProfileResponse(
    id = id?.toHexString() ?: "",
    username = username,
    email = email,
    fullName = fullName,
    phoneNumber = phoneNumber,
    isVerified = isVerified,
    role = role,
    createdAt = createdAt
)
