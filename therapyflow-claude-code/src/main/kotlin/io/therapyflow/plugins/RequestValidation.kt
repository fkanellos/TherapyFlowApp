package io.therapyflow.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        // Validators are registered here as DTOs are added.
        // Example:
        // validate<CreateAppointmentRequest> { request ->
        //     val errors = request.validate()
        //     if (errors.isEmpty()) ValidationResult.Valid
        //     else ValidationResult.Invalid(errors)
        // }
    }
}
