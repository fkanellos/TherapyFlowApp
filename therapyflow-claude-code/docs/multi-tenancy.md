# Multi-Tenancy — Implementation Guide

## CRITICAL: Read this before any DB work

Every database operation MUST be scoped to a tenant schema.
Missing tenant scope = data leak between workspaces = catastrophic bug.

## How TenantContext Works

```kotlin
// Set per-request in authentication plugin
class TenantContext(val workspaceId: UUID, val schema: String) {
    companion object {
        private val local = ThreadLocal<TenantContext>()
        fun set(ctx: TenantContext) = local.set(ctx)
        fun current(): TenantContext = local.get() 
            ?: error("No tenant context — missing auth?")
        fun clear() = local.remove()
    }
}

// In JWT validation plugin:
validate { credential ->
    val workspaceId = credential.payload.getClaim("workspaceId").asString()
    val slug = workspaceService.getSlug(workspaceId)
    TenantContext.set(TenantContext(
        workspaceId = UUID.fromString(workspaceId),
        schema = "tenant_$slug"
    ))
    JWTPrincipal(credential.payload)
}
```

## How to Write Tenant-Safe Queries

### ✅ CORRECT — Always qualify table with schema
```kotlin
fun getAppointments(): List<Appointment> {
    val schema = TenantContext.current().schema
    return transaction {
        exec("SET search_path TO $schema, public")
        AppointmentTable.selectAll().map { it.toAppointment() }
    }
}
```

### ✅ CORRECT — Using schema prefix
```kotlin
object AppointmentTable : Table("appointments") {
    // Table name is relative — schema is set via search_path
    val id = uuid("id").autoGenerate()
    val therapistId = uuid("therapist_id")
    val clientId = uuid("client_id")
    val startTime = timestamp("start_time")
    val price = decimal("price", 10, 2)
    val status = enumerationByName("status", 32, AppointmentStatus::class)
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}
```

### ❌ WRONG — Never use public schema for tenant data
```kotlin
// NEVER DO THIS
val appointments = transaction {
    AppointmentTable.selectAll()  // Which workspace?! Bug!
}
```

## Creating a New Tenant Schema

When a new workspace registers, create their schema:
```sql
-- V_tenant_template.sql (applied per new workspace)
CREATE SCHEMA IF NOT EXISTS tenant_{slug};

CREATE TABLE tenant_{slug}.therapists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    commission_rate DECIMAL(5,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_{slug}.clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    therapist_id UUID NOT NULL REFERENCES tenant_{slug}.therapists(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    google_calendar_name VARCHAR(200),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_{slug}.appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    therapist_id UUID NOT NULL REFERENCES tenant_{slug}.therapists(id),
    client_id UUID NOT NULL REFERENCES tenant_{slug}.clients(id),
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
);

CREATE TABLE tenant_{slug}.payroll_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    year INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalized_at TIMESTAMPTZ,
    UNIQUE (month, year)
);

CREATE TABLE tenant_{slug}.payroll_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_id UUID NOT NULL REFERENCES tenant_{slug}.payroll_periods(id),
    therapist_id UUID NOT NULL REFERENCES tenant_{slug}.therapists(id),
    total_sessions INTEGER NOT NULL DEFAULT 0,
    total_revenue DECIMAL(10,2) NOT NULL DEFAULT 0,
    commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    breakdown JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX ON tenant_{slug}.appointments (therapist_id);
CREATE INDEX ON tenant_{slug}.appointments (client_id);
CREATE INDEX ON tenant_{slug}.appointments (start_time);
CREATE INDEX ON tenant_{slug}.appointments (status);
```

## Checklist for New Features

- [ ] Does every repository method call `TenantContext.current()`?
- [ ] Does every test set up a tenant context before running?
- [ ] Are all foreign keys within the same schema?
- [ ] Is `search_path` set before every transaction?
