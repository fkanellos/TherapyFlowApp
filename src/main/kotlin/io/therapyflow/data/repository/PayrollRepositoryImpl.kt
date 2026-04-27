package io.therapyflow.data.repository

import io.therapyflow.data.db.tenantTransaction
import io.therapyflow.data.table.PayrollEntryTable
import io.therapyflow.data.table.PayrollPeriodTable
import io.therapyflow.domain.model.PayrollEntry
import io.therapyflow.domain.model.PayrollPeriod
import io.therapyflow.domain.model.PayrollStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class PayrollRepositoryImpl : PayrollRepository {

    override suspend fun findPeriodByMonthYear(month: Int, year: Int): PayrollPeriod? = tenantTransaction {
        PayrollPeriodTable.selectAll()
            .where { (PayrollPeriodTable.month eq month) and (PayrollPeriodTable.year eq year) }
            .map { it.toPeriod() }
            .singleOrNull()
    }

    override suspend fun findPeriodById(id: UUID): PayrollPeriod? = tenantTransaction {
        PayrollPeriodTable.selectAll()
            .where { PayrollPeriodTable.id eq id }
            .map { it.toPeriod() }
            .singleOrNull()
    }

    override suspend fun createPeriod(period: PayrollPeriod): PayrollPeriod = tenantTransaction {
        val now = Clock.System.now()
        PayrollPeriodTable.insert {
            it[id] = period.id
            it[month] = period.month
            it[year] = period.year
            it[status] = PayrollStatus.DRAFT
            it[createdAt] = now
            it[finalizedAt] = null
        }
        period.copy(createdAt = now.toJavaInstant())
    }

    override suspend fun finalizePeriod(id: UUID): Unit = tenantTransaction {
        val now = Clock.System.now()
        PayrollPeriodTable.update({ PayrollPeriodTable.id eq id }) {
            it[status] = PayrollStatus.FINALIZED
            it[finalizedAt] = now
        }
    }

    override suspend fun deleteEntriesForPeriod(periodId: UUID): Unit = tenantTransaction {
        PayrollEntryTable.deleteWhere { PayrollEntryTable.periodId eq periodId }
    }

    override suspend fun createEntry(entry: PayrollEntry): PayrollEntry = tenantTransaction {
        val now = Clock.System.now()
        PayrollEntryTable.insert {
            it[id] = entry.id
            it[periodId] = entry.periodId
            it[therapistId] = entry.therapistId
            it[totalSessions] = entry.totalSessions
            it[totalRevenue] = entry.totalRevenue
            it[commissionAmount] = entry.commissionAmount
            it[breakdown] = entry.breakdown
            it[createdAt] = now
        }
        entry.copy(createdAt = now.toJavaInstant())
    }

    override suspend fun findEntriesByPeriod(periodId: UUID): List<PayrollEntry> = tenantTransaction {
        PayrollEntryTable.selectAll()
            .where { PayrollEntryTable.periodId eq periodId }
            .map { it.toEntry() }
    }

    private fun ResultRow.toPeriod() = PayrollPeriod(
        id = this[PayrollPeriodTable.id],
        month = this[PayrollPeriodTable.month],
        year = this[PayrollPeriodTable.year],
        status = this[PayrollPeriodTable.status],
        createdAt = this[PayrollPeriodTable.createdAt].toJavaInstant(),
        finalizedAt = this[PayrollPeriodTable.finalizedAt]?.toJavaInstant()
    )

    private fun ResultRow.toEntry() = PayrollEntry(
        id = this[PayrollEntryTable.id],
        periodId = this[PayrollEntryTable.periodId],
        therapistId = this[PayrollEntryTable.therapistId],
        totalSessions = this[PayrollEntryTable.totalSessions],
        totalRevenue = this[PayrollEntryTable.totalRevenue],
        commissionAmount = this[PayrollEntryTable.commissionAmount],
        breakdown = this[PayrollEntryTable.breakdown],
        createdAt = this[PayrollEntryTable.createdAt].toJavaInstant()
    )
}
