package io.therapyflow.fixtures

import io.therapyflow.domain.model.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Reusable test data. Stable UUIDs so assertions are deterministic.
 */
object TestFixtures {

    // ── IDs ─────────────────────────────────────────────────────────────
    val WORKSPACE_ID: UUID      = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val OWNER_USER_ID: UUID     = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val THERAPIST_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val THERAPIST_ID: UUID      = UUID.fromString("00000000-0000-0000-0000-000000000004")
    val THERAPIST_2_ID: UUID    = UUID.fromString("00000000-0000-0000-0000-000000000005")
    val CLIENT_ID: UUID         = UUID.fromString("00000000-0000-0000-0000-000000000006")
    val CLIENT_2_ID: UUID       = UUID.fromString("00000000-0000-0000-0000-000000000007")
    val APPOINTMENT_ID: UUID    = UUID.fromString("00000000-0000-0000-0000-000000000008")
    val PERIOD_ID: UUID         = UUID.fromString("00000000-0000-0000-0000-000000000009")

    val NOW: Instant = Instant.parse("2026-04-01T10:00:00Z")

    // ── Workspace ───────────────────────────────────────────────────────
    fun workspace(
        id: UUID = WORKSPACE_ID,
        plan: Plan = Plan.PRO
    ) = Workspace(
        id = id,
        name = "Test Workspace",
        slug = "test_ws",
        plan = plan,
        status = WorkspaceStatus.ACTIVE,
        createdAt = NOW,
        updatedAt = NOW
    )

    // ── Users ───────────────────────────────────────────────────────────
    fun ownerUser(
        id: UUID = OWNER_USER_ID,
        workspaceId: UUID = WORKSPACE_ID,
        email: String = "owner@test.com",
        hashedPassword: String = "hashed_password"
    ) = User(
        id = id,
        workspaceId = workspaceId,
        email = email,
        hashedPassword = hashedPassword,
        role = UserRole.OWNER,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW
    )

    fun therapistUser(
        id: UUID = THERAPIST_USER_ID,
        workspaceId: UUID = WORKSPACE_ID
    ) = User(
        id = id,
        workspaceId = workspaceId,
        email = "therapist@test.com",
        hashedPassword = "hashed_password",
        role = UserRole.THERAPIST,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW
    )

    // ── Therapists ──────────────────────────────────────────────────────
    fun therapist(
        id: UUID = THERAPIST_ID,
        userId: UUID = THERAPIST_USER_ID,
        commissionRate: BigDecimal = BigDecimal("0.50"),
        receivesSupervisionFee: Boolean = false
    ) = Therapist(
        id = id,
        userId = userId,
        firstName = "Maria",
        lastName = "Papadopoulou",
        commissionRate = commissionRate,
        receivesSupervisionFee = receivesSupervisionFee,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW
    )

    // ── Clients ─────────────────────────────────────────────────────────
    fun client(
        id: UUID = CLIENT_ID,
        therapistId: UUID = THERAPIST_ID
    ) = Client(
        id = id,
        therapistId = therapistId,
        firstName = "Giorgos",
        lastName = "Arvanitis",
        googleCalendarName = null,
        customPrice = null,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW
    )

    // ── Appointments ────────────────────────────────────────────────────
    fun appointment(
        id: UUID = APPOINTMENT_ID,
        therapistId: UUID = THERAPIST_ID,
        clientId: UUID = CLIENT_ID,
        status: AppointmentStatus = AppointmentStatus.COMPLETED,
        sessionType: SessionType = SessionType.INDIVIDUAL,
        price: BigDecimal = BigDecimal("80.00"),
        startTime: Instant = Instant.parse("2026-04-10T10:00:00Z")
    ) = Appointment(
        id = id,
        therapistId = therapistId,
        clientId = clientId,
        startTime = startTime,
        durationMinutes = 60,
        price = price,
        sessionType = sessionType,
        status = status,
        googleCalendarEventId = null,
        source = AppointmentSource.MANUAL,
        notes = null,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW
    )

    // ── Payroll ─────────────────────────────────────────────────────────
    fun payrollPeriod(
        id: UUID = PERIOD_ID,
        month: Int = 4,
        year: Int = 2026,
        status: PayrollStatus = PayrollStatus.DRAFT
    ) = PayrollPeriod(
        id = id,
        month = month,
        year = year,
        status = status,
        createdAt = NOW,
        finalizedAt = null
    )
}
