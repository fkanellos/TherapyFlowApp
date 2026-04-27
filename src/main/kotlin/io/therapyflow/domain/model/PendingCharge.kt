package io.therapyflow.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class PendingCharge(
    val id: UUID,
    val clientId: UUID,
    val appointmentId: UUID,
    val amount: BigDecimal,
    val reason: String,
    val status: PendingChargeStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
