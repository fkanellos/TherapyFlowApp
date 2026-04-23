# TherapyFlow — Claude Code Instructions

## Project Overview
TherapyFlow is a multi-tenant SaaS platform for psychology practices.
- **Backend:** Kotlin + Ktor (this repo: `PayrollApp`)
- **Mobile:** Kotlin Multiplatform + Compose Multiplatform (`PayrollDesktop` → future `TherapyFlowMobile`)
- **Database:** PostgreSQL with schema-per-tenant isolation
- **Auth:** JWT (access 15min + refresh 30 days)

## Critical Rules — Read Before ANY Change

1. **NEVER write code without reading the relevant doc in `/docs/`**
2. **NEVER hard-code secrets** — use environment variables only
3. **NEVER hard-delete data** — always soft delete (`isActive = false`)
4. **NEVER break multi-tenancy** — every DB query MUST be tenant-scoped
5. **ALWAYS write tests** for domain logic and API endpoints
6. **ALWAYS run `./gradlew test` before considering a task done**

## Project Structure

```
PayrollApp/
├── CLAUDE.md                    ← you are here — READ THIS FIRST
├── build.gradle.kts             ← Gradle build file (Ktor 3.x + Kotlin 2.x)
├── gradle/
│   └── libs.versions.toml      ← version catalog (all dependency versions)
├── docs/
│   ├── architecture.md          ← system design & all major decisions
│   ├── database.md              ← schema, migrations, Exposed conventions
│   ├── api-guidelines.md        ← REST conventions, auth, error format
│   ├── multi-tenancy.md         ← tenant isolation — READ BEFORE ANY DB WORK
│   ├── testing.md               ← testing strategy & examples
│   ├── deployment.md            ← Railway (dev) → Hetzner (prod)
│   ├── web-admin.md             ← Web dashboard spec (HTML/CSS/Vanilla JS)
│   ├── android/                 ← Mobile — based on Google MAD Skills
│   │   ├── architecture.md      ← MVI pattern, UDF, use cases, Koin DI
│   │   ├── compose-guidelines.md ← state hoisting, stability, design system
│   │   └── kotlin-style.md      ← coroutines, Flow, naming conventions
│   └── features/
│       ├── auth.md              ← JWT auth, login flow, role guards
│       ├── payroll.md           ← payroll engine — BATTLE-TESTED, do not simplify
│       ├── calendar-sync.md     ← Google Calendar OAuth + sync logic
│       └── feature-flags.md    ← plan-based feature gating
├── src/
│   ├── main/kotlin/
│   │   ├── Application.kt
│   │   ├── domain/              ← entities, interfaces, use cases
│   │   ├── data/                ← repositories, DB, external APIs
│   │   ├── api/                 ← routes, controllers, DTOs
│   │   ├── plugins/             ← Ktor plugins (auth, serialization)
│   │   └── di/                  ← Koin modules
│   └── test/kotlin/
│       ├── domain/
│       ├── data/
│       └── api/
├── db/
│   └── migrations/              ← Flyway SQL migrations
└── build.gradle.kts
```

## Tech Stack

```
Language:     Kotlin 2.x
Framework:    Ktor 3.x
DI:           Koin
Database:     PostgreSQL + Exposed ORM
Migrations:   Flyway
Auth:         JWT (kotlin-jwt)
Serialization: kotlinx.serialization
Testing:      JUnit5 + Ktor testApplication
Build:        Gradle (Kotlin DSL)
```

## Clients

| Client | Tech | Users | Purpose |
|--------|------|-------|---------|
| Mobile (Android + iOS) | Kotlin Multiplatform + Compose | Therapists + Admin (on the go) | Appointments, earnings |
| Web Admin Dashboard | HTML + CSS + Vanilla JS | Owner/Admin only | Full management, analytics, payroll |

Both call the **same Ktor REST API** — no separate backend per client.

## Quick Reference

### Adding a new endpoint
1. Read `docs/api-guidelines.md`
2. Define DTO in `api/dto/`
3. Define route in `api/routes/`
4. Implement service in `domain/`
5. Implement repository in `data/`
6. Write test in `test/api/`

### Adding a new DB table
1. Read `docs/database.md`
2. Create migration in `db/migrations/V{N}__description.sql`
3. Define Exposed table in `data/tables/`
4. Define entity in `domain/model/`
5. NEVER modify existing migrations

### Common Commands
```bash
./gradlew run              # run locally
./gradlew test             # run all tests
./gradlew test --tests "com.therapyflow.api.*"  # run specific tests
./gradlew flywayMigrate    # apply DB migrations
```

## Environment Variables Required
```
DATABASE_URL=jdbc:postgresql://...
DATABASE_USER=...
DATABASE_PASSWORD=...
JWT_SECRET=...
JWT_ISSUER=therapyflow.io
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```
