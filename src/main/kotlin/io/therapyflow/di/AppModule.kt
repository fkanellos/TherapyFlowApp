package io.therapyflow.di

import io.therapyflow.data.db.TenantSchemaService
import io.therapyflow.data.repository.*
import io.therapyflow.domain.service.EncryptionService
import io.therapyflow.domain.service.FeatureService
import io.therapyflow.domain.service.JwtService
import io.therapyflow.domain.service.PasswordHasher
import io.therapyflow.domain.service.PayrollCalculationService
import org.koin.dsl.module

/**
 * Main Koin DI module.
 * Add sub-modules here as features are implemented.
 *
 * Pattern:
 *   single { }   → one instance for the app lifetime (services, repositories)
 *   factory { }  → new instance each time (use cases)
 */
val appModule = module {

    // Environment config — available to all
    single { AppConfig.load() }

    // ── Multi-tenancy ─────────────────────────────────────────────────
    single { TenantSchemaService() }

    // ── Encryption ──────────────────────────────────────────────────────
    single { EncryptionService(get<AppConfig>().encryptionKey) }

    // ── Auth ──────────────────────────────────────────────────────────
    single { JwtService(get<AppConfig>().jwtSecret, get<AppConfig>().jwtIssuer) }
    single { PasswordHasher() }
    single<UserRepository> { UserRepositoryImpl() }
    single<RefreshTokenRepository> { RefreshTokenRepositoryImpl() }

    // ── Workspace ─────────────────────────────────────────────────────
    single<WorkspaceRepository> { WorkspaceRepositoryImpl() }
    single<FeatureRepository> { FeatureRepositoryImpl() }
    single { FeatureService(get()) }

    // ── Therapist / Client ────────────────────────────────────────────
    single<TherapistRepository> { TherapistRepositoryImpl(get()) }
    single<ClientRepository> { ClientRepositoryImpl(get()) }
    // single<ClientAliasRepository> { ClientAliasRepositoryImpl() }

    // ── Appointments ──────────────────────────────────────────────────
    single<AppointmentRepository> { AppointmentRepositoryImpl(get()) }
    // single { GreekNameMatcher() }

    // ── Payroll ───────────────────────────────────────────────────────
    single<PayrollRepository> { PayrollRepositoryImpl() }
    single<PendingChargeRepository> { PendingChargeRepositoryImpl() }
    single { PayrollCalculationService(get(), get(), get(), get(), get()) }

    // ── Google Calendar ───────────────────────────────────────────────
    // single { GoogleCalendarApi(get()) }
    // single { CalendarSyncService(get(), get(), get(), get()) }

    // ── Export ────────────────────────────────────────────────────────
    // single { PdfExportService() }
    // single { ExcelExportService() }
}

data class AppConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val googleClientId: String,
    val googleClientSecret: String,
    val encryptionKey: String,
) {
    companion object {
        fun load() = AppConfig(
            jwtSecret           = requireEnv("JWT_SECRET"),
            jwtIssuer           = System.getenv("JWT_ISSUER") ?: "therapyflow.io",
            databaseUrl         = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/therapyflow",
            databaseUser        = System.getenv("DATABASE_USER") ?: "therapyflow",
            databasePassword    = System.getenv("DATABASE_PASSWORD") ?: "therapyflow",
            googleClientId      = System.getenv("GOOGLE_CLIENT_ID") ?: "",
            googleClientSecret  = System.getenv("GOOGLE_CLIENT_SECRET") ?: "",
            encryptionKey       = requireEnv("ENCRYPTION_KEY"),
        )

        private fun requireEnv(key: String): String =
            System.getenv(key) ?: error("Required environment variable $key is not set")
    }
}
