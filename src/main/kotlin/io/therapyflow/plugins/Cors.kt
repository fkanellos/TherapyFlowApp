package io.therapyflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    install(CORS) {
        // Web Admin Dashboard origin
        allowHost("admin.therapyflow.io", schemes = listOf("https"))
        allowHost("app.therapyflow.io", schemes = listOf("https"))

        // Local development
        allowHost("localhost:3000", schemes = listOf("http"))
        allowHost("localhost:5173", schemes = listOf("http"))

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
