package io.therapyflow.domain.model

import java.time.Instant
import java.util.*

data class Workspace(
    val id: UUID,
    val name: String,
    val slug: String,
    val plan: Plan,
    val status: WorkspaceStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
