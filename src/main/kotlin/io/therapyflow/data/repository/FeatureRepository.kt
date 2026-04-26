package io.therapyflow.data.repository

import io.therapyflow.domain.model.WorkspaceFeature
import java.util.*

interface FeatureRepository {
    suspend fun findByWorkspaceAndKey(workspaceId: UUID, featureKey: String): WorkspaceFeature?
    suspend fun findAllByWorkspace(workspaceId: UUID): List<WorkspaceFeature>
    suspend fun enable(workspaceId: UUID, featureKey: String, enabledBy: UUID?)
    suspend fun disable(workspaceId: UUID, featureKey: String)
}
