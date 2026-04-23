package io.therapyflow.domain.tenant

import java.util.*

/**
 * Holds the current tenant (workspace) context for the duration of a request.
 * Set in Authentication plugin after JWT validation.
 * Read in every repository before executing DB queries.
 *
 * See: docs/multi-tenancy.md — READ BEFORE WRITING ANY DB CODE
 */
class TenantContext(
    val workspaceId: UUID,
    val schema: String          // e.g. "tenant_apov"
) {
    companion object {
        private val local = ThreadLocal<TenantContext>()

        fun set(ctx: TenantContext) = local.set(ctx)

        fun current(): TenantContext =
            local.get() ?: error(
                "No TenantContext set. " +
                "Did you forget authenticate(\"jwt\") on this route? " +
                "See docs/multi-tenancy.md"
            )

        fun clear() = local.remove()
    }
}
