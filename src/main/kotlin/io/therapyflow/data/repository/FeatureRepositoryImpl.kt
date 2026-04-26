package io.therapyflow.data.repository

import io.therapyflow.data.db.publicTransaction
import io.therapyflow.data.table.WorkspaceFeatureTable
import io.therapyflow.domain.model.WorkspaceFeature
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.*
import java.util.*

class FeatureRepositoryImpl : FeatureRepository {

    override suspend fun findByWorkspaceAndKey(workspaceId: UUID, featureKey: String): WorkspaceFeature? =
        publicTransaction {
            WorkspaceFeatureTable.selectAll()
                .where {
                    (WorkspaceFeatureTable.workspaceId eq workspaceId) and
                    (WorkspaceFeatureTable.featureKey eq featureKey)
                }
                .map { it.toWorkspaceFeature() }
                .singleOrNull()
        }

    override suspend fun findAllByWorkspace(workspaceId: UUID): List<WorkspaceFeature> = publicTransaction {
        WorkspaceFeatureTable.selectAll()
            .where { WorkspaceFeatureTable.workspaceId eq workspaceId }
            .map { it.toWorkspaceFeature() }
    }

    override suspend fun enable(workspaceId: UUID, featureKey: String, enabledBy: UUID?): Unit =
        publicTransaction {
            val now = Clock.System.now()
            val exists = WorkspaceFeatureTable.selectAll()
                .where {
                    (WorkspaceFeatureTable.workspaceId eq workspaceId) and
                    (WorkspaceFeatureTable.featureKey eq featureKey)
                }
                .count() > 0

            if (exists) {
                WorkspaceFeatureTable.update({
                    (WorkspaceFeatureTable.workspaceId eq workspaceId) and
                    (WorkspaceFeatureTable.featureKey eq featureKey)
                }) {
                    it[isEnabled] = true
                    it[enabledAt] = now
                    it[WorkspaceFeatureTable.enabledBy] = enabledBy
                }
            } else {
                WorkspaceFeatureTable.insert {
                    it[WorkspaceFeatureTable.workspaceId] = workspaceId
                    it[WorkspaceFeatureTable.featureKey] = featureKey
                    it[isEnabled] = true
                    it[enabledAt] = now
                    it[WorkspaceFeatureTable.enabledBy] = enabledBy
                }
            }
        }

    override suspend fun disable(workspaceId: UUID, featureKey: String): Unit = publicTransaction {
        WorkspaceFeatureTable.update({
            (WorkspaceFeatureTable.workspaceId eq workspaceId) and
            (WorkspaceFeatureTable.featureKey eq featureKey)
        }) {
            it[isEnabled] = false
        }
    }

    private fun ResultRow.toWorkspaceFeature() = WorkspaceFeature(
        workspaceId = this[WorkspaceFeatureTable.workspaceId],
        featureKey = this[WorkspaceFeatureTable.featureKey],
        isEnabled = this[WorkspaceFeatureTable.isEnabled],
        enabledAt = this[WorkspaceFeatureTable.enabledAt]?.toJavaInstant(),
        enabledBy = this[WorkspaceFeatureTable.enabledBy]
    )
}
