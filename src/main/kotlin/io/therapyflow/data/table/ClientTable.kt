package io.therapyflow.data.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Tenant-scoped table — always accessed via tenantTransaction.
 * Schema set dynamically via search_path.
 */
object ClientTable : Table("clients") {
    val id = uuid("id").autoGenerate()
    val therapistId = uuid("therapist_id").references(TherapistTable.id)
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val googleCalendarName = varchar("google_calendar_name", 200).nullable()
    val customPrice = decimal("custom_price", 10, 2).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
