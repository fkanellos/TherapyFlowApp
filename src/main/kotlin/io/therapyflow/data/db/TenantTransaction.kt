package io.therapyflow.data.db

import io.therapyflow.domain.tenant.TenantContext
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Runs a suspended Exposed transaction scoped to the current tenant schema.
 * Sets `search_path` to the tenant schema + public before executing the block.
 *
 * Every tenant-aware repository method MUST use this instead of raw `transaction {}`.
 * See: docs/multi-tenancy.md
 */
suspend fun <T> tenantTransaction(block: Transaction.() -> T): T {
    val schema = TenantContext.current().schema
    return newSuspendedTransaction {
        exec("SET search_path TO \"$schema\", public")
        block()
    }
}

/**
 * Runs a suspended Exposed transaction scoped to the public schema only.
 * Use for cross-tenant queries: auth, workspace lookup, user management.
 */
suspend fun <T> publicTransaction(block: Transaction.() -> T): T {
    return newSuspendedTransaction {
        exec("SET search_path TO public")
        block()
    }
}
