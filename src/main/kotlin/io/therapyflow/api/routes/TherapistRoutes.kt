package io.therapyflow.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.api.dto.*
import io.therapyflow.api.extensions.requireRole
import io.therapyflow.data.repository.TherapistRepository
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.Therapist
import io.therapyflow.domain.model.UserRole
import org.koin.ktor.ext.inject
import java.math.BigDecimal
import java.time.Instant
import java.util.*

fun Route.therapistRoutes() {
    val therapistRepository by inject<TherapistRepository>()

    authenticate("jwt") {
        route("/therapists") {

            // GET /v1/therapists — OWNER sees all, THERAPIST sees own
            get {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val therapists = if (role == UserRole.OWNER.name) {
                    therapistRepository.findAll()
                } else {
                    listOfNotNull(therapistRepository.findByUserId(userId))
                }

                call.respond(HttpStatusCode.OK, mapOf("data" to therapists.map { it.toResponse() }))
            }

            // GET /v1/therapists/{id} — OWNER sees any, THERAPIST sees own
            get("/{id}") {
                val id = call.pathParam("id")
                val therapist = therapistRepository.findById(UUID.fromString(id))
                    ?: throw AppError.NotFound("Therapist", id)

                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                if (role != UserRole.OWNER.name && therapist.userId != userId) {
                    throw AppError.Forbidden()
                }

                call.respond(HttpStatusCode.OK, mapOf("data" to therapist.toResponse()))
            }

            // POST /v1/therapists — OWNER only
            requireRole(UserRole.OWNER) {
                post {
                    val request = call.receive<CreateTherapistRequest>()
                    val errors = request.validate()
                    if (errors.isNotEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                        return@post
                    }

                    val therapist = therapistRepository.create(
                        Therapist(
                            id = UUID.randomUUID(),
                            userId = UUID.fromString(request.userId),
                            firstName = request.firstName,
                            lastName = request.lastName,
                            commissionRate = BigDecimal.valueOf(request.commissionRate),
                            receivesSupervisionFee = request.receivesSupervisionFee,
                            isActive = true,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    )

                    call.respond(HttpStatusCode.Created, mapOf("data" to therapist.toResponse()))
                }
            }

            // PUT /v1/therapists/{id} — OWNER only
            requireRole(UserRole.OWNER) {
                put("/{id}") {
                    val id = call.pathParam("id")
                    val existing = therapistRepository.findById(UUID.fromString(id))
                        ?: throw AppError.NotFound("Therapist", id)

                    val request = call.receive<UpdateTherapistRequest>()
                    val errors = request.validate()
                    if (errors.isNotEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                        return@put
                    }

                    val updated = therapistRepository.update(
                        existing.copy(
                            firstName = request.firstName,
                            lastName = request.lastName,
                            commissionRate = BigDecimal.valueOf(request.commissionRate),
                            receivesSupervisionFee = request.receivesSupervisionFee
                        )
                    )

                    call.respond(HttpStatusCode.OK, mapOf("data" to updated.toResponse()))
                }
            }

            // DELETE /v1/therapists/{id} — OWNER only, soft delete
            requireRole(UserRole.OWNER) {
                delete("/{id}") {
                    val id = call.pathParam("id")
                    therapistRepository.findById(UUID.fromString(id))
                        ?: throw AppError.NotFound("Therapist", id)

                    therapistRepository.softDelete(UUID.fromString(id))
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Therapist deactivated"))
                }
            }
        }
    }
}

private fun RoutingCall.pathParam(name: String): String =
    pathParameters[name] ?: throw AppError.ValidationFailed("Missing path parameter: $name")

private fun Therapist.toResponse() = TherapistResponse(
    id = id.toString(),
    userId = userId.toString(),
    firstName = firstName,
    lastName = lastName,
    commissionRate = commissionRate.toDouble(),
    receivesSupervisionFee = receivesSupervisionFee,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
