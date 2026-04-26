package io.therapyflow.domain.model

import java.time.Instant
import java.util.*

data class WorkspaceFeature(
    val workspaceId: UUID,
    val featureKey: String,
    val isEnabled: Boolean,
    val enabledAt: Instant?,
    val enabledBy: UUID?
)
