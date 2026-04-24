package io.therapyflow.data.repository

import io.therapyflow.data.db.publicTransaction
import io.therapyflow.data.table.RefreshTokenTable
import io.therapyflow.domain.model.RefreshToken
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.*

class RefreshTokenRepositoryImpl : RefreshTokenRepository {

    override suspend fun save(token: RefreshToken): RefreshToken = publicTransaction {
        val now = Clock.System.now()
        RefreshTokenTable.insert {
            it[id] = token.id
            it[userId] = token.userId
            it[tokenHash] = token.tokenHash
            it[expiresAt] = token.expiresAt.toKotlinInstant()
            it[revoked] = false
            it[createdAt] = now
        }
        token.copy(createdAt = now.toJavaInstant())
    }

    override suspend fun findByTokenHash(tokenHash: String): RefreshToken? = publicTransaction {
        RefreshTokenTable.selectAll()
            .where { RefreshTokenTable.tokenHash eq tokenHash }
            .map { it.toRefreshToken() }
            .singleOrNull()
    }

    override suspend fun revoke(tokenHash: String): Unit = publicTransaction {
        RefreshTokenTable.update({ RefreshTokenTable.tokenHash eq tokenHash }) {
            it[revoked] = true
        }
    }

    override suspend fun revokeAllForUser(userId: UUID): Unit = publicTransaction {
        RefreshTokenTable.update({ RefreshTokenTable.userId eq userId }) {
            it[revoked] = true
        }
    }

    private fun ResultRow.toRefreshToken() = RefreshToken(
        id = this[RefreshTokenTable.id],
        userId = this[RefreshTokenTable.userId],
        tokenHash = this[RefreshTokenTable.tokenHash],
        expiresAt = this[RefreshTokenTable.expiresAt].toJavaInstant(),
        revoked = this[RefreshTokenTable.revoked],
        createdAt = this[RefreshTokenTable.createdAt].toJavaInstant()
    )
}
