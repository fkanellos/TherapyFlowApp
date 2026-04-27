package io.therapyflow.data.repository

import io.therapyflow.domain.model.PayrollEntry
import io.therapyflow.domain.model.PayrollPeriod
import java.util.*

interface PayrollRepository {
    suspend fun findPeriodByMonthYear(month: Int, year: Int): PayrollPeriod?
    suspend fun findPeriodById(id: UUID): PayrollPeriod?
    suspend fun createPeriod(period: PayrollPeriod): PayrollPeriod
    suspend fun finalizePeriod(id: UUID)
    suspend fun deleteEntriesForPeriod(periodId: UUID)
    suspend fun createEntry(entry: PayrollEntry): PayrollEntry
    suspend fun findEntriesByPeriod(periodId: UUID): List<PayrollEntry>
}
