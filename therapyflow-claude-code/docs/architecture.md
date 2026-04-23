# Architecture — TherapyFlow Backend

## Pattern: Clean Architecture + Layered

```
api/          → HTTP layer (routes, DTOs, controllers)
domain/       → Business logic (use cases, entities, interfaces)
data/         → Data layer (repositories, DB, external APIs)
plugins/      → Ktor infrastructure (auth, CORS, serialization)
di/           → Dependency injection (Koin modules)
```

**Rule:** Dependencies flow inward only.
- `api` depends on `domain`
- `data` depends on `domain`
- `domain` depends on NOTHING external

## Client Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Mobile App (Android + iOS)                             │
│  Kotlin Multiplatform + Compose Multiplatform           │
│                                                         │
│  THERAPIST role:                                        │
│    → Βλέπει μόνο τα δικά της ραντεβού                  │
│    → Προσθέτει/ακυρώνει ραντεβού                       │
│    → Βλέπει τα earnings της                             │
│                                                         │
│  ADMIN role (on the go):                                │
│    → Overview dashboard (όλοι οι θεραπευτές)           │
│    → Γρήγορη επισκόπηση ραντεβού                       │
│    → Approve/reject αλλαγές                            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Web Admin Dashboard (browser — OWNER only)             │
│  HTML + CSS + Vanilla JavaScript (Fetch API)            │
│                                                         │
│    → Employee management (CRUD εργαζομένων)             │
│    → Client management (όλοι οι πελάτες)               │
│    → Full calendar view όλων των θεραπευτών             │
│    → Payroll calculation + finalization                 │
│    → Analytics + statistics + charts                   │
│    → Data export (PDF, Excel)                           │
│    → Workspace settings & brand config                 │
│    → Feature flags management                          │
│                                                         │
│  Accessible από παντού — browser only, no install       │
│  Same Ktor API as mobile — no separate backend          │
└─────────────────────────────────────────────────────────┘

Both clients → Same Ktor REST API → PostgreSQL
```

**Why Vanilla JS (not React) for Web:**
- Filippos knows HTML/CSS/JS from master's degree
- Can read, review and guide Claude Code on the frontend
- Admin dashboard = tables, charts, forms — no complex UI needed
- No framework dependency, simpler deployment
- AJAX patterns from master's degree apply directly

## Multi-Tenancy: Schema-per-Tenant

Every workspace (practice) has its own PostgreSQL schema:
```
public.workspaces      → SaaS layer (all tenants)
public.users           → all users
tenant_apov.*          → Another Point of View data
tenant_xyz.*           → other workspace data
```

**TenantContext** is injected per-request via Ktor pipeline:
```kotlin
// Every DB query automatically uses the tenant schema
val schema = TenantContext.current().schema  // e.g. "tenant_apov"
```

Read `docs/multi-tenancy.md` for implementation details.

## Key Design Decisions

### Why Ktor over Spring Boot?
- Lightweight, coroutine-native
- Better KMP alignment (shared Ktor client on mobile)
- No reflection magic — explicit configuration

### Why Exposed over Hibernate?
- Type-safe SQL in Kotlin
- Works well with Ktor coroutines
- No proxy objects or lazy-loading surprises

### Why Schema-per-Tenant over Row-level?
- Physical data isolation (GDPR requirement for medical data)
- Easy per-tenant backup/restore
- No risk of missing a `WHERE workspace_id=?`

### Why PostgreSQL over MongoDB?
- Payroll requires ACID transactions
- Complex JOIN queries across therapist/appointments/payroll
- Schema validation ensures data integrity

## Domain Model Summary

```
Workspace       → SaaS tenant (one per psychology practice)
User            → Person with login (OWNER | THERAPIST | ADMIN_STAFF)
Therapist       → Employee profile linked to User
Client          → Patient of a therapist
Appointment     → Session (manual or from Google Calendar)
PayrollPeriod   → Monthly payroll run (DRAFT | FINALIZED | PAID)
PayrollEntry    → Per-therapist breakdown within a period
Feature         → Feature flag (linked to Plan)
```

## Error Handling Strategy

All errors return consistent JSON:
```json
{
  "error": "APPOINTMENT_NOT_FOUND",
  "message": "Appointment with id X not found",
  "status": 404
}
```

Use sealed class `AppError` in domain layer.
Never expose stack traces in production responses.

## Logging Strategy

- Use `slf4j` + structured logging
- Log: request id, workspace id, user id (never log passwords/tokens)
- Log level: ERROR for exceptions, INFO for business events, DEBUG for dev

## Performance Notes

- All DB queries must use indexes on `workspace_id` foreign keys
- Avoid N+1 queries — use Exposed `leftJoin` or batch loading
- Payroll calculation is CPU-heavy → run in `Dispatchers.Default`
- Google Calendar sync is I/O-heavy → run in `Dispatchers.IO`
