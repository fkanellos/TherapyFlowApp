package io.therapyflow.data.repository

import io.therapyflow.domain.model.Appointment
import java.time.Instant
import java.util.*

interface AppointmentRepository {
    suspend fun findAll(): List<Appointment>
    suspend fun findById(id: UUID): Appointment?
    suspend fun findByTherapistId(therapistId: UUID): List<Appointment>
    suspend fun findByDateRange(from: Instant, to: Instant): List<Appointment>
    suspend fun findByTherapistAndDateRange(therapistId: UUID, from: Instant, to: Instant): List<Appointment>
    suspend fun create(appointment: Appointment): Appointment
    suspend fun update(appointment: Appointment): Appointment
    suspend fun softDelete(id: UUID)
}
