package io.therapyflow.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CalculatePayrollRequest(
    val month: Int,
    val year: Int
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (month !in 1..12) errors.add("Month must be between 1 and 12")
        if (year < 2020 || year > 2100) errors.add("Year must be between 2020 and 2100")
        return errors
    }
}

@Serializable
data class PayrollPeriodResponse(
    val id: String,
    val month: Int,
    val year: Int,
    val status: String,
    val createdAt: String,
    val finalizedAt: String?,
    val entries: List<PayrollEntryResponse>
)

@Serializable
data class PayrollEntryResponse(
    val id: String,
    val therapistId: String,
    val totalSessions: Int,
    val totalRevenue: Double,
    val commissionAmount: Double,
    val breakdown: String?,
    val createdAt: String
)
