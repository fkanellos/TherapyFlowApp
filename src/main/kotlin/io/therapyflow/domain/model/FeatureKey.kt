package io.therapyflow.domain.model

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

/** Default features provisioned per plan at workspace creation. */
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
