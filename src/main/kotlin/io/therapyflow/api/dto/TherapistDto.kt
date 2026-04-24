package io.therapyflow.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTherapistRequest(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val commissionRate: Double,
    val receivesSupervisionFee: Boolean = false
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (firstName.isBlank()) errors.add("First name is required")
        if (lastName.isBlank()) errors.add("Last name is required")
        if (commissionRate < 0 || commissionRate > 1)
            errors.add("Commission rate must be between 0 and 1")
        return errors
    }
}

@Serializable
data class UpdateTherapistRequest(
    val firstName: String,
    val lastName: String,
    val commissionRate: Double,
    val receivesSupervisionFee: Boolean
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (firstName.isBlank()) errors.add("First name is required")
        if (lastName.isBlank()) errors.add("Last name is required")
        if (commissionRate < 0 || commissionRate > 1)
            errors.add("Commission rate must be between 0 and 1")
        return errors
    }
}

@Serializable
data class TherapistResponse(
    val id: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
    val commissionRate: Double,
    val receivesSupervisionFee: Boolean,
    val createdAt: String,
    val updatedAt: String
)
