package io.therapyflow.data.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Tenant-scoped table — always accessed via tenantTransaction.
 * Schema set dynamically via search_path.
 */
object TherapistTable : Table("therapists") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id")  // references public.users — cross-schema FK
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val commissionRate = decimal("commission_rate", 5, 2)
    val receivesSupervisionFee = bool("receives_supervision_fee").default(false)
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
