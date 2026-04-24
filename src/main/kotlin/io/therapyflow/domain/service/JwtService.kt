package io.therapyflow.domain.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.therapyflow.domain.model.User
import io.therapyflow.domain.model.Workspace
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class JwtService(
    private val secret: String,
    private val issuer: String
) {

    fun generateAccessToken(user: User, workspace: Workspace): String =
        JWT.create()
            .withIssuer(issuer)
            .withClaim("userId", user.id.toString())
            .withClaim("workspaceId", workspace.id.toString())
            .withClaim("workspaceSlug", workspace.slug)
            .withClaim("role", user.role.name)
            .withExpiresAt(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
            .withIssuedAt(Date.from(Instant.now()))
            .sign(Algorithm.HMAC256(secret))

    fun generateRefreshToken(): String =
        UUID.randomUUID().toString() + UUID.randomUUID().toString()

    /**
     * Hashes a refresh token for DB storage.
     * Uses SHA-256 — constant-time comparison not needed here because
     * the token itself is a random UUID (no timing oracle).
     */
    fun hashToken(token: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
