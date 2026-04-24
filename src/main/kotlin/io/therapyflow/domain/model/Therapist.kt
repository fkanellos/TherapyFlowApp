package io.therapyflow.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class Therapist(
    val id: UUID,
    val userId: UUID,
    val firstName: String,
    val lastName: String,
    val commissionRate: BigDecimal,
    val receivesSupervisionFee: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val fullName: String get() = "$firstName $lastName"
}
