package io.therapyflow.domain

import io.therapyflow.domain.service.EncryptionService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class EncryptionServiceTest {

    // 64 hex chars = 256-bit key
    private val testKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val service = EncryptionService(testKey)

    @Test
    fun `encrypt and decrypt round-trip produces original plaintext`() {
        val plaintext = "Hello, World!"
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt produces unique ciphertexts for same input (unique IVs)`() {
        val plaintext = "same value"
        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)
        assertNotEquals(encrypted1, encrypted2)
        // Both decrypt to same value
        assertEquals(plaintext, service.decrypt(encrypted1))
        assertEquals(plaintext, service.decrypt(encrypted2))
    }

    @Test
    fun `handles Unicode characters including Greek names`() {
        val greek = "Φίλιππος Κανέλλος"
        val encrypted = service.encrypt(greek)
        assertEquals(greek, service.decrypt(encrypted))
    }

    @Test
    fun `encryptNullable returns null for null input`() {
        assertNull(service.encryptNullable(null))
    }

    @Test
    fun `decryptNullable returns null for null input`() {
        assertNull(service.decryptNullable(null))
    }

    @Test
    fun `encryptNullable and decryptNullable round-trip for non-null`() {
        val plaintext = "therapy notes"
        val encrypted = service.encryptNullable(plaintext)
        assertEquals(plaintext, service.decryptNullable(encrypted))
    }

    @Test
    fun `decrypt with wrong key throws exception`() {
        val encrypted = service.encrypt("secret data")
        val wrongKey = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val wrongService = EncryptionService(wrongKey)
        assertThrows<Exception> {
            wrongService.decrypt(encrypted)
        }
    }

    @Test
    fun `tampered ciphertext throws exception`() {
        val encrypted = service.encrypt("important data")
        val parts = encrypted.split(":")
        // Flip a character in the ciphertext portion
        val tampered = parts[0] + ":" + parts[1].reversed()
        assertThrows<Exception> {
            service.decrypt(tampered)
        }
    }

    @Test
    fun `invalid key length throws on construction`() {
        assertThrows<IllegalArgumentException> {
            EncryptionService("tooshort")
        }
    }

    @Test
    fun `encrypted output format is Base64-IV colon Base64-ciphertext`() {
        val encrypted = service.encrypt("test")
        val parts = encrypted.split(":")
        assertEquals(2, parts.size)
        // Both parts should be valid Base64
        java.util.Base64.getDecoder().decode(parts[0])
        java.util.Base64.getDecoder().decode(parts[1])
    }
}
