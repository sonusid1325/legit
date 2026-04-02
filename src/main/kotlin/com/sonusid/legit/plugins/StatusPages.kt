package com.sonusid.legit.plugins

import com.sonusid.legit.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {

        // Handle our custom exception hierarchy
        exception<LegitException> { call, cause ->
            val statusCode = when (cause.statusCode) {
                400 -> HttpStatusCode.BadRequest
                401 -> HttpStatusCode.Unauthorized
                403 -> HttpStatusCode.Forbidden
                404 -> HttpStatusCode.NotFound
                409 -> HttpStatusCode.Conflict
                429 -> HttpStatusCode.TooManyRequests
                422 -> HttpStatusCode.UnprocessableEntity
                else -> HttpStatusCode.InternalServerError
            }

            logger.warn("LegitException [{}]: {} - {}", cause.code, statusCode.value, cause.message)

            call.respond(
                statusCode,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Validation exceptions with field-level detail
        exception<ValidationException> { call, cause ->
            logger.warn("ValidationException: {} errors", cause.errors.size)

            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiError(
                    code = cause.code,
                    message = cause.message,
                    details = cause.errors.associate { it.field to it.message }
                )
            )
        }

        // Authentication exceptions
        exception<AuthenticationException> { call, cause ->
            logger.warn("AuthenticationException: {}", cause.message)

            call.respond(
                HttpStatusCode.Unauthorized,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Authorization exceptions
        exception<AuthorizationException> { call, cause ->
            logger.warn("AuthorizationException: {}", cause.message)

            call.respond(
                HttpStatusCode.Forbidden,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Not found exceptions
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Conflict exceptions
        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Bad request exceptions
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Rate limit exceptions
        exception<RateLimitException> { call, cause ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiError(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        // Catch kotlinx serialization errors (malformed JSON, missing fields, etc.)
        exception<kotlinx.serialization.SerializationException> { call, cause ->
            logger.warn("SerializationException: {}", cause.message)

            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(
                    code = ErrorCodes.BAD_REQUEST,
                    message = "Invalid request body: ${cause.message ?: "malformed JSON"}"
                )
            )
        }

        // Catch content negotiation / content type issues
        exception<io.ktor.server.plugins.ContentTransformationException> { call, cause ->
            logger.warn("ContentTransformationException: {}", cause.message)

            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(
                    code = ErrorCodes.BAD_REQUEST,
                    message = "Invalid request format: ${cause.message ?: "could not parse request body"}"
                )
            )
        }

        // Catch IllegalArgumentException (often from ObjectId parsing, etc.)
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("IllegalArgumentException: {}", cause.message)

            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(
                    code = ErrorCodes.BAD_REQUEST,
                    message = cause.message ?: "Invalid argument"
                )
            )
        }

        // Catch-all for any unhandled exceptions
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)

            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    code = ErrorCodes.INTERNAL_ERROR,
                    message = "An unexpected error occurred. Please try again later."
                )
            )
        }

        // Custom status code handlers
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiError(
                    code = "NOT_FOUND",
                    message = "The requested resource was not found"
                )
            )
        }

<<<<<<< HEAD


=======
>>>>>>> refs/remotes/origin/main
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                status,
                ApiError(
                    code = "METHOD_NOT_ALLOWED",
                    message = "The HTTP method used is not allowed for this endpoint"
                )
            )
        }
    }
}
