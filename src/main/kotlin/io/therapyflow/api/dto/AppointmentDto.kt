package io.therapyflow.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateAppointmentRequest(
    val therapistId: String,
    val clientId: String,
    val startTime: String,
    val durationMinutes: Int,
    val price: Double,
    val sessionType: String,
    val notes: String? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (durationMinutes <= 0) errors.add("Duration must be positive")
        if (price < 0) errors.add("Price cannot be negative")
        val validTypes = listOf("INDIVIDUAL", "COUPLE", "GROUP", "SUPERVISION")
        if (sessionType !in validTypes) errors.add("Session type must be one of: $validTypes")
        return errors
    }
}

@Serializable
data class UpdateAppointmentRequest(
    val startTime: String? = null,
    val durationMinutes: Int? = null,
    val price: Double? = null,
    val sessionType: String? = null,
    val status: String? = null,
    val notes: String? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (durationMinutes != null && durationMinutes <= 0) errors.add("Duration must be positive")
        if (price != null && price < 0) errors.add("Price cannot be negative")
        val validTypes = listOf("INDIVIDUAL", "COUPLE", "GROUP", "SUPERVISION")
        if (sessionType != null && sessionType !in validTypes)
            errors.add("Session type must be one of: $validTypes")
        val validStatuses = listOf("SCHEDULED", "COMPLETED", "CANCELLED_EARLY", "CANCELLED_LATE", "NO_SHOW")
        if (status != null && status !in validStatuses)
            errors.add("Status must be one of: $validStatuses")
        return errors
    }
}

@Serializable
data class AppointmentResponse(
    val id: String,
    val therapistId: String,
    val clientId: String,
    val startTime: String,
    val durationMinutes: Int,
    val price: Double,
    val sessionType: String,
    val status: String,
    val source: String,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String
)
