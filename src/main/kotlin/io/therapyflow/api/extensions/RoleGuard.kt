package io.therapyflow.api.extensions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.domain.model.UserRole

/**
 * Route guard that restricts access to users with specific roles.
 * Must be used inside an `authenticate("jwt")` block.
 *
 * Usage:
 * ```
 * authenticate("jwt") {
 *     requireRole(UserRole.OWNER) {
 *         post("/calculate") { ... }
 *     }
 * }
 * ```
 */
fun Route.requireRole(vararg roles: UserRole, build: Route.() -> Unit): Route {
    val selector = object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }

    return createChild(selector).also { child ->
        child.intercept(ApplicationCallPipeline.Plugins) {
            val principal = call.principal<JWTPrincipal>()
            if (principal == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "UNAUTHORIZED", "message" to "Authentication required")
                )
                return@intercept finish()
            }

            val role = principal.payload.getClaim("role").asString()
            if (roles.none { it.name == role }) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf(
                        "error" to "FORBIDDEN",
                        "message" to "Required role: ${roles.joinToString()}"
                    )
                )
                return@intercept finish()
            }
        }
        build(child)
    }
}
