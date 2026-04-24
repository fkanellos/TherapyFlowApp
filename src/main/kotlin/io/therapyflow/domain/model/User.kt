package io.therapyflow.domain.model

import java.time.Instant
import java.util.*

data class User(
    val id: UUID,
    val workspaceId: UUID,
    val email: String,
    val hashedPassword: String,
    val role: UserRole,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
