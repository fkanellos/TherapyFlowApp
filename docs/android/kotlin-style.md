# Kotlin Style Guide
# Based on: Google Kotlin Style Guide + Android Kotlin conventions
# Reference: https://developer.android.com/kotlin/style-guide

## Coroutines & Flow

### Repository returns Flow or Result — never throws
```kotlin
// ✅ CORRECT
interface AppointmentRepository {
    fun getTodayAppointments(): Flow<List<Appointment>>
    suspend fun create(appointment: Appointment): Result<Appointment>
    suspend fun cancel(id: String, status: AppointmentStatus): Result<Unit>
}

// ❌ WRONG — throwing from suspend fun forces try-catch everywhere
suspend fun create(appointment: Appointment): Appointment  // throws on error
```

### Use Result<T> for one-shot operations
```kotlin
// In ViewModel:
viewModelScope.launch {
    createAppointmentUseCase(appointment)
        .onSuccess { created ->
            _state.update { it.copy(appointments = it.appointments + created.toUi()) }
        }
        .onFailure { error ->
            _effect.send(Effect.ShowError(error.message ?: "Σφάλμα αποθήκευσης"))
        }
}
```

### Dispatchers
```kotlin
// Don't hardcode — inject for testability
class AppointmentRepository(
    private val api: AppointmentApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getAppointments(): Result<List<Appointment>> =
        withContext(ioDispatcher) {
            runCatching { api.getAppointments().map { it.toDomain() } }
        }
}
```

### Flow collection — use lifecycle-aware collection
```kotlin
// In Composable — always use collectAsStateWithLifecycle (not collectAsState)
val state by viewModel.state.collectAsStateWithLifecycle()
```

## Naming Conventions

```kotlin
// Files
TherapistHomeScreen.kt     // Composable screen
TherapistHomeViewModel.kt  // ViewModel
AppointmentRepository.kt   // Interface
AppointmentRepositoryImpl.kt // Implementation

// Classes
data class AppointmentUi(...)      // UI model suffix: Ui
data class AppointmentDto(...)     // API model suffix: Dto
data class Appointment(...)        // Domain model: no suffix

// Extension functions for mapping
fun AppointmentDto.toDomain(): Appointment
fun Appointment.toUi(): AppointmentUi
fun CreateAppointmentRequest.toDomain(): Appointment

// Boolean functions start with is/has/can
val isCompleted: Boolean
val hasPermission: Boolean
fun canCancel(appointment: Appointment): Boolean

// Greek text in UI — use string resources
// NEVER hardcode Greek strings in Kotlin code
// Use strings.xml or a strings object
```

## Sealed Classes for State/Events
```kotlin
// ✅ Use sealed class/interface for exhaustive when
sealed interface AppointmentStatus {
    object Scheduled : AppointmentStatus
    object Completed : AppointmentStatus
    data class Cancelled(val isLate: Boolean) : AppointmentStatus
    object NoShow : AppointmentStatus
}

// Then when() is exhaustive — compiler catches missing cases
val isChargeable = when (status) {
    is AppointmentStatus.Scheduled  -> false
    is AppointmentStatus.Completed  -> false
    is AppointmentStatus.Cancelled  -> status.isLate  // late = chargeable
    is AppointmentStatus.NoShow     -> true
}
```

## Extension Functions — Keep Focused
```kotlin
// ✅ Domain mapping
fun AppointmentDto.toDomain() = Appointment(
    id = id,
    clientId = clientId,
    price = BigDecimal(price),
    status = AppointmentStatus.valueOf(status),
    startTime = Instant.parse(startTime)
)

// ✅ UI formatting
fun Appointment.toTimeDisplay(): String =
    "${startTime.toLocalTime()} - ${startTime.plus(duration).toLocalTime()}"

fun BigDecimal.toEuroDisplay(): String = "€${this.setScale(2, HALF_UP)}"

// ❌ Don't put business logic in extension functions
// Business logic → UseCase
fun Appointment.calculateCommission(): BigDecimal  // WRONG place
```

## Null Safety — Fail Fast
```kotlin
// ✅ Require non-null in constructors — validate at the boundary
data class Appointment(
    val id: String,
    val price: BigDecimal,
    val status: AppointmentStatus
) {
    init {
        require(price >= BigDecimal.ZERO) { "Price cannot be negative" }
        require(id.isNotBlank()) { "ID cannot be blank" }
    }
}

// ✅ Use requireNotNull for debug assertions
val therapist = requireNotNull(state.therapist) { "Therapist must be set before creating appointment" }

// ✅ Use ?: for defaults
val displayName = client.preferredName ?: client.firstName
```

## Constants & Magic Numbers
```kotlin
// ✅ Named constants
object PayrollConstants {
    const val LATE_CANCELLATION_THRESHOLD_HOURS = 48  // 2 days
    const val MAX_PENDING_CHARGES_PER_CLIENT = 3
    const val SUPERVISION_SESSION_TYPE = "SUPERVISION"
}

// ❌ Magic numbers
if (hoursBeforeAppointment < 48) { ... }  // What is 48??
```

## Do NOT
- `!!` operator outside of `init` or tests
- `runBlocking` in production code
- `GlobalScope` — always use `viewModelScope` or inject scope
- `Thread.sleep` — use `delay()`
- Mutable collections in state — always copy with `+`, `.copy()`, etc.
