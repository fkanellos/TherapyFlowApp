package io.therapyflow.data.repository

import io.therapyflow.data.db.tenantTransaction
import io.therapyflow.data.table.PendingChargeTable
import io.therapyflow.domain.model.PendingCharge
import io.therapyflow.domain.model.PendingChargeStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.*
import java.util.*

class PendingChargeRepositoryImpl : PendingChargeRepository {

    override suspend fun create(charge: PendingCharge): PendingCharge = tenantTransaction {
        val now = Clock.System.now()
        PendingChargeTable.insert {
            it[id] = charge.id
            it[clientId] = charge.clientId
            it[appointmentId] = charge.appointmentId
            it[amount] = charge.amount
            it[reason] = charge.reason
            it[status] = PendingChargeStatus.PENDING
            it[createdAt] = now
            it[updatedAt] = now
        }
        charge.copy(createdAt = now.toJavaInstant(), updatedAt = now.toJavaInstant())
    }

    override suspend fun findPendingByClientId(clientId: UUID): List<PendingCharge> = tenantTransaction {
        PendingChargeTable.selectAll()
            .where {
                (PendingChargeTable.clientId eq clientId) and
                (PendingChargeTable.status eq PendingChargeStatus.PENDING)
            }
            .map { it.toCharge() }
    }

    override suspend fun findByAppointmentId(appointmentId: UUID): PendingCharge? = tenantTransaction {
        PendingChargeTable.selectAll()
            .where { PendingChargeTable.appointmentId eq appointmentId }
            .map { it.toCharge() }
            .singleOrNull()
    }

    override suspend fun markCollected(id: UUID): Unit = tenantTransaction {
        val now = Clock.System.now()
        PendingChargeTable.update({ PendingChargeTable.id eq id }) {
            it[status] = PendingChargeStatus.COLLECTED
            it[updatedAt] = now
        }
    }

    override suspend fun markWaived(id: UUID): Unit = tenantTransaction {
        val now = Clock.System.now()
        PendingChargeTable.update({ PendingChargeTable.id eq id }) {
            it[status] = PendingChargeStatus.WAIVED
            it[updatedAt] = now
        }
    }

    private fun ResultRow.toCharge() = PendingCharge(
        id = this[PendingChargeTable.id],
        clientId = this[PendingChargeTable.clientId],
        appointmentId = this[PendingChargeTable.appointmentId],
        amount = this[PendingChargeTable.amount],
        reason = this[PendingChargeTable.reason],
        status = this[PendingChargeTable.status],
        createdAt = this[PendingChargeTable.createdAt].toJavaInstant(),
        updatedAt = this[PendingChargeTable.updatedAt].toJavaInstant()
    )
}
