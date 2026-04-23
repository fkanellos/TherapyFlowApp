# Feature Flags — Implementation Guide

## Overview

Feature flags control which features are available per workspace,
based on their subscription plan. This is the mechanism that enforces
plan limits and allows gradual rollout of new features.

## Plans & Features Matrix

```
FREE    → Google Calendar sync (read-only), basic payroll, 1 therapist
STARTER → up to 5 therapists, PDF export, Excel export
PRO     → unlimited therapists, analytics, Google Sheets export, custom branding
ENTERPRISE → white-label, API access, priority support
```

## Feature Keys (constants)

```kotlin
// domain/model/Feature.kt
object FeatureKey {
    const val GOOGLE_CALENDAR_SYNC = "google_calendar_sync"
    const val PDF_EXPORT           = "pdf_export"
    const val EXCEL_EXPORT         = "excel_export"
    const val ANALYTICS_DASHBOARD  = "analytics_dashboard"
    const val GOOGLE_SHEETS_EXPORT = "google_sheets_export"
    const val CUSTOM_BRANDING      = "custom_branding"
    const val MULTI_THERAPIST      = "multi_therapist"
    const val API_ACCESS           = "api_access"
}

// Default features per plan — applied at workspace creation
val PLAN_DEFAULTS: Map<Plan, Set<String>> = mapOf(
    Plan.FREE to setOf(
        FeatureKey.GOOGLE_CALENDAR_SYNC
    ),
    Plan.STARTER to setOf(
        FeatureKey.GOOGLE_CALENDAR_SYNC,
        FeatureKey.PDF_EXPORT,
        FeatureKey.EXCEL_EXPORT,
        FeatureKey.MULTI_THERAPIST
    ),
    Plan.PRO to setOf(
        FeatureKey.GOOGLE_CALENDAR_SYNC,
        FeatureKey.PDF_EXPORT,
        FeatureKey.EXCEL_EXPORT,
        FeatureKey.MULTI_THERAPIST,
        FeatureKey.ANALYTICS_DASHBOARD,
        FeatureKey.GOOGLE_SHEETS_EXPORT,
        FeatureKey.CUSTOM_BRANDING
    ),
    Plan.ENTERPRISE to setOf(
        // All features
        FeatureKey.GOOGLE_CALENDAR_SYNC,
        FeatureKey.PDF_EXPORT,
        FeatureKey.EXCEL_EXPORT,
        FeatureKey.MULTI_THERAPIST,
        FeatureKey.ANALYTICS_DASHBOARD,
        FeatureKey.GOOGLE_SHEETS_EXPORT,
        FeatureKey.CUSTOM_BRANDING,
        FeatureKey.API_ACCESS
    )
)
```

## Checking Features in Code

```kotlin
// domain/service/FeatureService.kt
class FeatureService(private val featureRepository: FeatureRepository) {

    suspend fun isEnabled(workspaceId: UUID, featureKey: String): Boolean {
        return featureRepository.findByWorkspaceAndKey(workspaceId, featureKey)
            ?.isEnabled ?: false
    }

    suspend fun requireFeature(workspaceId: UUID, featureKey: String) {
        if (!isEnabled(workspaceId, featureKey)) {
            throw FeatureNotEnabledException(featureKey)
        }
    }
}

// In route handlers:
post("/v1/payroll/{id}/export") {
    val format = call.parameters["format"] ?: "pdf"
    val workspaceId = TenantContext.current().workspaceId

    when (format) {
        "pdf"   -> featureService.requireFeature(workspaceId, FeatureKey.PDF_EXPORT)
        "excel" -> featureService.requireFeature(workspaceId, FeatureKey.EXCEL_EXPORT)
    }
    // ... proceed with export
}
```

## Feature Gate Ktor Plugin (Route-level)

```kotlin
fun Route.requireFeature(featureKey: String, build: Route.() -> Unit): Route {
    return createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }).also { child ->
        child.intercept(ApplicationCallPipeline.Plugins) {
            val workspaceId = TenantContext.current().workspaceId
            if (!featureService.isEnabled(workspaceId, featureKey)) {
                call.respond(HttpStatusCode.Forbidden, mapOf(
                    "error" to "FEATURE_NOT_ENABLED",
                    "message" to "Your plan does not include: $featureKey",
                    "upgradeUrl" to "https://therapyflow.io/pricing"
                ))
                return@intercept finish()
            }
        }
        build(child)
    }
}

// Usage in routes:
authenticate("jwt") {
    requireFeature(FeatureKey.ANALYTICS_DASHBOARD) {
        get("/v1/analytics/revenue") { ... }
        get("/v1/analytics/therapists") { ... }
    }

    requireFeature(FeatureKey.GOOGLE_SHEETS_EXPORT) {
        post("/v1/payroll/{id}/export/sheets") { ... }
    }
}
```

## DB Table (already in database.md public schema)

```sql
-- From database.md — workspace_features
SELECT * FROM public.workspace_features
WHERE workspace_id = $1 AND feature_key = $2;
```

## Provisioning Features at Workspace Creation

```kotlin
// Called when a new workspace registers
suspend fun provisionWorkspace(workspace: Workspace) {
    val features = PLAN_DEFAULTS[workspace.plan] ?: emptySet()
    features.forEach { featureKey ->
        featureRepository.enable(
            workspaceId = workspace.id,
            featureKey = featureKey,
            enabledBy = null  // system-provisioned
        )
    }
}
```

## Admin Override (for sales/support)

```kotlin
// OWNER can see their features, SUPER_ADMIN (internal) can override
// Add a hidden /admin endpoint (protected by separate admin token)
post("/internal/workspaces/{id}/features/{key}/enable") {
    featureRepository.enable(workspaceId, featureKey, enabledBy = adminUserId)
}
```

## Plan Upgrade Flow

When workspace upgrades plan:
1. Update `workspaces.plan`
2. Enable all features for new plan
3. Do NOT disable old features (downgrade is separate flow — 30-day grace period)

```kotlin
suspend fun upgradePlan(workspaceId: UUID, newPlan: Plan) {
    workspaceRepository.updatePlan(workspaceId, newPlan)
    val newFeatures = PLAN_DEFAULTS[newPlan] ?: emptySet()
    newFeatures.forEach { featureKey ->
        featureRepository.enable(workspaceId, featureKey, enabledBy = null)
    }
}
```

## Caching

Feature checks happen on every request. Cache per workspace with 5-min TTL:

```kotlin
// Simple in-memory cache — good enough for MVP
private val cache = ConcurrentHashMap<String, Pair<Boolean, Instant>>()

fun getCached(workspaceId: UUID, featureKey: String): Boolean? {
    val key = "$workspaceId:$featureKey"
    val (value, expiresAt) = cache[key] ?: return null
    return if (expiresAt.isAfter(Instant.now())) value else null
}
```
