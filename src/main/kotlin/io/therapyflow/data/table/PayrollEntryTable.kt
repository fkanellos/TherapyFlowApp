package io.therapyflow.data.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PayrollEntryTable : Table("payroll_entries") {
    val id = uuid("id").autoGenerate()
    val periodId = uuid("period_id").references(PayrollPeriodTable.id)
    val therapistId = uuid("therapist_id").references(TherapistTable.id)
    val totalSessions = integer("total_sessions").default(0)
    val totalRevenue = decimal("total_revenue", 10, 2)
    val commissionAmount = decimal("commission_amount", 10, 2)
    val breakdown = text("breakdown").nullable()  // JSONB stored as text
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
