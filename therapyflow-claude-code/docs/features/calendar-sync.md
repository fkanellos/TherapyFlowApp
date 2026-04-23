# Google Calendar Sync — Implementation Guide

## Overview

TherapyFlow syncs appointments from Google Calendar for workspaces
that have enabled the integration. This is the **legacy input method** —
new appointments should be created via the app directly.

The sync logic is **battle-tested in the existing PayrollApp (Spring Boot)**.
When migrating to Ktor, preserve the core matching logic exactly.
Only change the I/O layer (OAuth flow, DB writes).

## Architecture

```
Google Calendar API
        ↓
CalendarSyncService       ← orchestrates the sync
        ↓
GreekNameMatcher          ← matches event titles to Client records
        ↓
AppointmentRepository     ← upsert into tenant schema
        ↓
UnmatchedEventRepository  ← stores events that could not be matched
```

## OAuth2 Setup

TherapyFlow uses OAuth2 with offline access (refresh token) so the
server can sync calendars without the user being present.

```kotlin
// The OAuth consent is done ONCE per workspace by the OWNER
// Tokens are stored per workspace in DB (not in files like the old app)

data class WorkspaceGoogleTokens(
    val workspaceId: UUID,
    val accessToken: String,        // encrypted at rest
    val refreshToken: String,       // encrypted at rest
    val expiresAt: Instant,
    val calendarIds: List<String>   // which calendars to sync
)
```

### OAuth Flow (Web Admin)

```
1. Owner clicks "Connect Google Calendar" in Settings
2. Web admin redirects to: GET /v1/integrations/google/auth
3. Backend redirects to Google consent screen
4. Google redirects back to: GET /v1/integrations/google/callback?code=...
5. Backend exchanges code for tokens, stores encrypted in DB
6. Owner is redirected back to Settings — "Connected ✓"
```

```kotlin
// api/routes/IntegrationRoutes.kt
get("/v1/integrations/google/auth") {
    authenticate("jwt") {
        requireRole(UserRole.OWNER) {
            val authUrl = googleOAuthService.buildAuthUrl(
                workspaceId = TenantContext.current().workspaceId,
                scopes = listOf(
                    "https://www.googleapis.com/auth/calendar.readonly",
                    "https://www.googleapis.com/auth/calendar.events.readonly"
                )
            )
            call.respondRedirect(authUrl)
        }
    }
}

get("/v1/integrations/google/callback") {
    val code = call.request.queryParameters["code"]
        ?: return@get call.respond(HttpStatusCode.BadRequest)
    val state = call.request.queryParameters["state"]  // contains workspaceId

    val tokens = googleOAuthService.exchangeCode(code)
    googleTokenRepository.save(state.workspaceId, tokens)

    call.respondRedirect("https://admin.therapyflow.io/settings?connected=true")
}
```

## Sync Trigger

Sync runs in two ways:
1. **Scheduled:** Every 6 hours via Ktor background job
2. **Manual:** POST /v1/integrations/google/sync (Owner only)

```kotlin
// Scheduled sync — add to Application.kt
fun Application.scheduleCalendarSync() {
    launch(Dispatchers.IO) {
        while (true) {
            delay(6.hours)
            calendarSyncService.syncAllWorkspaces()
        }
    }
}
```

## Sync Logic

```kotlin
// domain/service/CalendarSyncService.kt
class CalendarSyncService(
    private val googleCalendarApi: GoogleCalendarApi,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val unmatchedRepository: UnmatchedEventRepository,
    private val nameMatcher: GreekNameMatcher
) {

    suspend fun syncWorkspace(workspaceId: UUID, schema: String) {
        val tokens = googleTokenRepository.find(workspaceId) ?: return
        val calendarIds = tokens.calendarIds

        for (calendarId in calendarIds) {
            val events = googleCalendarApi.getEvents(
                calendarId = calendarId,
                accessToken = tokens.accessToken,
                // Sync last 2 months + next 1 month
                timeMin = Instant.now().minus(60, ChronoUnit.DAYS),
                timeMax = Instant.now().plus(30, ChronoUnit.DAYS)
            )

            for (event in events) {
                processEvent(event, workspaceId, schema)
            }
        }
    }

    private suspend fun processEvent(
        event: GoogleCalendarEvent,
        workspaceId: UUID,
        schema: String
    ) {
        // Skip events with no title (blocked time, etc.)
        if (event.summary.isNullOrBlank()) return

        // Determine appointment status from event color
        val status = mapColorToStatus(event.colorId)

        // Try to find matching client
        val clients = clientRepository.findAll(schema)
        val matchResult = nameMatcher.match(event.summary, clients)

        when (matchResult) {
            is MatchResult.Exact,
            is MatchResult.Reversed,
            is MatchResult.SurnameOnly,
            is MatchResult.HighConfidence -> {
                val client = matchResult.client
                // Upsert — same googleCalendarEventId → update, new → insert
                appointmentRepository.upsertFromCalendar(
                    schema = schema,
                    googleEventId = event.id,
                    clientId = client.id,
                    therapistId = client.primaryTherapistId,
                    startTime = event.start.toInstant(),
                    durationMinutes = event.durationMinutes(),
                    price = client.customPrice ?: client.primaryTherapist.defaultPrice,
                    status = status,
                    source = AppointmentSource.GOOGLE_CALENDAR
                )
            }

            is MatchResult.LowConfidence,
            is MatchResult.NoMatch -> {
                // Store as unmatched — show in UI for manual assignment
                unmatchedRepository.upsert(
                    schema = schema,
                    googleEventId = event.id,
                    rawTitle = event.summary,
                    startTime = event.start.toInstant(),
                    suggestion = (matchResult as? MatchResult.LowConfidence)?.client
                )
            }
        }
    }
}
```

## Color → Status Mapping

The existing PayrollApp uses Google Calendar event colors to determine
appointment status. **Preserve this mapping exactly.**

```kotlin
// This mapping comes from real production usage — do NOT change
fun mapColorToStatus(colorId: String?): AppointmentStatus = when (colorId) {
    "11" -> AppointmentStatus.CANCELLED_LATE    // RED (Tomato)
    "8"  -> AppointmentStatus.COMPLETED         // GREY (Graphite) = paid/done
    null -> AppointmentStatus.SCHEDULED         // Default/no color
    else -> AppointmentStatus.SCHEDULED
}
```

## Unmatched Events — Manual Assignment UI

When sync finds events that cannot be matched to a client,
they appear in the Web Admin under Appointments → "Needs Review":

```
Event: "Γ. Κωνσταντίνου" — 2026-04-22 at 10:00
Suggestion: Γιώτα Κωνσταντίνου (75% match)
[ Confirm match ] [ Pick different client ] [ Ignore ]
```

When user confirms → store alias:
```kotlin
clientAliasRepository.save(ClientAlias(
    clientId = confirmedClient.id,
    alias = "Γ. Κωνσταντίνου"  // future events with this name auto-match
))
```

## API Endpoints

```
POST /v1/integrations/google/sync          → manual sync trigger (OWNER)
GET  /v1/integrations/google/status        → connection status, last sync time
DELETE /v1/integrations/google             → disconnect (revoke tokens)
GET  /v1/appointments/unmatched            → list events needing manual review
POST /v1/appointments/unmatched/{id}/assign → assign unmatched event to client
```

## Token Refresh

Google access tokens expire in 1 hour. Before each API call, check and refresh:

```kotlin
suspend fun getValidAccessToken(workspaceId: UUID): String {
    val stored = googleTokenRepository.find(workspaceId)!!
    if (stored.expiresAt.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
        return stored.accessToken  // still valid
    }
    // Refresh
    val newTokens = googleOAuthService.refreshToken(stored.refreshToken)
    googleTokenRepository.update(workspaceId, newTokens)
    return newTokens.accessToken
}
```

## DB Table (public schema)

```sql
CREATE TABLE public.workspace_google_tokens (
    workspace_id UUID PRIMARY KEY REFERENCES public.workspaces(id),
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    calendar_ids TEXT[] NOT NULL DEFAULT '{}',
    last_sync_at TIMESTAMPTZ,
    last_sync_status VARCHAR(50),   -- SUCCESS | PARTIAL | FAILED
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-tenant: unmatched events
CREATE TABLE tenant_{slug}.unmatched_calendar_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_event_id VARCHAR(200) NOT NULL UNIQUE,
    raw_title VARCHAR(500) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    suggested_client_id UUID,       -- nullable — best guess from matcher
    suggestion_score DECIMAL(5,4),  -- e.g. 0.75
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING | ASSIGNED | IGNORED
    assigned_client_id UUID,        -- set when user manually assigns
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## Important: Proxy Configuration

The existing PayrollApp had issues with SOCKS proxy interfering with
Google API calls. When deploying, ensure:

```
JVM flag: -Djava.net.useSystemProxies=false
```

Or in Ktor's Ktor client config:
```kotlin
val client = HttpClient(CIO) {
    engine {
        proxy = ProxyBuilder.http("") // explicit no-proxy
    }
}
```
