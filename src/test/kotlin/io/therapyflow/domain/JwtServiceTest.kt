package io.therapyflow.domain

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.therapyflow.domain.service.JwtService
import io.therapyflow.fixtures.TestFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtServiceTest {

    private val secret = "test-secret-key-for-unit-tests"
    private val issuer = "therapyflow-test"
    private val jwtService = JwtService(secret, issuer)

    @Test
    fun `generateAccessToken creates a valid JWT`() {
        val user = TestFixtures.ownerUser()
        val workspace = TestFixtures.workspace()

        val token = jwtService.generateAccessToken(user, workspace)
        assertTrue(token.isNotBlank())

        // Decode and verify claims
        val verifier = JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .build()
        val decoded = verifier.verify(token)

        assertEquals(user.id.toString(), decoded.getClaim("userId").asString())
        assertEquals(workspace.id.toString(), decoded.getClaim("workspaceId").asString())
        assertEquals(workspace.slug, decoded.getClaim("workspaceSlug").asString())
        assertEquals(user.role.name, decoded.getClaim("role").asString())
        assertNotNull(decoded.expiresAt)
    }

    @Test
    fun `generateAccessToken includes correct role`() {
        val therapist = TestFixtures.therapistUser()
        val workspace = TestFixtures.workspace()

        val token = jwtService.generateAccessToken(therapist, workspace)
        val decoded = JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .build()
            .verify(token)

        assertEquals("THERAPIST", decoded.getClaim("role").asString())
    }

    @Test
    fun `generateRefreshToken creates a non-empty random string`() {
        val token = jwtService.generateRefreshToken()
        assertTrue(token.isNotBlank())
        assertTrue(token.length > 30) // two UUIDs concatenated
    }

    @Test
    fun `generateRefreshToken produces unique tokens`() {
        val token1 = jwtService.generateRefreshToken()
        val token2 = jwtService.generateRefreshToken()
        assertNotEquals(token1, token2)
    }

    @Test
    fun `hashToken produces consistent output for same input`() {
        val token = "test-refresh-token"
        val hash1 = jwtService.hashToken(token)
        val hash2 = jwtService.hashToken(token)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashToken produces different output for different input`() {
        val hash1 = jwtService.hashToken("token-a")
        val hash2 = jwtService.hashToken("token-b")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashToken returns hex-encoded SHA-256 of 64 chars`() {
        val hash = jwtService.hashToken("any-token")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
