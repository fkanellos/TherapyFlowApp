package io.therapyflow.domain.service

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM field-level encryption for PII fields.
 * Output format: Base64(IV):Base64(ciphertext+tag)
 */
class EncryptionService(hexKey: String) {

    private val keyBytes: ByteArray
    private val random = SecureRandom()

    init {
        require(hexKey.length == 64) { "ENCRYPTION_KEY must be 64 hex characters (256 bits)" }
        keyBytes = hexKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return "${encoder.encodeToString(iv)}:${encoder.encodeToString(ciphertext)}"
    }

    fun decrypt(encrypted: String): String {
        val parts = encrypted.split(":")
        require(parts.size == 2) { "Invalid encrypted format" }
        val decoder = Base64.getDecoder()
        val iv = decoder.decode(parts[0])
        val ciphertext = decoder.decode(parts[1])
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun encryptNullable(plaintext: String?): String? = plaintext?.let { encrypt(it) }

    fun decryptNullable(encrypted: String?): String? = encrypted?.let { decrypt(it) }
}
