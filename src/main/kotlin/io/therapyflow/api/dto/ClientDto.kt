package io.therapyflow.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRequest(
    val therapistId: String,
    val firstName: String,
    val lastName: String,
    val googleCalendarName: String? = null,
    val customPrice: Double? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (firstName.isBlank()) errors.add("First name is required")
        if (lastName.isBlank()) errors.add("Last name is required")
        if (customPrice != null && customPrice < 0) errors.add("Custom price cannot be negative")
        return errors
    }
}

@Serializable
data class UpdateClientRequest(
    val firstName: String,
    val lastName: String,
    val googleCalendarName: String? = null,
    val customPrice: Double? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (firstName.isBlank()) errors.add("First name is required")
        if (lastName.isBlank()) errors.add("Last name is required")
        if (customPrice != null && customPrice < 0) errors.add("Custom price cannot be negative")
        return errors
    }
}

@Serializable
data class ClientResponse(
    val id: String,
    val therapistId: String,
    val firstName: String,
    val lastName: String,
    val googleCalendarName: String? = null,
    val customPrice: Double? = null,
    val createdAt: String,
    val updatedAt: String
)
