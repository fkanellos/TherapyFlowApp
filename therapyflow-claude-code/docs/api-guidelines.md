# API Guidelines

## Base URL
```
https://api.therapyflow.io/v1
```

## Authentication

All endpoints (except /auth/*) require:
```
Authorization: Bearer <access_token>
```

JWT payload contains:
```json
{
  "userId": "uuid",
  "workspaceId": "uuid", 
  "role": "OWNER | THERAPIST | ADMIN_STAFF",
  "exp": 1234567890
}
```

## Role-Based Access

```kotlin
// In routes — use role guards:
authenticate("jwt") {
    route("/payroll") {
        requireRole(UserRole.OWNER) {
            get { /* only owners */ }
            post("/calculate") { /* only owners */ }
        }
    }
    route("/appointments") {
        // Both roles, but data is filtered by role in service layer
        get { /* OWNER sees all, THERAPIST sees own */ }
        post { /* both can create */ }
    }
}
```

## Response Format

### Success
```json
{
  "data": { ... },
  "meta": { "page": 1, "total": 42 }  // for lists
}
```

### Error
```json
{
  "error": "APPOINTMENT_NOT_FOUND",
  "message": "Human readable message",
  "status": 404
}
```

### Standard HTTP Status Codes
- `200` OK
- `201` Created
- `400` Bad Request (validation error)
- `401` Unauthorized (missing/invalid token)
- `403` Forbidden (valid token, wrong role/feature)
- `404` Not Found
- `409` Conflict (duplicate, already exists)
- `422` Unprocessable (business logic error)
- `500` Internal Server Error

## Naming Conventions

```
GET    /appointments           → list
GET    /appointments/{id}      → get one
POST   /appointments           → create
PUT    /appointments/{id}      → update (full)
PATCH  /appointments/{id}      → update (partial)
DELETE /appointments/{id}      → soft delete
POST   /appointments/sync      → action (verb after resource)
GET    /payroll/{id}/export    → sub-resource
```

## Pagination

All list endpoints support:
```
GET /appointments?page=1&pageSize=20&sortBy=startTime&sortOrder=desc
```

Response includes:
```json
{
  "data": [...],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

## DTO Conventions

```kotlin
// Request DTOs — validate eagerly
@Serializable
data class CreateAppointmentRequest(
    val therapistId: String,
    val clientId: String,
    val startTime: String,          // ISO 8601
    val durationMinutes: Int,
    val price: Double,
    val sessionType: String,
    val notes: String? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (durationMinutes <= 0) errors.add("Duration must be positive")
        if (price < 0) errors.add("Price cannot be negative")
        return errors
    }
}

// Response DTOs — never expose internal IDs or sensitive fields
@Serializable
data class AppointmentResponse(
    val id: String,
    val therapistId: String,
    val clientName: String,         // denormalized for convenience
    val startTime: String,
    val durationMinutes: Int,
    val price: Double,
    val status: String,
    val source: String
)
```

## Input Validation

Always validate in the route handler before calling service:
```kotlin
post("/appointments") {
    val request = call.receive<CreateAppointmentRequest>()
    val errors = request.validate()
    if (errors.isNotEmpty()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("errors" to errors))
        return@post
    }
    val result = appointmentService.create(request)
    call.respond(HttpStatusCode.Created, result)
}
```

## Versioning

Current: `v1`. Breaking changes → new version `v2`.
Old versions supported for 6 months after deprecation notice.
