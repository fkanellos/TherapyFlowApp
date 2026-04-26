package io.therapyflow.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class Client(
    val id: UUID,
    val therapistId: UUID,
    val firstName: String,
    val lastName: String,
    val googleCalendarName: String?,
    val customPrice: BigDecimal?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val fullName: String get() = "$firstName $lastName"
}
