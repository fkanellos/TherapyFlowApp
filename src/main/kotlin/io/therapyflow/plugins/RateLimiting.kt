package io.therapyflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate limiter for auth endpoints.
 * Tracks attempts per IP — max 5 per 15-minute window.
 * MVP implementation; swap to Redis for multi-instance deployments.
 */
fun Application.configureRateLimiting() {
    val attempts = ConcurrentHashMap<String, MutableList<Long>>()
    val maxAttempts = 5
    val windowMs = 15 * 60 * 1000L // 15 minutes

    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        if (path == "/v1/auth/login" || path == "/v1/auth/register") {
            if (call.request.httpMethod == HttpMethod.Post) {
                val ip = call.request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteAddress

                val now = System.currentTimeMillis()
                val record = attempts.getOrPut(ip) { mutableListOf() }

                val retryAfter: Long? = synchronized(record) {
                    record.removeAll { now - it > windowMs }

                    if (record.size >= maxAttempts) {
                        val oldestInWindow = record.first()
                        ((oldestInWindow + windowMs - now) / 1000).coerceAtLeast(1)
                    } else {
                        record.add(now)
                        null
                    }
                }

                if (retryAfter != null) {
                    call.response.header("Retry-After", retryAfter.toString())
                    call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "TOO_MANY_REQUESTS"))
                    finish()
                    return@intercept
                }
            }
        }
    }
}
