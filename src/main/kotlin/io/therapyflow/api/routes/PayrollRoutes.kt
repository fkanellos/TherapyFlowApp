package io.therapyflow.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.api.dto.*
import io.therapyflow.api.extensions.requireRole
import io.therapyflow.data.repository.PayrollRepository
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.PayrollEntry
import io.therapyflow.domain.model.PayrollPeriod
import io.therapyflow.domain.model.UserRole
import io.therapyflow.domain.service.PayrollCalculationService
import org.koin.ktor.ext.inject
import java.util.*

fun Route.payrollRoutes() {
    val payrollService by application.inject<PayrollCalculationService>()
    val payrollRepository by application.inject<PayrollRepository>()

    authenticate("jwt") {
        requireRole(UserRole.OWNER) {
            route("/payroll") {

                // POST /v1/payroll/calculate
                post("/calculate") {
                    val request = call.receive<CalculatePayrollRequest>()
                    val errors = request.validate()
                    if (errors.isNotEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                        return@post
                    }

                    val period = payrollService.calculate(request.month, request.year)
                    val entries = payrollRepository.findEntriesByPeriod(period.id)

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("data" to period.toResponse(entries))
                    )
                }

                // GET /v1/payroll/{id}
                get("/{id}") {
                    val id = call.payrollPathParam("id")
                    val period = payrollRepository.findPeriodById(UUID.fromString(id))
                        ?: throw AppError.NotFound("PayrollPeriod", id)

                    val entries = payrollRepository.findEntriesByPeriod(period.id)

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("data" to period.toResponse(entries))
                    )
                }

                // PUT /v1/payroll/{id}/finalize
                put("/{id}/finalize") {
                    val id = call.payrollPathParam("id")
                    val period = payrollService.finalize(UUID.fromString(id))
                    val entries = payrollRepository.findEntriesByPeriod(period.id)

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("data" to period.toResponse(entries))
                    )
                }
            }
        }
    }
}

private fun RoutingCall.payrollPathParam(name: String): String =
    pathParameters[name] ?: throw AppError.ValidationFailed("Missing path parameter: $name")

private fun PayrollPeriod.toResponse(entries: List<PayrollEntry>) = PayrollPeriodResponse(
    id = id.toString(),
    month = month,
    year = year,
    status = status.name,
    createdAt = createdAt.toString(),
    finalizedAt = finalizedAt?.toString(),
    entries = entries.map { it.toResponse() }
)

private fun PayrollEntry.toResponse() = PayrollEntryResponse(
    id = id.toString(),
    therapistId = therapistId.toString(),
    totalSessions = totalSessions,
    totalRevenue = totalRevenue.toDouble(),
    commissionAmount = commissionAmount.toDouble(),
    breakdown = breakdown,
    createdAt = createdAt.toString()
)
