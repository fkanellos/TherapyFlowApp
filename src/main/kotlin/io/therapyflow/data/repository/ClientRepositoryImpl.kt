package io.therapyflow.data.repository

import io.therapyflow.data.db.tenantTransaction
import io.therapyflow.data.table.ClientTable
import io.therapyflow.domain.model.Client
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class ClientRepositoryImpl : ClientRepository {

    override suspend fun findAll(): List<Client> = tenantTransaction {
        ClientTable.selectAll()
            .where { ClientTable.isActive eq true }
            .map { it.toClient() }
    }

    override suspend fun findById(id: UUID): Client? = tenantTransaction {
        ClientTable.selectAll()
            .where { (ClientTable.id eq id) and (ClientTable.isActive eq true) }
            .map { it.toClient() }
            .singleOrNull()
    }

    override suspend fun findByTherapistId(therapistId: UUID): List<Client> = tenantTransaction {
        ClientTable.selectAll()
            .where { (ClientTable.therapistId eq therapistId) and (ClientTable.isActive eq true) }
            .map { it.toClient() }
    }

    override suspend fun create(client: Client): Client = tenantTransaction {
        val now = Clock.System.now()
        ClientTable.insert {
            it[id] = client.id
            it[therapistId] = client.therapistId
            it[firstName] = client.firstName
            it[lastName] = client.lastName
            it[googleCalendarName] = client.googleCalendarName
            it[customPrice] = client.customPrice
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        client.copy(createdAt = now.toJavaInstant(), updatedAt = now.toJavaInstant())
    }

    override suspend fun update(client: Client): Client = tenantTransaction {
        val now = Clock.System.now()
        ClientTable.update({ ClientTable.id eq client.id }) {
            it[firstName] = client.firstName
            it[lastName] = client.lastName
            it[googleCalendarName] = client.googleCalendarName
            it[customPrice] = client.customPrice
            it[updatedAt] = now
        }
        client.copy(updatedAt = now.toJavaInstant())
    }

    override suspend fun softDelete(id: UUID): Unit = tenantTransaction {
        val now = Clock.System.now()
        ClientTable.update({ ClientTable.id eq id }) {
            it[isActive] = false
            it[updatedAt] = now
        }
    }

    private fun ResultRow.toClient() = Client(
        id = this[ClientTable.id],
        therapistId = this[ClientTable.therapistId],
        firstName = this[ClientTable.firstName],
        lastName = this[ClientTable.lastName],
        googleCalendarName = this[ClientTable.googleCalendarName],
        customPrice = this[ClientTable.customPrice],
        isActive = this[ClientTable.isActive],
        createdAt = this[ClientTable.createdAt].toJavaInstant(),
        updatedAt = this[ClientTable.updatedAt].toJavaInstant()
    )
}
