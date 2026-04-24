package io.therapyflow.data.repository

import io.therapyflow.domain.model.RefreshToken
import java.util.*

interface RefreshTokenRepository {
    suspend fun save(token: RefreshToken): RefreshToken
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?
    suspend fun revoke(tokenHash: String)
    suspend fun revokeAllForUser(userId: UUID)
}
