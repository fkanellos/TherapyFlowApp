package io.therapyflow.data.repository

import io.therapyflow.domain.model.PendingCharge
import java.util.*

interface PendingChargeRepository {
    suspend fun create(charge: PendingCharge): PendingCharge
    suspend fun findPendingByClientId(clientId: UUID): List<PendingCharge>
    suspend fun findByAppointmentId(appointmentId: UUID): PendingCharge?
    suspend fun markCollected(id: UUID)
    suspend fun markWaived(id: UUID)
}
