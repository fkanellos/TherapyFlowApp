package io.therapyflow.data.table

import io.therapyflow.domain.model.AppointmentSource
import io.therapyflow.domain.model.AppointmentStatus
import io.therapyflow.domain.model.SessionType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Tenant-scoped table — always accessed via tenantTransaction.
 * Schema set dynamically via search_path.
 */
object AppointmentTable : Table("appointments") {
    val id = uuid("id").autoGenerate()
    val therapistId = uuid("therapist_id").references(TherapistTable.id)
    val clientId = uuid("client_id").references(ClientTable.id)
    val startTime = timestamp("start_time")
    val durationMinutes = integer("duration_minutes")
    val price = decimal("price", 10, 2)
    val sessionType = enumerationByName<SessionType>("session_type", 50)
    val status = enumerationByName<AppointmentStatus>("status", 50).default(AppointmentStatus.SCHEDULED)
    val googleCalendarEventId = varchar("google_calendar_event_id", 200).nullable()
    val appointmentSource = enumerationByName<AppointmentSource>("source", 50).default(AppointmentSource.MANUAL)
    val notes = text("notes").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
