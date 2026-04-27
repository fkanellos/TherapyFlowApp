package io.therapyflow

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.therapyflow.di.appModule
import io.therapyflow.plugins.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toInt() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // 1. Dependency Injection
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    // 2. Database — migrations run at startup
    configureDatabase()

    // 3. Plugins (order matters)
    configureTenantCleanup()   // must be before auth so finally runs after all plugins
    configureSerialization()
    configureSecurityHeaders()
    configureRateLimiting()
    configureCors()
    configureAuthentication()
    configureStatusPages()
    configureRequestValidation()

    // 3.5 Request size limiting (1 MB)
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (contentLength != null && contentLength > 1_048_576) {
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "PAYLOAD_TOO_LARGE"))
            finish()
            return@intercept
        }
    }

    // 4. Routes
    configureRouting()

    // 5. Background jobs (calendar sync etc.)
    // configureScheduler()  ← uncomment when CalendarSyncService is implemented
}
