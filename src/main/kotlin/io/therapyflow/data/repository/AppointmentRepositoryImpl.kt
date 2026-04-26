package io.therapyflow.data.repository

import io.therapyflow.data.db.tenantTransaction
import io.therapyflow.data.table.AppointmentTable
import io.therapyflow.domain.model.Appointment
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

class AppointmentRepositoryImpl : AppointmentRepository {

    override suspend fun findAll(): List<Appointment> = tenantTransaction {
        AppointmentTable.selectAll()
            .where { AppointmentTable.isActive eq true }
            .orderBy(AppointmentTable.startTime, SortOrder.DESC)
            .map { it.toAppointment() }
    }

    override suspend fun findById(id: UUID): Appointment? = tenantTransaction {
        AppointmentTable.selectAll()
            .where { (AppointmentTable.id eq id) and (AppointmentTable.isActive eq true) }
            .map { it.toAppointment() }
            .singleOrNull()
    }

    override suspend fun findByTherapistId(therapistId: UUID): List<Appointment> = tenantTransaction {
        AppointmentTable.selectAll()
            .where { (AppointmentTable.therapistId eq therapistId) and (AppointmentTable.isActive eq true) }
            .orderBy(AppointmentTable.startTime, SortOrder.DESC)
            .map { it.toAppointment() }
    }

    override suspend fun findByDateRange(from: Instant, to: Instant): List<Appointment> = tenantTransaction {
        val kFrom = from.toKotlinInstant()
        val kTo = to.toKotlinInstant()
        AppointmentTable.selectAll()
            .where {
                (AppointmentTable.startTime greaterEq kFrom) and
                (AppointmentTable.startTime lessEq kTo) and
                (AppointmentTable.isActive eq true)
            }
            .orderBy(AppointmentTable.startTime, SortOrder.ASC)
            .map { it.toAppointment() }
    }

    override suspend fun findByTherapistAndDateRange(
        therapistId: UUID,
        from: Instant,
        to: Instant
    ): List<Appointment> = tenantTransaction {
        val kFrom = from.toKotlinInstant()
        val kTo = to.toKotlinInstant()
        AppointmentTable.selectAll()
            .where {
                (AppointmentTable.therapistId eq therapistId) and
                (AppointmentTable.startTime greaterEq kFrom) and
                (AppointmentTable.startTime lessEq kTo) and
                (AppointmentTable.isActive eq true)
            }
            .orderBy(AppointmentTable.startTime, SortOrder.ASC)
            .map { it.toAppointment() }
    }

    override suspend fun create(appointment: Appointment): Appointment = tenantTransaction {
        val now = Clock.System.now()
        AppointmentTable.insert {
            it[id] = appointment.id
            it[therapistId] = appointment.therapistId
            it[clientId] = appointment.clientId
            it[startTime] = appointment.startTime.toKotlinInstant()
            it[durationMinutes] = appointment.durationMinutes
            it[price] = appointment.price
            it[sessionType] = appointment.sessionType
            it[status] = appointment.status
            it[googleCalendarEventId] = appointment.googleCalendarEventId
            it[appointmentSource] = appointment.source
            it[notes] = appointment.notes
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        appointment.copy(createdAt = now.toJavaInstant(), updatedAt = now.toJavaInstant())
    }

    override suspend fun update(appointment: Appointment): Appointment = tenantTransaction {
        val now = Clock.System.now()
        AppointmentTable.update({ AppointmentTable.id eq appointment.id }) {
            it[startTime] = appointment.startTime.toKotlinInstant()
            it[durationMinutes] = appointment.durationMinutes
            it[price] = appointment.price
            it[sessionType] = appointment.sessionType
            it[status] = appointment.status
            it[notes] = appointment.notes
            it[updatedAt] = now
        }
        appointment.copy(updatedAt = now.toJavaInstant())
    }

    override suspend fun softDelete(id: UUID): Unit = tenantTransaction {
        val now = Clock.System.now()
        AppointmentTable.update({ AppointmentTable.id eq id }) {
            it[isActive] = false
            it[updatedAt] = now
        }
    }

    private fun ResultRow.toAppointment() = Appointment(
        id = this[AppointmentTable.id],
        therapistId = this[AppointmentTable.therapistId],
        clientId = this[AppointmentTable.clientId],
        startTime = this[AppointmentTable.startTime].toJavaInstant(),
        durationMinutes = this[AppointmentTable.durationMinutes],
        price = this[AppointmentTable.price],
        sessionType = this[AppointmentTable.sessionType],
        status = this[AppointmentTable.status],
        googleCalendarEventId = this[AppointmentTable.googleCalendarEventId],
        source = this[AppointmentTable.appointmentSource],
        notes = this[AppointmentTable.notes],
        isActive = this[AppointmentTable.isActive],
        createdAt = this[AppointmentTable.createdAt].toJavaInstant(),
        updatedAt = this[AppointmentTable.updatedAt].toJavaInstant()
    )
}
