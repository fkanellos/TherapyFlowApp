package io.therapyflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.api.routes.authRoutes
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val version: String)

fun Application.configureRouting() {
    routing {
        // Health check — used by Railway/Hetzner Docker healthcheck
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(
                status  = "ok",
                version = "0.1.0"
            ))
        }

        // API v1 — add route files here as they are implemented
        route("/v1") {
            authRoutes()
            // therapistRoutes()      ← uncomment when therapists are implemented
            // appointmentRoutes()    ← uncomment when appointments are implemented
            // payrollRoutes()        ← uncomment when payroll is implemented
        }
    }
}
