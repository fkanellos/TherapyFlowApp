# Authentication — Implementation Guide

## Overview

TherapyFlow uses JWT-based authentication with short-lived access tokens
and long-lived refresh tokens. All tokens are stateless except refresh tokens
which are stored in the DB for revocation.

## Token Strategy

```
Access Token:   15 minutes  — sent with every API request
Refresh Token:  30 days     — used only to get a new access token
```

**Why short access tokens:**
Medical/payroll data is sensitive. If a token is stolen, damage window is 15min.

## JWT Payload

```json
{
  "userId": "uuid",
  "workspaceId": "uuid",
  "workspaceSlug": "apov",
  "role": "OWNER | THERAPIST | ADMIN_STAFF",
  "exp": 1234567890,
  "iat": 1234567890,
  "iss": "therapyflow.io"
}
```

## Endpoints

```
POST /v1/auth/login          → email + password → access + refresh token
POST /v1/auth/refresh        → refresh token → new access token
POST /v1/auth/logout         → revoke refresh token
POST /v1/auth/register       → create workspace + owner account (onboarding)
```

## Implementation — Ktor Plugin

```kotlin
// plugins/AuthPlugin.kt
fun Application.configureAuth() {
    install(Authentication) {
        jwt("jwt") {
            realm = "therapyflow"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer("therapyflow.io")
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                    ?: return@validate null
                val workspaceId = credential.payload.getClaim("workspaceId").asString()
                    ?: return@validate null
                val slug = credential.payload.getClaim("workspaceSlug").asString()
                    ?: return@validate null
                val role = credential.payload.getClaim("role").asString()
                    ?: return@validate null

                // Set tenant context for this request
                TenantContext.set(TenantContext(
                    workspaceId = UUID.fromString(workspaceId),
                    schema = "tenant_$slug"
                ))

                JWTPrincipal(credential.payload)
            }
        }
    }
}
```

## Role Guard Helper

```kotlin
// api/extensions/RoleGuard.kt
fun Route.requireRole(vararg roles: UserRole, build: Route.() -> Unit): Route {
    return createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    }).also { child ->
        child.intercept(ApplicationCallPipeline.Plugins) {
            val principal = call.principal<JWTPrincipal>()
                ?: return@intercept call.respond(HttpStatusCode.Unauthorized)
            val role = principal.payload.getClaim("role").asString()
            if (roles.none { it.name == role }) {
                call.respond(HttpStatusCode.Forbidden, mapOf(
                    "error" to "FORBIDDEN",
                    "message" to "Required role: ${roles.joinToString()}"
                ))
                return@intercept finish()
            }
        }
        build(child)
    }
}

// Usage in routes:
authenticate("jwt") {
    route("/payroll") {
        requireRole(UserRole.OWNER) {
            post("/calculate") { ... }
            put("/{id}/finalize") { ... }
        }
    }
    route("/appointments") {
        // No requireRole → both OWNER and THERAPIST, filtered in service layer
        get { ... }
        post { ... }
    }
}
```

## Login Flow

```kotlin
// api/routes/AuthRoutes.kt
post("/v1/auth/login") {
    val request = call.receive<LoginRequest>()

    val user = userRepository.findByEmail(request.email)
        ?: return@post call.respond(HttpStatusCode.Unauthorized,
            mapOf("error" to "INVALID_CREDENTIALS"))

    if (!passwordHasher.verify(request.password, user.hashedPassword)) {
        return@post call.respond(HttpStatusCode.Unauthorized,
            mapOf("error" to "INVALID_CREDENTIALS"))
    }

    if (!user.isActive) {
        return@post call.respond(HttpStatusCode.Forbidden,
            mapOf("error" to "ACCOUNT_DISABLED"))
    }

    val workspace = workspaceRepository.findById(user.workspaceId)
        ?: return@post call.respond(HttpStatusCode.InternalServerError)

    val accessToken = jwtService.generateAccessToken(user, workspace)
    val refreshToken = jwtService.generateRefreshToken()

    // Store hashed refresh token in DB
    refreshTokenRepository.save(RefreshToken(
        userId = user.id,
        tokenHash = hashToken(refreshToken),
        expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
    ))

    call.respond(LoginResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = 900  // 15 min in seconds
    ))
}
```

## Token Generation

```kotlin
// domain/service/JwtService.kt
class JwtService(private val secret: String) {

    fun generateAccessToken(user: User, workspace: Workspace): String {
        return JWT.create()
            .withIssuer("therapyflow.io")
            .withClaim("userId", user.id.toString())
            .withClaim("workspaceId", workspace.id.toString())
            .withClaim("workspaceSlug", workspace.slug)
            .withClaim("role", user.role.name)
            .withExpiresAt(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
            .withIssuedAt(Date.from(Instant.now()))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(): String =
        UUID.randomUUID().toString() + UUID.randomUUID().toString()
}
```

## Password Hashing

Use BCrypt — NEVER store plain passwords.

```kotlin
// domain/service/PasswordHasher.kt
class PasswordHasher {
    private val workFactor = 12

    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(workFactor))
    fun verify(plain: String, hashed: String): Boolean = BCrypt.checkpw(plain, hashed)
}
```

## Refresh Flow

```kotlin
post("/v1/auth/refresh") {
    val request = call.receive<RefreshRequest>()
    val tokenHash = hashToken(request.refreshToken)

    val stored = refreshTokenRepository.findByTokenHash(tokenHash)
        ?: return@post call.respond(HttpStatusCode.Unauthorized,
            mapOf("error" to "INVALID_REFRESH_TOKEN"))

    if (stored.revoked || stored.expiresAt.isBefore(Instant.now())) {
        return@post call.respond(HttpStatusCode.Unauthorized,
            mapOf("error" to "TOKEN_EXPIRED"))
    }

    val user = userRepository.findById(stored.userId)!!
    val workspace = workspaceRepository.findById(user.workspaceId)!!

    val newAccessToken = jwtService.generateAccessToken(user, workspace)
    call.respond(RefreshResponse(accessToken = newAccessToken, expiresIn = 900))
}
```

## Security Rules

- NEVER log tokens or passwords
- NEVER return user password hash in any response
- ALWAYS use constant-time comparison for token hashes (BCrypt handles this)
- On logout: revoke refresh token in DB
- On password change: revoke ALL refresh tokens for that user
- Rate limit login endpoint: max 5 attempts per 15min per IP

## Environment Variables

```
JWT_SECRET=<64-char random hex — generate with: openssl rand -hex 32>
JWT_ISSUER=therapyflow.io
```
