package io.therapyflow.domain.error

/**
 * All domain errors extend AppError.
 * StatusPages plugin maps these to structured JSON responses.
 * See: docs/api-guidelines.md — Error format section
 */
sealed class AppError(
    val code: String,
    override val message: String,
    val httpStatus: Int
) : Exception(message) {

    // Auth errors
    class InvalidCredentials : AppError("INVALID_CREDENTIALS", "Email or password is incorrect", 401)
    class TokenExpired       : AppError("TOKEN_EXPIRED",        "Access token has expired", 401)
    class Forbidden          : AppError("FORBIDDEN",            "You do not have permission to perform this action", 403)
    class AccountDisabled    : AppError("ACCOUNT_DISABLED",     "This account has been disabled", 403)

    // Feature flag errors
    class FeatureNotEnabled(featureKey: String) : AppError(
        "FEATURE_NOT_ENABLED",
        "Your plan does not include: $featureKey. Visit therapyflow.io/pricing to upgrade.",
        403
    )

    // Resource errors
    class NotFound(resource: String, id: String) : AppError(
        "NOT_FOUND",
        "$resource with id '$id' was not found",
        404
    )
    class Conflict(message: String) : AppError("CONFLICT", message, 409)

    // Business logic errors (422 Unprocessable)
    class PayrollAlreadyFinalized(period: String) : AppError(
        "PAYROLL_ALREADY_FINALIZED",
        "Payroll period $period is finalized and cannot be modified",
        409
    )
    class ValidationFailed(details: String) : AppError("VALIDATION_FAILED", details, 422)
    class TenantNotFound(slug: String) : AppError(
        "TENANT_NOT_FOUND",
        "Workspace '$slug' does not exist",
        404
    )
}
