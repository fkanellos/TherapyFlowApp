package io.therapyflow.data.repository

import io.therapyflow.domain.model.User
import java.util.*

interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findById(id: UUID): User?
    suspend fun create(user: User): User
}
