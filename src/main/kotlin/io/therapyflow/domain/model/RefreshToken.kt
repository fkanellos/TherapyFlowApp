package io.therapyflow.domain.model

import java.time.Instant
import java.util.*

data class RefreshToken(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean,
    val createdAt: Instant
)
