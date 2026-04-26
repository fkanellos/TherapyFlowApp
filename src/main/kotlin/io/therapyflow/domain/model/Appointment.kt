package io.therapyflow.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class Appointment(
    val id: UUID,
    val therapistId: UUID,
    val clientId: UUID,
    val startTime: Instant,
    val durationMinutes: Int,
    val price: BigDecimal,
    val sessionType: SessionType,
    val status: AppointmentStatus,
    val googleCalendarEventId: String?,
    val source: AppointmentSource,
    val notes: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
