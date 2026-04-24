package io.therapyflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.therapyflow.domain.error.AppError
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {

        // Domain errors → structured JSON response
        exception<AppError> { call, error ->
            logger.warn("AppError: ${error.code} — ${error.message}")
            call.respond(
                status = HttpStatusCode.fromValue(error.httpStatus),
                message = mapOf(
                    "error"   to error.code,
                    "message" to error.message,
                    "status"  to error.httpStatus
                )
            )
        }

        // Bad request / deserialization
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "BAD_REQUEST", "message" to (cause.message ?: "Invalid request"))
            )
        }

        // Catch-all — never expose stack traces in production
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "INTERNAL_ERROR", "message" to "An unexpected error occurred")
            )
        }

        // 404 for unknown routes
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "NOT_FOUND", "message" to "Route not found")
            )
        }
    }
}
