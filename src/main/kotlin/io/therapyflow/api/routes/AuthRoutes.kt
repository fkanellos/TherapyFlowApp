package io.therapyflow.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.api.dto.*
import io.therapyflow.data.db.TenantSchemaService
import io.therapyflow.data.repository.RefreshTokenRepository
import io.therapyflow.data.repository.UserRepository
import io.therapyflow.data.repository.WorkspaceRepository
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.*
import io.therapyflow.domain.service.JwtService
import io.therapyflow.domain.service.PasswordHasher
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

fun Route.authRoutes() {
    val userRepository by inject<UserRepository>()
    val workspaceRepository by inject<WorkspaceRepository>()
    val refreshTokenRepository by inject<RefreshTokenRepository>()
    val jwtService by inject<JwtService>()
    val passwordHasher by inject<PasswordHasher>()
    val tenantSchemaService by inject<TenantSchemaService>()

    route("/auth") {

        // POST /v1/auth/login
        post("/login") {
            val request = call.receive<LoginRequest>()
            val errors = request.validate()
            if (errors.isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                return@post
            }

            val user = userRepository.findByEmail(request.email)
                ?: throw AppError.InvalidCredentials()

            if (!passwordHasher.verify(request.password, user.hashedPassword)) {
                throw AppError.InvalidCredentials()
            }

            if (!user.isActive) {
                throw AppError.AccountDisabled()
            }

            val workspace = workspaceRepository.findById(user.workspaceId)
                ?: error("User ${user.id} references non-existent workspace ${user.workspaceId}")

            val accessToken = jwtService.generateAccessToken(user, workspace)
            val refreshToken = jwtService.generateRefreshToken()

            refreshTokenRepository.save(
                RefreshToken(
                    id = UUID.randomUUID(),
                    userId = user.id,
                    tokenHash = jwtService.hashToken(refreshToken),
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
                    revoked = false,
                    createdAt = Instant.now()
                )
            )

            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = 900
                )
            )
        }

        // POST /v1/auth/refresh
        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val tokenHash = jwtService.hashToken(request.refreshToken)

            val stored = refreshTokenRepository.findByTokenHash(tokenHash)
                ?: throw AppError.TokenExpired()

            if (stored.revoked || stored.expiresAt.isBefore(Instant.now())) {
                throw AppError.TokenExpired()
            }

            val user = userRepository.findById(stored.userId)
                ?: error("Refresh token references non-existent user ${stored.userId}")

            if (!user.isActive) {
                throw AppError.AccountDisabled()
            }

            val workspace = workspaceRepository.findById(user.workspaceId)
                ?: error("User ${user.id} references non-existent workspace ${user.workspaceId}")

            val newAccessToken = jwtService.generateAccessToken(user, workspace)

            call.respond(
                HttpStatusCode.OK,
                RefreshResponse(accessToken = newAccessToken, expiresIn = 900)
            )
        }

        // POST /v1/auth/logout
        post("/logout") {
            val request = call.receive<LogoutRequest>()
            val tokenHash = jwtService.hashToken(request.refreshToken)
            refreshTokenRepository.revoke(tokenHash)

            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }

        // POST /v1/auth/register — create workspace + owner account
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val errors = request.validate()
            if (errors.isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                return@post
            }

            // Check uniqueness
            if (workspaceRepository.slugExists(request.workspaceSlug)) {
                throw AppError.Conflict("Workspace slug '${request.workspaceSlug}' is already taken")
            }
            if (userRepository.findByEmail(request.email) != null) {
                throw AppError.Conflict("Email '${request.email}' is already registered")
            }

            // Create workspace
            val workspaceId = UUID.randomUUID()
            val workspace = workspaceRepository.create(
                Workspace(
                    id = workspaceId,
                    name = request.workspaceName,
                    slug = request.workspaceSlug,
                    plan = Plan.FREE,
                    status = WorkspaceStatus.ACTIVE,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            // Create tenant schema with all tables
            tenantSchemaService.createSchema(request.workspaceSlug)

            // Create owner user
            val userId = UUID.randomUUID()
            val user = userRepository.create(
                User(
                    id = userId,
                    workspaceId = workspaceId,
                    email = request.email,
                    hashedPassword = passwordHasher.hash(request.password),
                    role = UserRole.OWNER,
                    isActive = true,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            // Generate tokens
            val accessToken = jwtService.generateAccessToken(user, workspace)
            val refreshToken = jwtService.generateRefreshToken()

            refreshTokenRepository.save(
                RefreshToken(
                    id = UUID.randomUUID(),
                    userId = userId,
                    tokenHash = jwtService.hashToken(refreshToken),
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
                    revoked = false,
                    createdAt = Instant.now()
                )
            )

            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(
                    workspaceId = workspaceId.toString(),
                    userId = userId.toString(),
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = 900
                )
            )
        }
    }
}
