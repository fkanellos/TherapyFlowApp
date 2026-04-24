package io.therapyflow.data.repository

import io.therapyflow.domain.model.Therapist
import java.util.*

interface TherapistRepository {
    suspend fun findAll(): List<Therapist>
    suspend fun findById(id: UUID): Therapist?
    suspend fun findByUserId(userId: UUID): Therapist?
    suspend fun create(therapist: Therapist): Therapist
    suspend fun update(therapist: Therapist): Therapist
    suspend fun softDelete(id: UUID)
}
