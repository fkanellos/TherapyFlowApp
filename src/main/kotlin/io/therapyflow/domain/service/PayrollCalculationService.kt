package io.therapyflow.domain.service

import io.therapyflow.data.repository.*
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.*

/**
 * Core payroll calculation engine.
 * Battle-tested logic migrated from PayrollDesktop — DO NOT SIMPLIFY.
 *
 * See: docs/features/payroll.md
 */
class PayrollCalculationService(
    private val therapistRepository: TherapistRepository,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val payrollRepository: PayrollRepository,
    private val pendingChargeRepository: PendingChargeRepository
) {

    /**
     * Calculates payroll for all therapists for the given month/year.
     *
     * Idempotency:
     * - DRAFT exists → recalculate (delete old entries, recompute)
     * - FINALIZED exists → throw 409
     * - No period → create new DRAFT
     */
    suspend fun calculate(month: Int, year: Int): PayrollPeriod {
        val existing = payrollRepository.findPeriodByMonthYear(month, year)

        if (existing != null && existing.status == PayrollStatus.FINALIZED) {
            throw AppError.PayrollAlreadyFinalized("$month/$year")
        }

        // Reuse existing DRAFT period or create new one
        val period = if (existing != null) {
            payrollRepository.deleteEntriesForPeriod(existing.id)
            existing
        } else {
            payrollRepository.createPeriod(
                PayrollPeriod(
                    id = UUID.randomUUID(),
                    month = month,
                    year = year,
                    status = PayrollStatus.DRAFT,
                    createdAt = Instant.now(),
                    finalizedAt = null
                )
            )
        }

        // Date range for the month
        val ym = YearMonth.of(year, month)
        val from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = ym.atEndOfMonth().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        val allAppointments = appointmentRepository.findByDateRange(from, to)
        val allTherapists = therapistRepository.findAll()
        val allClients = clientRepository.findAll()
        val clientMap = allClients.associateBy { it.id }

        for (therapist in allTherapists) {
            val therapistAppointments = allAppointments.filter { it.therapistId == therapist.id }
            calculateForTherapist(therapist, therapistAppointments, clientMap, period.id)
        }

        return period
    }

    private suspend fun calculateForTherapist(
        therapist: Therapist,
        appointments: List<Appointment>,
        clientMap: Map<UUID, Client>,
        periodId: UUID
    ) {
        var totalSessions = 0
        var totalRevenue = BigDecimal.ZERO
        val clientBreakdowns = mutableMapOf<UUID, MutableClientBreakdown>()

        for (appointment in appointments) {
            // Skip supervision sessions for therapists that don't receive supervision fees
            if (appointment.sessionType == SessionType.SUPERVISION && !therapist.receivesSupervisionFee) {
                continue
            }

            when (appointment.status) {
                AppointmentStatus.COMPLETED -> {
                    totalSessions++
                    totalRevenue = totalRevenue.add(appointment.price)
                    trackClientBreakdown(clientBreakdowns, appointment, clientMap, lateCancellation = false)
                }

                AppointmentStatus.CANCELLED_LATE, AppointmentStatus.NO_SHOW -> {
                    // Therapist earns the price
                    totalSessions++
                    totalRevenue = totalRevenue.add(appointment.price)
                    trackClientBreakdown(clientBreakdowns, appointment, clientMap, lateCancellation = true)

                    // Create pending charge on client (if not already created)
                    val existingCharge = pendingChargeRepository.findByAppointmentId(appointment.id)
                    if (existingCharge == null) {
                        pendingChargeRepository.create(
                            PendingCharge(
                                id = UUID.randomUUID(),
                                clientId = appointment.clientId,
                                appointmentId = appointment.id,
                                amount = appointment.price,
                                reason = "Late cancellation fee",
                                status = PendingChargeStatus.PENDING,
                                createdAt = Instant.now(),
                                updatedAt = Instant.now()
                            )
                        )
                    }
                }

                AppointmentStatus.CANCELLED_EARLY -> {
                    // Not billable, no penalty — skip
                }

                AppointmentStatus.SCHEDULED -> {
                    // Future appointment — not included in payroll
                }
            }
        }

        val commissionAmount = totalRevenue.multiply(therapist.commissionRate)

        val breakdownList = clientBreakdowns.values.map { cb ->
            ClientBreakdown(
                clientId = cb.clientId.toString(),
                clientName = cb.clientName,
                sessions = cb.sessions,
                revenue = cb.revenue.toDouble(),
                lateCancellations = cb.lateCancellations
            )
        }

        payrollRepository.createEntry(
            PayrollEntry(
                id = UUID.randomUUID(),
                periodId = periodId,
                therapistId = therapist.id,
                totalSessions = totalSessions,
                totalRevenue = totalRevenue,
                commissionAmount = commissionAmount,
                breakdown = Json.encodeToString(breakdownList),
                createdAt = Instant.now()
            )
        )
    }

    private fun trackClientBreakdown(
        map: MutableMap<UUID, MutableClientBreakdown>,
        appointment: Appointment,
        clientMap: Map<UUID, Client>,
        lateCancellation: Boolean
    ) {
        val client = clientMap[appointment.clientId]
        val cb = map.getOrPut(appointment.clientId) {
            MutableClientBreakdown(
                clientId = appointment.clientId,
                clientName = client?.fullName ?: "Unknown",
                sessions = 0,
                revenue = BigDecimal.ZERO,
                lateCancellations = 0
            )
        }
        cb.sessions++
        cb.revenue = cb.revenue.add(appointment.price)
        if (lateCancellation) cb.lateCancellations++
    }

    suspend fun finalize(periodId: UUID): PayrollPeriod {
        val period = payrollRepository.findPeriodById(periodId)
            ?: throw AppError.NotFound("PayrollPeriod", periodId.toString())

        if (period.status == PayrollStatus.FINALIZED) {
            throw AppError.PayrollAlreadyFinalized("${period.month}/${period.year}")
        }

        payrollRepository.finalizePeriod(periodId)
        return payrollRepository.findPeriodById(periodId)!!
    }

    private data class MutableClientBreakdown(
        val clientId: UUID,
        val clientName: String,
        var sessions: Int,
        var revenue: BigDecimal,
        var lateCancellations: Int
    )
}
