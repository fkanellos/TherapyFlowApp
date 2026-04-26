package io.therapyflow.data.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object WorkspaceFeatureTable : Table("workspace_features") {
    val workspaceId = uuid("workspace_id").references(WorkspaceTable.id)
    val featureKey = varchar("feature_key", 100)
    val isEnabled = bool("is_enabled")
    val enabledAt = timestamp("enabled_at").nullable()
    val enabledBy = uuid("enabled_by").nullable()

    override val primaryKey = PrimaryKey(workspaceId, featureKey)
}
