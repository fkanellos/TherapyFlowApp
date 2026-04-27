package io.therapyflow.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.mockk.*
import io.therapyflow.data.db.TenantSchemaService
import io.therapyflow.data.repository.*
import io.therapyflow.di.AppConfig
import io.therapyflow.domain.model.*
import io.therapyflow.domain.service.EncryptionService
import io.therapyflow.domain.service.FeatureService
import io.therapyflow.domain.service.JwtService
import io.therapyflow.domain.service.PasswordHasher
import io.therapyflow.fixtures.TestFixtures
import io.therapyflow.plugins.configureAuthentication
import io.therapyflow.plugins.configureRouting
import io.therapyflow.plugins.configureSerialization
import io.therapyflow.plugins.configureStatusPages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthRoutesTest {

    private val userRepository = mockk<UserRepository>()
    private val workspaceRepository = mockk<WorkspaceRepository>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val featureRepository = mockk<FeatureRepository>()
    private val therapistRepository = mockk<TherapistRepository>()
    private val clientRepository = mockk<ClientRepository>()
    private val appointmentRepository = mockk<AppointmentRepository>()
    private val payrollRepository = mockk<PayrollRepository>()
    private val pendingChargeRepository = mockk<PendingChargeRepository>()
    private val passwordHasher = PasswordHasher() // real — lightweight, no DB
    private val jwtService = JwtService("test-secret", "test-issuer")
    private val featureService = mockk<FeatureService>()
    private val tenantSchemaService = mockk<TenantSchemaService>()

    private val testEncryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val encryptionService = EncryptionService(testEncryptionKey)

    private val testKoinModule = module {
        single { AppConfig("test-secret", "test-issuer", "", "", "", "", "", testEncryptionKey) }
        single { jwtService }
        single { passwordHasher }
        single { encryptionService }
        single { featureService }
        single { tenantSchemaService }
        single<UserRepository> { userRepository }
        single<WorkspaceRepository> { workspaceRepository }
        single<RefreshTokenRepository> { refreshTokenRepository }
        single<FeatureRepository> { featureRepository }
        single<TherapistRepository> { therapistRepository }
        single<ClientRepository> { clientRepository }
        single<AppointmentRepository> { appointmentRepository }
        single<PayrollRepository> { payrollRepository }
        single<PendingChargeRepository> { pendingChargeRepository }
        single { io.therapyflow.domain.service.PayrollCalculationService(get(), get(), get(), get(), get()) }
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    private fun Application.configureTestApp() {
        install(Koin) { modules(testKoinModule) }
        configureSerialization()
        configureAuthentication()
        configureStatusPages()
        configureRouting()
    }

    // ── Login tests ─────────────────────────────────────────────────────

    @Test
    fun `login with valid credentials returns 200 with tokens`() = testApplication {
        application { configureTestApp() }

        val hashedPw = passwordHasher.hash("correct_password")
        val user = TestFixtures.ownerUser(hashedPassword = hashedPw)
        val workspace = TestFixtures.workspace()

        coEvery { userRepository.findByEmail("owner@test.com") } returns user
        coEvery { workspaceRepository.findById(user.workspaceId) } returns workspace
        coEvery { refreshTokenRepository.save(any()) } answers { firstArg() }

        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@test.com","password":"correct_password"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["accessToken"])
        assertNotNull(body["refreshToken"])
        assertEquals("900", body["expiresIn"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login with wrong password returns 401`() = testApplication {
        application { configureTestApp() }

        val hashedPw = passwordHasher.hash("correct_password")
        val user = TestFixtures.ownerUser(hashedPassword = hashedPw)

        coEvery { userRepository.findByEmail("owner@test.com") } returns user

        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@test.com","password":"wrong_password"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("INVALID_CREDENTIALS", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login with unknown email returns 401`() = testApplication {
        application { configureTestApp() }

        coEvery { userRepository.findByEmail("unknown@test.com") } returns null

        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"unknown@test.com","password":"somepassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login with disabled account returns 403`() = testApplication {
        application { configureTestApp() }

        val hashedPw = passwordHasher.hash("password123")
        val disabledUser = TestFixtures.ownerUser(hashedPassword = hashedPw).copy(isActive = false)

        coEvery { userRepository.findByEmail("owner@test.com") } returns disabledUser

        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@test.com","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ACCOUNT_DISABLED", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login with blank email returns 400 validation error`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"","password":"somepassword"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── Register tests ──────────────────────────────────────────────────

    @Test
    fun `register creates workspace and owner and returns 201`() = testApplication {
        application { configureTestApp() }

        coEvery { workspaceRepository.slugExists("new_clinic") } returns false
        coEvery { userRepository.findByEmail("new@test.com") } returns null
        coEvery { workspaceRepository.create(any()) } answers { firstArg() }
        coEvery { tenantSchemaService.createSchema("new_clinic") } returns Unit
        coEvery { featureService.provisionDefaults(any(), Plan.FREE) } returns Unit
        coEvery { userRepository.create(any()) } answers { firstArg() }
        coEvery { refreshTokenRepository.save(any()) } answers { firstArg() }

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "New Clinic",
                    "workspaceSlug": "new_clinic",
                    "email": "new@test.com",
                    "password": "Secure@Pass123!"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["workspaceId"])
        assertNotNull(body["userId"])
        assertNotNull(body["accessToken"])
        assertNotNull(body["refreshToken"])

        coVerify { tenantSchemaService.createSchema("new_clinic") }
        coVerify { featureService.provisionDefaults(any(), Plan.FREE) }
    }

    @Test
    fun `register with duplicate slug returns 409`() = testApplication {
        application { configureTestApp() }

        coEvery { workspaceRepository.slugExists("taken_slug") } returns true

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Some Clinic",
                    "workspaceSlug": "taken_slug",
                    "email": "new@test.com",
                    "password": "Secure@Pass123!"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `register with duplicate email returns 409`() = testApplication {
        application { configureTestApp() }

        coEvery { workspaceRepository.slugExists("unique_slug") } returns false
        coEvery { userRepository.findByEmail("existing@test.com") } returns TestFixtures.ownerUser(email = "existing@test.com")

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Another Clinic",
                    "workspaceSlug": "unique_slug",
                    "email": "existing@test.com",
                    "password": "Secure@Pass123!"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `register with short password returns 400`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Clinic",
                    "workspaceSlug": "clinic",
                    "email": "user@test.com",
                    "password": "short"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register with invalid slug returns 400`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Clinic",
                    "workspaceSlug": "INVALID-Slug!",
                    "email": "user@test.com",
                    "password": "Secure@Pass123!"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── Refresh tests ───────────────────────────────────────────────────

    @Test
    fun `refresh with valid token returns new access token`() = testApplication {
        application { configureTestApp() }

        val refreshToken = "valid-refresh-token"
        val tokenHash = jwtService.hashToken(refreshToken)
        val user = TestFixtures.ownerUser()
        val workspace = TestFixtures.workspace()
        val stored = RefreshToken(
            id = UUID.randomUUID(),
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
            revoked = false,
            createdAt = Instant.now()
        )

        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns stored
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { workspaceRepository.findById(user.workspaceId) } returns workspace

        val response = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["accessToken"])
    }

    @Test
    fun `refresh with expired token returns 401`() = testApplication {
        application { configureTestApp() }

        val refreshToken = "expired-token"
        val tokenHash = jwtService.hashToken(refreshToken)
        val stored = RefreshToken(
            id = UUID.randomUUID(),
            userId = TestFixtures.OWNER_USER_ID,
            tokenHash = tokenHash,
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS), // expired
            revoked = false,
            createdAt = Instant.now().minus(31, ChronoUnit.DAYS)
        )

        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns stored

        val response = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `refresh with revoked token returns 401`() = testApplication {
        application { configureTestApp() }

        val refreshToken = "revoked-token"
        val tokenHash = jwtService.hashToken(refreshToken)
        val stored = RefreshToken(
            id = UUID.randomUUID(),
            userId = TestFixtures.OWNER_USER_ID,
            tokenHash = tokenHash,
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
            revoked = true,
            createdAt = Instant.now()
        )

        coEvery { refreshTokenRepository.findByTokenHash(tokenHash) } returns stored

        val response = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── Logout tests ────────────────────────────────────────────────────

    @Test
    fun `logout revokes refresh token and returns 200`() = testApplication {
        application { configureTestApp() }

        val refreshToken = "some-token-to-revoke"
        val tokenHash = jwtService.hashToken(refreshToken)

        coEvery { refreshTokenRepository.revoke(tokenHash) } returns Unit

        val response = client.post("/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { refreshTokenRepository.revoke(tokenHash) }
    }

    // ── Password complexity tests ────────────────────────────────────────

    @Test
    fun `register with no uppercase returns 400`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Clinic",
                    "workspaceSlug": "clinic",
                    "email": "user@test.com",
                    "password": "nouppercase@123!"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register with no special character returns 400`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Clinic",
                    "workspaceSlug": "clinic",
                    "email": "user@test.com",
                    "password": "NoSpecialChar123"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register with too-long password returns 400`() = testApplication {
        application { configureTestApp() }

        val longPassword = "A@1a" + "x".repeat(125) // 129 chars
        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "workspaceName": "Clinic",
                    "workspaceSlug": "clinic",
                    "email": "user@test.com",
                    "password": "$longPassword"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── Health check ────────────────────────────────────────────────────

    @Test
    fun `health endpoint returns 200`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
    }
}
