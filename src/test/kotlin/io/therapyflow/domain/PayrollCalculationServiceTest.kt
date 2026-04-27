package io.therapyflow.domain

import io.mockk.*
import io.therapyflow.data.repository.*
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.*
import io.therapyflow.domain.service.PayrollCalculationService
import io.therapyflow.fixtures.TestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PayrollCalculationServiceTest {

    private val therapistRepo = mockk<TherapistRepository>()
    private val appointmentRepo = mockk<AppointmentRepository>()
    private val clientRepo = mockk<ClientRepository>()
    private val payrollRepo = mockk<PayrollRepository>()
    private val pendingChargeRepo = mockk<PendingChargeRepository>()

    private lateinit var service: PayrollCalculationService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service = PayrollCalculationService(
            therapistRepo, appointmentRepo, clientRepo, payrollRepo, pendingChargeRepo
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun setupBasicMocks(
        existingPeriod: PayrollPeriod? = null,
        therapists: List<Therapist> = listOf(TestFixtures.therapist()),
        clients: List<Client> = listOf(TestFixtures.client()),
        appointments: List<Appointment> = emptyList()
    ) {
        coEvery { payrollRepo.findPeriodByMonthYear(any(), any()) } returns existingPeriod
        coEvery { payrollRepo.createPeriod(any()) } answers { firstArg() }
        coEvery { payrollRepo.deleteEntriesForPeriod(any()) } returns Unit
        coEvery { payrollRepo.createEntry(any()) } answers { firstArg() }
        coEvery { therapistRepo.findAll() } returns therapists
        coEvery { clientRepo.findAll() } returns clients
        coEvery { appointmentRepo.findByDateRange(any(), any()) } returns appointments
        coEvery { pendingChargeRepo.findByAppointmentId(any()) } returns null
        coEvery { pendingChargeRepo.create(any()) } answers { firstArg() }
    }

    // ── calculate() tests ───────────────────────────────────────────────

    @Test
    fun `calculate creates new DRAFT period when none exists`() = runBlocking {
        setupBasicMocks()

        val period = service.calculate(4, 2026)

        assertEquals(4, period.month)
        assertEquals(2026, period.year)
        assertEquals(PayrollStatus.DRAFT, period.status)
        coVerify { payrollRepo.createPeriod(any()) }
    }

    @Test
    fun `calculate reuses existing DRAFT period and deletes old entries`() = runBlocking {
        val existing = TestFixtures.payrollPeriod()
        setupBasicMocks(existingPeriod = existing)

        val period = service.calculate(4, 2026)

        assertEquals(existing.id, period.id)
        coVerify { payrollRepo.deleteEntriesForPeriod(existing.id) }
        coVerify(exactly = 0) { payrollRepo.createPeriod(any()) }
    }

    @Test
    fun `calculate throws when period is FINALIZED`() = runBlocking {
        val finalized = TestFixtures.payrollPeriod(status = PayrollStatus.FINALIZED)
        coEvery { payrollRepo.findPeriodByMonthYear(4, 2026) } returns finalized

        assertFailsWith<AppError.PayrollAlreadyFinalized> {
            service.calculate(4, 2026)
        }
    }

    @Test
    fun `COMPLETED appointments are counted and revenue added`() = runBlocking {
        val appt = TestFixtures.appointment(
            status = AppointmentStatus.COMPLETED,
            price = BigDecimal("80.00")
        )
        setupBasicMocks(appointments = listOf(appt))

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.totalSessions == 1 &&
                entry.totalRevenue.compareTo(BigDecimal("80.00")) == 0 &&
                entry.commissionAmount.compareTo(BigDecimal("40.00")) == 0 // 50% commission
            })
        }
    }

    @Test
    fun `CANCELLED_LATE appointments are billable and create pending charge`() = runBlocking {
        val apptId = UUID.randomUUID()
        val appt = TestFixtures.appointment(
            id = apptId,
            status = AppointmentStatus.CANCELLED_LATE,
            price = BigDecimal("80.00")
        )
        setupBasicMocks(appointments = listOf(appt))

        service.calculate(4, 2026)

        // Should count as a session with revenue
        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.totalSessions == 1 &&
                entry.totalRevenue.compareTo(BigDecimal("80.00")) == 0
            })
        }

        // Should create pending charge
        coVerify {
            pendingChargeRepo.create(match { charge ->
                charge.appointmentId == apptId &&
                charge.amount.compareTo(BigDecimal("80.00")) == 0 &&
                charge.status == PendingChargeStatus.PENDING
            })
        }
    }

    @Test
    fun `NO_SHOW appointments are billable and create pending charge`() = runBlocking {
        val apptId = UUID.randomUUID()
        val appt = TestFixtures.appointment(
            id = apptId,
            status = AppointmentStatus.NO_SHOW,
            price = BigDecimal("80.00")
        )
        setupBasicMocks(appointments = listOf(appt))

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { it.totalSessions == 1 })
        }
        coVerify {
            pendingChargeRepo.create(match { it.appointmentId == apptId })
        }
    }

    @Test
    fun `CANCELLED_EARLY appointments are not billable`() = runBlocking {
        val appt = TestFixtures.appointment(status = AppointmentStatus.CANCELLED_EARLY)
        setupBasicMocks(appointments = listOf(appt))

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.totalSessions == 0 &&
                entry.totalRevenue.compareTo(BigDecimal.ZERO) == 0
            })
        }
    }

    @Test
    fun `SCHEDULED appointments are not included in payroll`() = runBlocking {
        val appt = TestFixtures.appointment(status = AppointmentStatus.SCHEDULED)
        setupBasicMocks(appointments = listOf(appt))

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { it.totalSessions == 0 })
        }
    }

    @Test
    fun `supervision sessions skipped when therapist does not receive supervision fee`() = runBlocking {
        val therapist = TestFixtures.therapist(receivesSupervisionFee = false)
        val appt = TestFixtures.appointment(
            sessionType = SessionType.SUPERVISION,
            status = AppointmentStatus.COMPLETED
        )
        setupBasicMocks(
            therapists = listOf(therapist),
            appointments = listOf(appt)
        )

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { it.totalSessions == 0 })
        }
    }

    @Test
    fun `supervision sessions counted when therapist receives supervision fee`() = runBlocking {
        val therapist = TestFixtures.therapist(receivesSupervisionFee = true)
        val appt = TestFixtures.appointment(
            sessionType = SessionType.SUPERVISION,
            status = AppointmentStatus.COMPLETED,
            price = BigDecimal("50.00")
        )
        setupBasicMocks(
            therapists = listOf(therapist),
            appointments = listOf(appt)
        )

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.totalSessions == 1 &&
                entry.totalRevenue.compareTo(BigDecimal("50.00")) == 0
            })
        }
    }

    @Test
    fun `commission is calculated correctly with custom rate`() = runBlocking {
        val therapist = TestFixtures.therapist(commissionRate = BigDecimal("0.40"))
        val appt = TestFixtures.appointment(
            status = AppointmentStatus.COMPLETED,
            price = BigDecimal("100.00")
        )
        setupBasicMocks(
            therapists = listOf(therapist),
            appointments = listOf(appt)
        )

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.commissionAmount.compareTo(BigDecimal("40.00")) == 0
            })
        }
    }

    @Test
    fun `multiple appointments summed correctly`() = runBlocking {
        val appts = listOf(
            TestFixtures.appointment(
                id = UUID.randomUUID(),
                status = AppointmentStatus.COMPLETED,
                price = BigDecimal("80.00")
            ),
            TestFixtures.appointment(
                id = UUID.randomUUID(),
                status = AppointmentStatus.COMPLETED,
                price = BigDecimal("100.00")
            ),
            TestFixtures.appointment(
                id = UUID.randomUUID(),
                status = AppointmentStatus.CANCELLED_EARLY,
                price = BigDecimal("60.00")
            )
        )
        setupBasicMocks(appointments = appts)

        service.calculate(4, 2026)

        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.totalSessions == 2 &&
                entry.totalRevenue.compareTo(BigDecimal("180.00")) == 0 &&
                entry.commissionAmount.compareTo(BigDecimal("90.00")) == 0 // 50%
            })
        }
    }

    @Test
    fun `pending charge not duplicated if already exists for appointment`() = runBlocking {
        val apptId = UUID.randomUUID()
        val appt = TestFixtures.appointment(
            id = apptId,
            status = AppointmentStatus.CANCELLED_LATE
        )
        val existingCharge = PendingCharge(
            id = UUID.randomUUID(),
            clientId = TestFixtures.CLIENT_ID,
            appointmentId = apptId,
            amount = BigDecimal("80.00"),
            reason = "Late cancellation fee",
            status = PendingChargeStatus.PENDING,
            createdAt = TestFixtures.NOW,
            updatedAt = TestFixtures.NOW
        )
        setupBasicMocks(appointments = listOf(appt))
        coEvery { pendingChargeRepo.findByAppointmentId(apptId) } returns existingCharge

        service.calculate(4, 2026)

        coVerify(exactly = 0) { pendingChargeRepo.create(any()) }
    }

    // ── finalize() tests ────────────────────────────────────────────────

    @Test
    fun `finalize marks DRAFT period as FINALIZED`() = runBlocking {
        val period = TestFixtures.payrollPeriod(status = PayrollStatus.DRAFT)
        val finalized = period.copy(status = PayrollStatus.FINALIZED)

        coEvery { payrollRepo.findPeriodById(period.id) } returnsMany listOf(period, finalized)
        coEvery { payrollRepo.finalizePeriod(period.id) } returns Unit

        val result = service.finalize(period.id)

        assertEquals(PayrollStatus.FINALIZED, result.status)
        coVerify { payrollRepo.finalizePeriod(period.id) }
    }

    @Test
    fun `finalize throws for already FINALIZED period`() = runBlocking {
        val period = TestFixtures.payrollPeriod(status = PayrollStatus.FINALIZED)
        coEvery { payrollRepo.findPeriodById(period.id) } returns period

        assertFailsWith<AppError.PayrollAlreadyFinalized> {
            service.finalize(period.id)
        }
    }

    @Test
    fun `finalize throws NotFound for unknown period ID`() = runBlocking {
        val unknownId = UUID.randomUUID()
        coEvery { payrollRepo.findPeriodById(unknownId) } returns null

        assertFailsWith<AppError.NotFound> {
            service.finalize(unknownId)
        }
    }

    @Test
    fun `appointments for different therapists are isolated`() = runBlocking {
        val therapist1 = TestFixtures.therapist(id = TestFixtures.THERAPIST_ID)
        val therapist2 = TestFixtures.therapist(
            id = TestFixtures.THERAPIST_2_ID,
            userId = UUID.randomUUID(),
            commissionRate = BigDecimal("0.60")
        )

        val appt1 = TestFixtures.appointment(
            id = UUID.randomUUID(),
            therapistId = TestFixtures.THERAPIST_ID,
            status = AppointmentStatus.COMPLETED,
            price = BigDecimal("80.00")
        )
        val appt2 = TestFixtures.appointment(
            id = UUID.randomUUID(),
            therapistId = TestFixtures.THERAPIST_2_ID,
            status = AppointmentStatus.COMPLETED,
            price = BigDecimal("100.00")
        )

        setupBasicMocks(
            therapists = listOf(therapist1, therapist2),
            appointments = listOf(appt1, appt2)
        )

        service.calculate(4, 2026)

        // Therapist 1: 80 revenue, 50% commission = 40
        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.therapistId == TestFixtures.THERAPIST_ID &&
                entry.totalSessions == 1 &&
                entry.totalRevenue.compareTo(BigDecimal("80.00")) == 0 &&
                entry.commissionAmount.compareTo(BigDecimal("40.00")) == 0
            })
        }

        // Therapist 2: 100 revenue, 60% commission = 60
        coVerify {
            payrollRepo.createEntry(match { entry ->
                entry.therapistId == TestFixtures.THERAPIST_2_ID &&
                entry.totalSessions == 1 &&
                entry.totalRevenue.compareTo(BigDecimal("100.00")) == 0 &&
                entry.commissionAmount.compareTo(BigDecimal("60.00")) == 0
            })
        }
    }
}
