package io.therapyflow.data.repository

import io.therapyflow.data.db.publicTransaction
import io.therapyflow.data.table.UserTable
import io.therapyflow.domain.model.User
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class UserRepositoryImpl : UserRepository {

    override suspend fun findByEmail(email: String): User? = publicTransaction {
        UserTable.selectAll()
            .where { UserTable.email eq email }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findById(id: UUID): User? = publicTransaction {
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun create(user: User): User = publicTransaction {
        val now = Clock.System.now()
        UserTable.insert {
            it[id] = user.id
            it[workspaceId] = user.workspaceId
            it[email] = user.email
            it[hashedPassword] = user.hashedPassword
            it[role] = user.role
            it[isActive] = user.isActive
            it[createdAt] = now
            it[updatedAt] = now
        }
        user.copy(createdAt = now.toJavaInstant(), updatedAt = now.toJavaInstant())
    }

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        workspaceId = this[UserTable.workspaceId],
        email = this[UserTable.email],
        hashedPassword = this[UserTable.hashedPassword],
        role = this[UserTable.role],
        isActive = this[UserTable.isActive],
        createdAt = this[UserTable.createdAt].toJavaInstant(),
        updatedAt = this[UserTable.updatedAt].toJavaInstant()
    )
}
