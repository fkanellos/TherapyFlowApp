package io.therapyflow.domain

import io.therapyflow.domain.service.PasswordHasher
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    private val hasher = PasswordHasher()

    @Test
    fun `hash produces a non-empty string different from input`() {
        val hash = hasher.hash("mysecretpassword")
        assertTrue(hash.isNotBlank())
        assertNotEquals("mysecretpassword", hash)
    }

    @Test
    fun `verify returns true for matching password`() {
        val password = "secure_P@ssw0rd!"
        val hash = hasher.hash(password)
        assertTrue(hasher.verify(password, hash))
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = hasher.hash("correct_password")
        assertFalse(hasher.verify("wrong_password", hash))
    }

    @Test
    fun `different passwords produce different hashes`() {
        val hash1 = hasher.hash("password1")
        val hash2 = hasher.hash("password2")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `same password hashed twice produces different hashes due to salt`() {
        val hash1 = hasher.hash("same_password")
        val hash2 = hasher.hash("same_password")
        // BCrypt uses random salt, so hashes should differ
        assertNotEquals(hash1, hash2)
        // But both should still verify
        assertTrue(hasher.verify("same_password", hash1))
        assertTrue(hasher.verify("same_password", hash2))
    }
}
