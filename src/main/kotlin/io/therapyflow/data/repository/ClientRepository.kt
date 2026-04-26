package io.therapyflow.data.repository

import io.therapyflow.domain.model.Client
import java.util.*

interface ClientRepository {
    suspend fun findAll(): List<Client>
    suspend fun findById(id: UUID): Client?
    suspend fun findByTherapistId(therapistId: UUID): List<Client>
    suspend fun create(client: Client): Client
    suspend fun update(client: Client): Client
    suspend fun softDelete(id: UUID)
}
