package io.therapyflow.data.table

import io.therapyflow.domain.model.UserRole
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserTable : Table("users") {
    val id = uuid("id").autoGenerate()
    val workspaceId = uuid("workspace_id").references(WorkspaceTable.id)
    val email = varchar("email", 255).uniqueIndex()
    val hashedPassword = varchar("hashed_password", 255)
    val role = enumerationByName<UserRole>("role", 50)
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
