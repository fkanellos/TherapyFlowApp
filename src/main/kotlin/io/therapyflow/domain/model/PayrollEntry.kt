package io.therapyflow.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class PayrollEntry(
    val id: UUID,
    val periodId: UUID,
    val therapistId: UUID,
    val totalSessions: Int,
    val totalRevenue: BigDecimal,
    val commissionAmount: BigDecimal,
    val breakdown: String?,  // JSONB — serialized ClientBreakdown list
    val createdAt: Instant
)

/**
 * Per-client breakdown within a payroll entry.
 * Serialized as JSON and stored in payroll_entries.breakdown.
 */
@kotlinx.serialization.Serializable
data class ClientBreakdown(
    val clientId: String,
    val clientName: String,
    val sessions: Int,
    val revenue: Double,
    val lateCancellations: Int
)
