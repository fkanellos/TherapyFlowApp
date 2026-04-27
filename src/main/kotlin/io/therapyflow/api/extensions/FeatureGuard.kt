package io.therapyflow.api.extensions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.domain.service.FeatureService
import io.therapyflow.domain.tenant.TenantContext
import org.koin.ktor.ext.inject

/**
 * Route guard that restricts access based on workspace feature flags.
 * Must be used inside an `authenticate("jwt")` block (TenantContext required).
 *
 * Usage:
 * ```
 * authenticate("jwt") {
 *     requireFeature(FeatureKey.ANALYTICS_DASHBOARD) {
 *         get("/analytics") { ... }
 *     }
 * }
 * ```
 */
fun Route.requireFeature(featureKey: String, build: Route.() -> Unit): Route {
    val featureService by application.inject<FeatureService>()

    val selector = object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }

    return createChild(selector).also { child ->
        child.intercept(ApplicationCallPipeline.Plugins) {
            val workspaceId = TenantContext.current().workspaceId
            if (!featureService.isEnabled(workspaceId, featureKey)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf(
                        "error" to "FEATURE_NOT_ENABLED",
                        "message" to "Your plan does not include: $featureKey",
                        "upgradeUrl" to "https://therapyflow.io/pricing"
                    )
                )
                return@intercept finish()
            }
        }
        build(child)
    }
}
