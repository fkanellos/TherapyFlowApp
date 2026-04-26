package io.therapyflow.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.api.dto.*
import io.therapyflow.api.extensions.requireRole
import io.therapyflow.data.repository.AppointmentRepository
import io.therapyflow.data.repository.ClientRepository
import io.therapyflow.data.repository.TherapistRepository
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.*
import org.koin.ktor.ext.inject
import java.math.BigDecimal
import java.time.Instant
import java.util.*

fun Route.appointmentRoutes() {
    val appointmentRepository by inject<AppointmentRepository>()
    val therapistRepository by inject<TherapistRepository>()
    val clientRepository by inject<ClientRepository>()

    authenticate("jwt") {
        route("/appointments") {

            // GET /v1/appointments — OWNER sees all, THERAPIST sees own
            get {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val appointments = if (role == UserRole.OWNER.name) {
                    appointmentRepository.findAll()
                } else {
                    val therapist = therapistRepository.findByUserId(userId)
                        ?: throw AppError.NotFound("Therapist", userId.toString())
                    appointmentRepository.findByTherapistId(therapist.id)
                }

                call.respond(HttpStatusCode.OK, mapOf("data" to appointments.map { it.toResponse() }))
            }

            // GET /v1/appointments/{id}
            get("/{id}") {
                val id = call.appointmentPathParam("id")
                val appointment = appointmentRepository.findById(UUID.fromString(id))
                    ?: throw AppError.NotFound("Appointment", id)

                ensureAppointmentAccess(call, appointment, therapistRepository)

                call.respond(HttpStatusCode.OK, mapOf("data" to appointment.toResponse()))
            }

            // POST /v1/appointments
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val request = call.receive<CreateAppointmentRequest>()
                val errors = request.validate()
                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                    return@post
                }

                val therapistId = UUID.fromString(request.therapistId)
                val clientId = UUID.fromString(request.clientId)

                // Verify therapist and client exist
                therapistRepository.findById(therapistId)
                    ?: throw AppError.NotFound("Therapist", request.therapistId)
                clientRepository.findById(clientId)
                    ?: throw AppError.NotFound("Client", request.clientId)

                // THERAPIST can only create for themselves
                if (role == UserRole.THERAPIST.name) {
                    val therapist = therapistRepository.findByUserId(userId)
                    if (therapist == null || therapist.id != therapistId) {
                        throw AppError.Forbidden()
                    }
                }

                val appointment = appointmentRepository.create(
                    Appointment(
                        id = UUID.randomUUID(),
                        therapistId = therapistId,
                        clientId = clientId,
                        startTime = Instant.parse(request.startTime),
                        durationMinutes = request.durationMinutes,
                        price = BigDecimal.valueOf(request.price),
                        sessionType = SessionType.valueOf(request.sessionType),
                        status = AppointmentStatus.SCHEDULED,
                        googleCalendarEventId = null,
                        source = AppointmentSource.MANUAL,
                        notes = request.notes,
                        isActive = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                )

                call.respond(HttpStatusCode.Created, mapOf("data" to appointment.toResponse()))
            }

            // PUT /v1/appointments/{id}
            put("/{id}") {
                val id = call.appointmentPathParam("id")
                val existing = appointmentRepository.findById(UUID.fromString(id))
                    ?: throw AppError.NotFound("Appointment", id)

                ensureAppointmentAccess(call, existing, therapistRepository)

                val request = call.receive<UpdateAppointmentRequest>()
                val errors = request.validate()
                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                    return@put
                }

                val updated = appointmentRepository.update(
                    existing.copy(
                        startTime = request.startTime?.let { Instant.parse(it) } ?: existing.startTime,
                        durationMinutes = request.durationMinutes ?: existing.durationMinutes,
                        price = request.price?.let { BigDecimal.valueOf(it) } ?: existing.price,
                        sessionType = request.sessionType?.let { SessionType.valueOf(it) } ?: existing.sessionType,
                        status = request.status?.let { AppointmentStatus.valueOf(it) } ?: existing.status,
                        notes = request.notes ?: existing.notes
                    )
                )

                call.respond(HttpStatusCode.OK, mapOf("data" to updated.toResponse()))
            }

            // DELETE /v1/appointments/{id} — OWNER only, soft delete
            requireRole(UserRole.OWNER) {
                delete("/{id}") {
                    val id = call.appointmentPathParam("id")
                    appointmentRepository.findById(UUID.fromString(id))
                        ?: throw AppError.NotFound("Appointment", id)

                    appointmentRepository.softDelete(UUID.fromString(id))
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Appointment deactivated"))
                }
            }
        }
    }
}

private suspend fun ensureAppointmentAccess(
    call: RoutingCall,
    appointment: Appointment,
    therapistRepository: TherapistRepository
) {
    val principal = call.principal<JWTPrincipal>()!!
    val role = principal.payload.getClaim("role").asString()
    if (role == UserRole.OWNER.name) return

    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
    val therapist = therapistRepository.findByUserId(userId)
    if (therapist == null || therapist.id != appointment.therapistId) {
        throw AppError.Forbidden()
    }
}

private fun RoutingCall.appointmentPathParam(name: String): String =
    pathParameters[name] ?: throw AppError.ValidationFailed("Missing path parameter: $name")

private fun Appointment.toResponse() = AppointmentResponse(
    id = id.toString(),
    therapistId = therapistId.toString(),
    clientId = clientId.toString(),
    startTime = startTime.toString(),
    durationMinutes = durationMinutes,
    price = price.toDouble(),
    sessionType = sessionType.name,
    status = status.name,
    source = source.name,
    notes = notes,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
