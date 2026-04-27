package io.therapyflow.domain.model

import java.time.Instant
import java.util.*

data class PayrollPeriod(
    val id: UUID,
    val month: Int,
    val year: Int,
    val status: PayrollStatus,
    val createdAt: Instant,
    val finalizedAt: Instant?
)
