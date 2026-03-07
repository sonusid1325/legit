package com.sonusid.legit.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun <T> success(data: T, message: String = "Success"): ApiResponse<T> = ApiResponse(
            success = true,
            message = message,
            data = data
        )

        fun <T> error(message: String, data: T? = null): ApiResponse<T> = ApiResponse(
            success = false,
            message = message,
            data = data
        )

        fun ok(message: String = "Success"): ApiResponse<Unit> = ApiResponse(
            success = true,
            message = message,
            data = null
        )

        fun fail(message: String): ApiResponse<Unit> = ApiResponse(
            success = false,
            message = message,
            data = null
        )
    }
}

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ValidationError(
    val field: String,
    val message: String
)

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
) {
    companion object {
        fun <T> of(items: List<T>, total: Long, page: Int, pageSize: Int): PaginatedResponse<T> {
            val totalPages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
            return PaginatedResponse(
                items = items,
                total = total,
                page = page,
                pageSize = pageSize,
                totalPages = totalPages
            )
        }
    }
}

// Common error codes used across the application
object ErrorCodes {
    // Auth errors
    const val INVALID_CREDENTIALS = "AUTH_001"
    const val TOKEN_EXPIRED = "AUTH_002"
    const val UNAUTHORIZED = "AUTH_003"
    const val FORBIDDEN = "AUTH_004"
    const val SESSION_EXPIRED = "AUTH_005"

    // User errors
    const val USER_NOT_FOUND = "USER_001"
    const val USER_ALREADY_EXISTS = "USER_002"
    const val INVALID_USER_DATA = "USER_003"
    const val EMAIL_ALREADY_TAKEN = "USER_004"

    // Document errors
    const val DOCUMENT_NOT_FOUND = "DOC_001"
    const val DOCUMENT_ALREADY_EXISTS = "DOC_002"
    const val INVALID_DOCUMENT_DATA = "DOC_003"
    const val DOCUMENT_UPLOAD_FAILED = "DOC_004"
    const val DOCUMENT_TYPE_NOT_SUPPORTED = "DOC_005"

    // Verification / Contract errors
    const val CONTRACT_NOT_FOUND = "CONTRACT_001"
    const val CONTRACT_ALREADY_APPROVED = "CONTRACT_002"
    const val CONTRACT_EXPIRED = "CONTRACT_003"
    const val INVALID_DISPOSABLE_KEY = "CONTRACT_004"
    const val KEY_EXPIRED = "CONTRACT_005"
    const val KEY_ALREADY_USED = "CONTRACT_006"
    const val VERIFICATION_FAILED = "CONTRACT_007"
    const val MISSING_REQUIRED_DOCUMENTS = "CONTRACT_008"
    const val CONTRACT_NOT_APPROVED = "CONTRACT_009"

    // Pipeline errors
    const val PIPELINE_ERROR = "PIPE_001"
    const val PIPELINE_TIMEOUT = "PIPE_002"

    // General errors
    const val INTERNAL_ERROR = "GEN_001"
    const val VALIDATION_ERROR = "GEN_002"
    const val BAD_REQUEST = "GEN_003"
    const val RATE_LIMITED = "GEN_004"
    const val SERVICE_UNAVAILABLE = "GEN_005"
}

// Custom exceptions that map to specific error responses
open class LegitException(
    val code: String,
    override val message: String,
    val statusCode: Int = 500
) : RuntimeException(message)

class AuthenticationException(
    code: String = ErrorCodes.UNAUTHORIZED,
    message: String = "Authentication required"
) : LegitException(code, message, 401)

class AuthorizationException(
    code: String = ErrorCodes.FORBIDDEN,
    message: String = "You do not have permission to perform this action"
) : LegitException(code, message, 403)

class NotFoundException(
    code: String,
    message: String
) : LegitException(code, message, 404)

class ConflictException(
    code: String,
    message: String
) : LegitException(code, message, 409)

class BadRequestException(
    code: String = ErrorCodes.BAD_REQUEST,
    message: String = "Invalid request"
) : LegitException(code, message, 400)

class ValidationException(
    val errors: List<ValidationError>,
    message: String = "Validation failed"
) : LegitException(ErrorCodes.VALIDATION_ERROR, message, 422)

class RateLimitException(
    message: String = "Too many requests. Please try again later."
) : LegitException(ErrorCodes.RATE_LIMITED, message, 429)
