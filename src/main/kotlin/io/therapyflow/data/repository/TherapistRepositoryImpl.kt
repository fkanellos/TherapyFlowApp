package io.therapyflow.data.repository

import io.therapyflow.data.db.tenantTransaction
import io.therapyflow.data.table.TherapistTable
import io.therapyflow.domain.model.Therapist
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class TherapistRepositoryImpl : TherapistRepository {

    override suspend fun findAll(): List<Therapist> = tenantTransaction {
        TherapistTable.selectAll()
            .where { TherapistTable.isActive eq true }
            .map { it.toTherapist() }
    }

    override suspend fun findById(id: UUID): Therapist? = tenantTransaction {
        TherapistTable.selectAll()
            .where { (TherapistTable.id eq id) and (TherapistTable.isActive eq true) }
            .map { it.toTherapist() }
            .singleOrNull()
    }

    override suspend fun findByUserId(userId: UUID): Therapist? = tenantTransaction {
        TherapistTable.selectAll()
            .where { (TherapistTable.userId eq userId) and (TherapistTable.isActive eq true) }
            .map { it.toTherapist() }
            .singleOrNull()
    }

    override suspend fun create(therapist: Therapist): Therapist = tenantTransaction {
        val now = Clock.System.now()
        TherapistTable.insert {
            it[id] = therapist.id
            it[userId] = therapist.userId
            it[firstName] = therapist.firstName
            it[lastName] = therapist.lastName
            it[commissionRate] = therapist.commissionRate
            it[receivesSupervisionFee] = therapist.receivesSupervisionFee
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        therapist.copy(createdAt = now.toJavaInstant(), updatedAt = now.toJavaInstant())
    }

    override suspend fun update(therapist: Therapist): Therapist = tenantTransaction {
        val now = Clock.System.now()
        TherapistTable.update({ TherapistTable.id eq therapist.id }) {
            it[firstName] = therapist.firstName
            it[lastName] = therapist.lastName
            it[commissionRate] = therapist.commissionRate
            it[receivesSupervisionFee] = therapist.receivesSupervisionFee
            it[updatedAt] = now
        }
        therapist.copy(updatedAt = now.toJavaInstant())
    }

    override suspend fun softDelete(id: UUID): Unit = tenantTransaction {
        val now = Clock.System.now()
        TherapistTable.update({ TherapistTable.id eq id }) {
            it[isActive] = false
            it[updatedAt] = now
        }
    }

    private fun ResultRow.toTherapist() = Therapist(
        id = this[TherapistTable.id],
        userId = this[TherapistTable.userId],
        firstName = this[TherapistTable.firstName],
        lastName = this[TherapistTable.lastName],
        commissionRate = this[TherapistTable.commissionRate],
        receivesSupervisionFee = this[TherapistTable.receivesSupervisionFee],
        isActive = this[TherapistTable.isActive],
        createdAt = this[TherapistTable.createdAt].toJavaInstant(),
        updatedAt = this[TherapistTable.updatedAt].toJavaInstant()
    )
}
