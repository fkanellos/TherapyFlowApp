# Database Guidelines

## Technology Stack
- **Database:** PostgreSQL 16
- **ORM:** Exposed (Kotlin)
- **Migrations:** Flyway
- **Connection Pool:** HikariCP

## Migration Rules

### NEVER modify existing migrations
Once a migration is applied to any environment, it is immutable.
Changes → new migration file.

### Naming Convention
```
db/migrations/
  V1__create_public_schema.sql
  V2__create_workspaces_table.sql
  V3__create_users_table.sql
  V4__add_workspace_features.sql
  V5__create_tenant_schema_template.sql
  ...
```

Format: `V{number}__{description}.sql` (double underscore)

### Migration File Template
```sql
-- V{N}__{description}.sql
-- Description: What this migration does and why
-- Author: Claude Code
-- Date: YYYY-MM-DD

-- Your SQL here

-- Rollback (for documentation only — Flyway doesn't auto-rollback):
-- DROP TABLE IF EXISTS ...
```

## Public Schema Tables

```sql
-- workspaces (SaaS tenants)
CREATE TABLE public.workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,  -- used as schema name: tenant_{slug}
    plan VARCHAR(50) NOT NULL DEFAULT 'FREE',
    primary_color VARCHAR(7),           -- hex e.g. #1976D2
    secondary_color VARCHAR(7),
    logo_url TEXT,
    google_calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- users
CREATE TABLE public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES public.workspaces(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,          -- OWNER | THERAPIST | ADMIN_STAFF
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ON public.users (workspace_id);
CREATE INDEX ON public.users (email);

-- refresh_tokens
CREATE TABLE public.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- workspace_features (feature flags per workspace)
CREATE TABLE public.workspace_features (
    workspace_id UUID NOT NULL REFERENCES public.workspaces(id),
    feature_key VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN NOT NULL,
    enabled_at TIMESTAMPTZ,
    enabled_by UUID REFERENCES public.users(id),
    PRIMARY KEY (workspace_id, feature_key)
);
```

## Exposed Table Definitions

```kotlin
// Public schema tables
object WorkspaceTable : Table("workspaces") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 200)
    val slug = varchar("slug", 100)
    val plan = enumerationByName("plan", 50, Plan::class)
    val status = enumerationByName("status", 50, WorkspaceStatus::class)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// Tenant schema tables (schema set dynamically via search_path)
object AppointmentTable : Table("appointments") {
    val id = uuid("id").autoGenerate()
    val therapistId = uuid("therapist_id")
    val clientId = uuid("client_id")
    val startTime = timestamp("start_time")
    val durationMinutes = integer("duration_minutes")
    val price = decimal("price", 10, 2)
    val sessionType = enumerationByName("session_type", 50, SessionType::class)
    val status = enumerationByName("status", 50, AppointmentStatus::class)
    val googleCalendarEventId = varchar("google_calendar_event_id", 200).nullable()
    val source = enumerationByName("source", 50, AppointmentSource::class)
    val notes = text("notes").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}
```

## Data Conventions

### UUIDs everywhere
- Use UUID for all primary keys
- Use `gen_random_uuid()` as default
- Reason: safe in URLs, works offline, non-sequential

### Soft Deletes
- Never hard delete business data
- Use `is_active BOOLEAN NOT NULL DEFAULT TRUE`
- Filter: `WHERE is_active = TRUE` in all queries

### Timestamps
- All tables: `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`
- Use `TIMESTAMPTZ` (with timezone) — store in UTC
- Update `updated_at` in application code before every UPDATE

### Audit Trail
For sensitive changes (payroll finalization, user changes):
```sql
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,     -- e.g. "PAYROLL_FINALIZED"
    entity_type VARCHAR(100) NOT NULL, -- e.g. "PayrollPeriod"
    entity_id UUID NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## Performance Checklist

- [ ] Index on every foreign key column
- [ ] Index on `start_time` for appointment date-range queries
- [ ] Index on `status` for filtered list queries
- [ ] EXPLAIN ANALYZE any query that returns > 100 rows
- [ ] Never SELECT * — always specify columns
