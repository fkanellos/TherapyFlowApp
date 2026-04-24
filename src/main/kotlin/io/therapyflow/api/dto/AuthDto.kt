package io.therapyflow.api.dto

import kotlinx.serialization.Serializable

// ── Login ───────────────────────────────────────────────────────────

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (email.isBlank()) errors.add("Email is required")
        if (password.isBlank()) errors.add("Password is required")
        return errors
    }
}

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

// ── Refresh ─────────────────────────────────────────────────────────

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val expiresIn: Int
)

// ── Register (workspace + owner onboarding) ─────────────────────────

@Serializable
data class RegisterRequest(
    val workspaceName: String,
    val workspaceSlug: String,
    val email: String,
    val password: String
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (workspaceName.isBlank()) errors.add("Workspace name is required")
        if (workspaceSlug.isBlank()) errors.add("Workspace slug is required")
        if (!workspaceSlug.matches(Regex("^[a-z0-9_]+$")))
            errors.add("Slug must contain only lowercase letters, digits, and underscores")
        if (email.isBlank()) errors.add("Email is required")
        if (!email.contains("@")) errors.add("Email must be valid")
        if (password.length < 8) errors.add("Password must be at least 8 characters")
        return errors
    }
}

@Serializable
data class RegisterResponse(
    val workspaceId: String,
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

// ── Logout ──────────────────────────────────────────────────────────

@Serializable
data class LogoutRequest(
    val refreshToken: String
)
