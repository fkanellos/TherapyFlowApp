package io.therapyflow.data.table

import io.therapyflow.domain.model.PendingChargeStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PendingChargeTable : Table("pending_charges") {
    val id = uuid("id").autoGenerate()
    val clientId = uuid("client_id").references(ClientTable.id)
    val appointmentId = uuid("appointment_id").references(AppointmentTable.id)
    val amount = decimal("amount", 10, 2)
    val reason = varchar("reason", 200)
    val status = enumerationByName<PendingChargeStatus>("status", 50).default(PendingChargeStatus.PENDING)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
