package io.therapyflow.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.therapyflow.domain.tenant.TenantContext
import java.util.*

fun Application.configureAuthentication() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: error("JWT_SECRET environment variable is required")
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "therapyflow.io"

    install(Authentication) {
        jwt("jwt") {
            realm = "therapyflow"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId      = credential.payload.getClaim("userId").asString()
                    ?: return@validate null
                val workspaceId = credential.payload.getClaim("workspaceId").asString()
                    ?: return@validate null
                val slug        = credential.payload.getClaim("workspaceSlug").asString()
                    ?: return@validate null

                // Set tenant context for this request — read docs/multi-tenancy.md
                TenantContext.set(
                    TenantContext(
                        workspaceId = UUID.fromString(workspaceId),
                        schema = "tenant_$slug"
                    )
                )

                JWTPrincipal(credential.payload)
            }
        }
    }
}
