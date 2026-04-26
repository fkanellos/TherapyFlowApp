package io.therapyflow.data.db

import org.slf4j.LoggerFactory

/**
 * Creates a new tenant schema with all required tables and indexes.
 * Called during workspace registration.
 *
 * Table definitions sourced from:
 *   - docs/multi-tenancy.md
 *   - docs/features/payroll.md   (pending_charges)
 *   - docs/features/calendar-sync.md (unmatched_calendar_events, client_aliases)
 */
class TenantSchemaService {

    private val log = LoggerFactory.getLogger(TenantSchemaService::class.java)

    /**
     * Creates a new schema for the given workspace slug and provisions
     * all tenant-scoped tables and indexes inside it.
     *
     * @param slug workspace slug — schema will be named "tenant_{slug}"
     * @throws IllegalArgumentException if slug contains invalid characters
     */
    suspend fun createSchema(slug: String) {
        require(slug.matches(Regex("^[a-z0-9_]+$"))) {
            "Slug must contain only lowercase letters, digits, and underscores: $slug"
        }

        val schema = "tenant_$slug"
        log.info("Creating tenant schema: {}", schema)

        publicTransaction {
            exec("CREATE SCHEMA IF NOT EXISTS \"$schema\"")

            exec(
                """
                CREATE TABLE "$schema".therapists (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id UUID NOT NULL REFERENCES public.users(id),
                    first_name VARCHAR(100) NOT NULL,
                    last_name VARCHAR(100) NOT NULL,
                    commission_rate DECIMAL(5,2) NOT NULL,
                    receives_supervision_fee BOOLEAN NOT NULL DEFAULT FALSE,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".clients (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    therapist_id UUID NOT NULL REFERENCES "$schema".therapists(id),
                    first_name VARCHAR(100) NOT NULL,
                    last_name VARCHAR(100) NOT NULL,
                    google_calendar_name VARCHAR(200),
                    custom_price DECIMAL(10,2),
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".client_aliases (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    client_id UUID NOT NULL REFERENCES "$schema".clients(id),
                    alias VARCHAR(200) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".appointments (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    therapist_id UUID NOT NULL REFERENCES "$schema".therapists(id),
                    client_id UUID NOT NULL REFERENCES "$schema".clients(id),
                    start_time TIMESTAMPTZ NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    price DECIMAL(10,2) NOT NULL,
                    session_type VARCHAR(50) NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
                    google_calendar_event_id VARCHAR(200),
                    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
                    notes TEXT,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".payroll_periods (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
                    year INTEGER NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    finalized_at TIMESTAMPTZ,
                    UNIQUE (month, year)
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".payroll_entries (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    period_id UUID NOT NULL REFERENCES "$schema".payroll_periods(id),
                    therapist_id UUID NOT NULL REFERENCES "$schema".therapists(id),
                    total_sessions INTEGER NOT NULL DEFAULT 0,
                    total_revenue DECIMAL(10,2) NOT NULL DEFAULT 0,
                    commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                    breakdown JSONB,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".pending_charges (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    client_id UUID NOT NULL REFERENCES "$schema".clients(id),
                    appointment_id UUID NOT NULL REFERENCES "$schema".appointments(id),
                    amount DECIMAL(10,2) NOT NULL,
                    reason VARCHAR(200) NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            exec(
                """
                CREATE TABLE "$schema".unmatched_calendar_events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    google_event_id VARCHAR(200) NOT NULL UNIQUE,
                    raw_title VARCHAR(500) NOT NULL,
                    start_time TIMESTAMPTZ NOT NULL,
                    suggested_client_id UUID,
                    suggestion_score DECIMAL(5,4),
                    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                    assigned_client_id UUID,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.trimIndent()
            )

            // Performance indexes
            exec("""CREATE INDEX ON "$schema".appointments (therapist_id)""")
            exec("""CREATE INDEX ON "$schema".appointments (client_id)""")
            exec("""CREATE INDEX ON "$schema".appointments (start_time)""")
            exec("""CREATE INDEX ON "$schema".appointments (status)""")
            exec("""CREATE INDEX ON "$schema".client_aliases (client_id)""")
            exec("""CREATE INDEX ON "$schema".pending_charges (client_id)""")
            exec("""CREATE INDEX ON "$schema".pending_charges (status)""")
        }

        log.info("Tenant schema created successfully: {}", schema)
    }
}
