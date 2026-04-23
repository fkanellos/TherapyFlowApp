# Payroll Calculation Engine

## Overview

The payroll engine is the core business logic of TherapyFlow.
It was originally developed in the PayrollDesktop app and is **battle-tested in production**.

## Source of Truth: Another Point of View (Production)

This logic comes from real production usage at the psychology practice.
Every edge case below has been observed in real data. Do NOT simplify.

## CRITICAL: Preserve Existing Logic

The calculation logic from PayrollDesktop MUST be migrated as-is.
Do NOT refactor the core math. Only adapt the I/O (database instead of SQLite, API instead of desktop).

## Calculation Rules

### Per-Therapist Calculation
```
totalSessions   = COUNT(appointments WHERE status = COMPLETED AND month = X)
totalRevenue    = SUM(appointments.price WHERE status = COMPLETED AND month = X)
commissionAmount = totalRevenue * therapist.commissionRate
```

### Session Breakdown (per client)
```
For each client of this therapist:
  - clientSessions = COUNT(appointments for this client this month)
  - clientRevenue  = SUM(price for this client this month)
```

### Appointment Status Model

```
AppointmentStatus {
    SCHEDULED       → future appointment, not yet occurred
    COMPLETED       → session happened, billable
    CANCELLED_EARLY → cancelled 2+ days before → NOT billable, no penalty
    CANCELLED_LATE  → cancelled last minute    → billable as LATE_CANCELLATION_PENDING
    NO_SHOW         → client did not appear    → treat as CANCELLED_LATE
}
```

## Edge Cases from Production — ALL MUST BE HANDLED

### 1. Cancellation Policy (Critical)

Two types of cancellations with very different billing behavior:

**Early Cancellation** (2+ days before appointment):
- Client cancelled with enough notice
- Therapist does NOT get paid
- Client does NOT owe anything
- Status: `CANCELLED_EARLY`
- Calendar color in existing system: **not flagged**

**Late Cancellation** (last minute — same day or day before):
- Client cancelled last minute OR did not show up
- Therapist DOES get paid for the lost session
- The fee is charged on the **next visit** (not immediately)
- Status: `CANCELLED_LATE` → creates a `PendingCharge` on the client
- Calendar color in existing system: **RED** = late cancellation, must be paid
- Calendar color: **GREY** = session was paid (either completed or late fee collected)

```kotlin
// Billing rules:
when (appointment.status) {
    COMPLETED          → therapist earns appointment.price
    CANCELLED_EARLY    → therapist earns 0, no client charge
    CANCELLED_LATE     → therapist earns appointment.price
                         client.pendingCharges += appointment.price
                         (collected on next visit)
    NO_SHOW            → same as CANCELLED_LATE
    SCHEDULED          → not included in payroll (future)
}
```

### 2. Pending Charges (Late Cancellation Debt)

When a client has a pending charge from a late cancellation:
- It appears on the next appointment as an additional line
- When collected, it is marked as PAID and removed from pending
- The therapist does NOT get double commission on the pending charge
  (they already earned it when the late cancellation occurred)

```kotlin
data class PendingCharge(
    val clientId: UUID,
    val appointmentId: UUID,   // the cancelled appointment
    val amount: Decimal,
    val reason: String,        // "Late cancellation fee"
    val status: PendingChargeStatus  // PENDING | COLLECTED | WAIVED
)
```

### 3. Per-Therapist Commission Rates

Each therapist has their own commission rate. They are NOT uniform.
The rate is set per therapist in their profile:

```kotlin
data class Therapist(
    ...
    val commissionRate: Decimal,  // e.g. 0.40 = 40%, varies per therapist
    val receivesSupervisionFee: Boolean
)
```

**Important:** Commission rate can change over time.
When calculating historical payroll, use the rate that was active during that month,
NOT the current rate. (Store rate history or snapshot at finalization.)

### 4. Per-Client Pricing

Some clients have custom pricing (not the standard rate).
This is set per client:

```kotlin
data class Client(
    ...
    val customPrice: Decimal?  // if null → use therapist's standard rate
                               // if set → use this price for every session
)
```

Priority: `client.customPrice ?? appointment.price ?? therapist.defaultPrice`

The actual price should be stored on the Appointment at creation time,
not calculated at payroll time. This prevents retroactive pricing issues.

### 5. Supervision Sessions (Εποπτεία)

- Not all therapists receive supervision fees
- Controlled by `therapist.receivesSupervisionFee`
- Supervision sessions have their own price (not the standard session price)
- They ARE included in payroll for eligible therapists
- SessionType: `SUPERVISION`

### 6. Name Matching — Transition Period

**Current problem (Google Calendar):**
The same client can appear with different names in Calendar events:
- "Παναγιώτα" vs "Γιώτα" → no automatic match
- "Αρβανίτης Γ." vs "Γιώργος Αρβανίτης" → no automatic match

**With the new app this is mitigated because:**
- Appointments created via the app always use the correct client record
- Source: `MANUAL` → exact client ID stored, no name matching needed
- Google Calendar sync (`source: GOOGLE_CALENDAR`) still needs matching
  but only for legacy/external events

**Matching strategy:**
- `source = MANUAL` → use `clientId` directly, no matching
- `source = GOOGLE_CALENDAR` → run `GreekNameMatcher`, flag ambiguous results
- Unmatched events → shown in UI for manual assignment
- Once assigned manually → store the mapping to avoid future mismatches

```kotlin
// Nickname/alias table to help Calendar matching
data class ClientAlias(
    val clientId: UUID,
    val alias: String   // e.g. "Γιώτα" → maps to client "Παναγιώτα Κωνσταντίνου"
)
// Once a match is confirmed by user → store alias for next sync
```

## Greek Name Matching Algorithm

**DO NOT REWRITE THIS** — it is the result of many iterations with real Greek data.

```kotlin
object GreekNameMatcher {
    
    fun normalize(name: String): String {
        return name
            .lowercase()
            .replace('ά', 'α').replace('έ', 'ε').replace('ή', 'η')
            .replace('ί', 'ι').replace('ό', 'ο').replace('ύ', 'υ')
            .replace('ώ', 'ω').replace('ϊ', 'ι').replace('ϋ', 'υ')
            .trim()
    }
    
    fun match(calendarName: String, clients: List<Client>): MatchResult {
        val normalized = normalize(calendarName)
        val parts = normalized.split(" ").filter { it.isNotBlank() }
        
        // Try exact match first
        clients.firstOrNull { normalize(it.fullName) == normalized }
            ?.let { return MatchResult.Exact(it) }
        
        // Try reversed name (Greek calendar often has surname first)
        val reversed = parts.reversed().joinToString(" ")
        clients.firstOrNull { normalize(it.fullName) == reversed }
            ?.let { return MatchResult.Reversed(it) }
        
        // Try surname-only match
        if (parts.size >= 2) {
            val surname = parts.first()  // surname is first in Greek format
            val surnameMatches = clients.filter { 
                normalize(it.lastName) == surname 
            }
            if (surnameMatches.size == 1) 
                return MatchResult.SurnameOnly(surnameMatches.first())
        }
        
        // Fuzzy match with confidence score
        val scored = clients.map { client ->
            val score = fuzzyScore(normalized, normalize(client.fullName))
            client to score
        }.sortedByDescending { it.second }
        
        val (best, score) = scored.first()
        return when {
            score >= 0.9 -> MatchResult.HighConfidence(best, score)
            score >= 0.7 -> MatchResult.LowConfidence(best, score)
            else -> MatchResult.NoMatch(calendarName)
        }
    }
    
    sealed class MatchResult {
        data class Exact(val client: Client) : MatchResult()
        data class Reversed(val client: Client) : MatchResult()
        data class SurnameOnly(val client: Client) : MatchResult()
        data class HighConfidence(val client: Client, val score: Double) : MatchResult()
        data class LowConfidence(val client: Client, val score: Double) : MatchResult()
        data class NoMatch(val calendarName: String) : MatchResult()
    }
}
```

## API Endpoints

```
POST /payroll/calculate
Body: { "month": 4, "year": 2026 }
→ Calculates payroll for all therapists, saves as DRAFT

GET /payroll/{periodId}
→ Returns period with all entries and breakdown

PUT /payroll/{periodId}/finalize
→ Changes status to FINALIZED (locked — no more changes)
→ Requires OWNER role

GET /payroll/{periodId}/export?format=pdf|excel
→ Downloads PDF or Excel report
```

## Idempotency

Calculating payroll for the same month twice:
- If period is DRAFT → recalculate and overwrite
- If period is FINALIZED → return 409 Conflict with message

## Testing Requirements

Every calculation change MUST have:
1. Unit test with real Greek name examples from production
2. Integration test with actual DB data
3. Edge case tests: empty month, cancelled sessions, no-shows

See `docs/testing.md` for test examples.
