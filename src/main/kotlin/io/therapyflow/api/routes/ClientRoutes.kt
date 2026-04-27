package io.therapyflow.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.therapyflow.api.dto.*
import io.therapyflow.api.extensions.requireRole
import io.therapyflow.data.repository.ClientRepository
import io.therapyflow.data.repository.TherapistRepository
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.Client
import io.therapyflow.domain.model.UserRole
import org.koin.ktor.ext.inject
import java.math.BigDecimal
import java.time.Instant
import java.util.*

fun Route.clientRoutes() {
    val clientRepository by application.inject<ClientRepository>()
    val therapistRepository by application.inject<TherapistRepository>()

    authenticate("jwt") {
        route("/clients") {

            // GET /v1/clients — OWNER sees all, THERAPIST sees own clients
            get {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val clients = if (role == UserRole.OWNER.name) {
                    clientRepository.findAll()
                } else {
                    val therapist = therapistRepository.findByUserId(userId)
                        ?: throw AppError.NotFound("Therapist", userId.toString())
                    clientRepository.findByTherapistId(therapist.id)
                }

                call.respond(HttpStatusCode.OK, mapOf("data" to clients.map { it.toResponse() }))
            }

            // GET /v1/clients/{id}
            get("/{id}") {
                val id = call.clientPathParam("id")
                val client = clientRepository.findById(UUID.fromString(id))
                    ?: throw AppError.NotFound("Client", id)

                ensureClientAccess(call, client, therapistRepository)

                call.respond(HttpStatusCode.OK, mapOf("data" to client.toResponse()))
            }

            // POST /v1/clients — OWNER can assign to any therapist, THERAPIST creates own
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val request = call.receive<CreateClientRequest>()
                val errors = request.validate()
                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                    return@post
                }

                val therapistId = UUID.fromString(request.therapistId)

                // Verify therapist exists
                therapistRepository.findById(therapistId)
                    ?: throw AppError.NotFound("Therapist", request.therapistId)

                // THERAPIST can only create clients for themselves
                if (role == UserRole.THERAPIST.name) {
                    val therapist = therapistRepository.findByUserId(userId)
                    if (therapist == null || therapist.id != therapistId) {
                        throw AppError.Forbidden()
                    }
                }

                val client = clientRepository.create(
                    Client(
                        id = UUID.randomUUID(),
                        therapistId = therapistId,
                        firstName = request.firstName,
                        lastName = request.lastName,
                        googleCalendarName = request.googleCalendarName,
                        customPrice = request.customPrice?.let { BigDecimal.valueOf(it) },
                        isActive = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                )

                call.respond(HttpStatusCode.Created, mapOf("data" to client.toResponse()))
            }

            // PUT /v1/clients/{id} — OWNER or owning THERAPIST
            put("/{id}") {
                val id = call.clientPathParam("id")
                val existing = clientRepository.findById(UUID.fromString(id))
                    ?: throw AppError.NotFound("Client", id)

                ensureClientAccess(call, existing, therapistRepository)

                val request = call.receive<UpdateClientRequest>()
                val errors = request.validate()
                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
                    return@put
                }

                val updated = clientRepository.update(
                    existing.copy(
                        firstName = request.firstName,
                        lastName = request.lastName,
                        googleCalendarName = request.googleCalendarName,
                        customPrice = request.customPrice?.let { BigDecimal.valueOf(it) }
                    )
                )

                call.respond(HttpStatusCode.OK, mapOf("data" to updated.toResponse()))
            }

            // DELETE /v1/clients/{id} — OWNER only, soft delete
            requireRole(UserRole.OWNER) {
                delete("/{id}") {
                    val id = call.clientPathParam("id")
                    clientRepository.findById(UUID.fromString(id))
                        ?: throw AppError.NotFound("Client", id)

                    clientRepository.softDelete(UUID.fromString(id))
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Client deactivated"))
                }
            }
        }
    }
}

/**
 * OWNER can access any client. THERAPIST can only access their own clients.
 */
private suspend fun ensureClientAccess(
    call: RoutingCall,
    client: Client,
    therapistRepository: TherapistRepository
) {
    val principal = call.principal<JWTPrincipal>()!!
    val role = principal.payload.getClaim("role").asString()
    if (role == UserRole.OWNER.name) return

    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
    val therapist = therapistRepository.findByUserId(userId)
    if (therapist == null || therapist.id != client.therapistId) {
        throw AppError.Forbidden()
    }
}

private fun RoutingCall.clientPathParam(name: String): String =
    pathParameters[name] ?: throw AppError.ValidationFailed("Missing path parameter: $name")

private fun Client.toResponse() = ClientResponse(
    id = id.toString(),
    therapistId = therapistId.toString(),
    firstName = firstName,
    lastName = lastName,
    googleCalendarName = googleCalendarName,
    customPrice = customPrice?.toDouble(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
