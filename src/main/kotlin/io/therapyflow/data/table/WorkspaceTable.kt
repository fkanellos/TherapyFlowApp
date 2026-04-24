package io.therapyflow.data.table

import io.therapyflow.domain.model.Plan
import io.therapyflow.domain.model.WorkspaceStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object WorkspaceTable : Table("workspaces") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 200)
    val slug = varchar("slug", 100).uniqueIndex()
    val plan = enumerationByName<Plan>("plan", 50).default(Plan.FREE)
    val primaryColor = varchar("primary_color", 7).nullable()
    val secondaryColor = varchar("secondary_color", 7).nullable()
    val logoUrl = text("logo_url").nullable()
    val status = enumerationByName<WorkspaceStatus>("status", 50).default(WorkspaceStatus.ACTIVE)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
