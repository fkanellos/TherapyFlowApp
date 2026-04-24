package io.therapyflow.domain.service

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher {
    private val workFactor = 12

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(workFactor, password.toCharArray())

    fun verify(plain: String, hashed: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hashed).verified
}
