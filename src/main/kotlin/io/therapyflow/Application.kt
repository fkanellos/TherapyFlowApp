package io.therapyflow

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
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
    configureCors()
    configureAuthentication()
    configureStatusPages()
    configureRequestValidation()

    // 4. Routes
    configureRouting()

    // 5. Background jobs (calendar sync etc.)
    // configureScheduler()  ← uncomment when CalendarSyncService is implemented
}
