package io.therapyflow.plugins

import io.ktor.server.application.*
import io.therapyflow.domain.tenant.TenantContext

/**
 * Clears TenantContext after every request to prevent ThreadLocal leaks
 * between requests handled on the same thread.
 *
 * Must be installed early in the pipeline (before auth) so the finally
 * block runs after all other plugins, including authentication.
 */
fun Application.configureTenantCleanup() {
    intercept(ApplicationCallPipeline.Monitoring) {
        try {
            proceed()
        } finally {
            TenantContext.clear()
        }
    }
}
