package io.therapyflow.data.table

import io.therapyflow.domain.model.PayrollStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PayrollPeriodTable : Table("payroll_periods") {
    val id = uuid("id").autoGenerate()
    val month = integer("month")
    val year = integer("year")
    val status = enumerationByName<PayrollStatus>("status", 50).default(PayrollStatus.DRAFT)
    val createdAt = timestamp("created_at")
    val finalizedAt = timestamp("finalized_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
