package io.therapyflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.therapyflow.domain.error.AppError
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

@Serializable
data class ErrorResponse(val error: String, val message: String, val status: Int)

fun Application.configureStatusPages() {
    install(StatusPages) {

        // Domain errors → structured JSON response
        exception<AppError> { call, error ->
            logger.warn("AppError: ${error.code} — ${error.message}")
            call.respond(
                status = HttpStatusCode.fromValue(error.httpStatus),
                message = ErrorResponse(
                    error = error.code,
                    message = error.message,
                    status = error.httpStatus
                )
            )
        }

        // Bad request / deserialization
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "BAD_REQUEST",
                    message = cause.message ?: "Invalid request",
                    status = 400
                )
            )
        }

        // Catch-all — never expose stack traces in production
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "INTERNAL_ERROR",
                    message = "An unexpected error occurred",
                    status = 500
                )
            )
        }

        // 404 for unknown routes
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = "NOT_FOUND",
                    message = "Route not found",
                    status = 404
                )
            )
        }
    }
}
