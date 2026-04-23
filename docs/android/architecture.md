# Android Architecture Guidelines
# Based on: Google MAD Skills — App Architecture
# Reference: https://developer.android.com/topic/architecture

## Pattern: MVI (Model-View-Intent)

TherapyFlow uses MVI — not MVVM. The distinction matters:
- **MVVM:** ViewModel exposes mutable LiveData/StateFlow, View observes
- **MVI:** ViewModel exposes a single immutable `State`, receives `Intent` (user actions), emits one-time `Effect`

```kotlin
// Every screen follows this structure:

// 1. State — immutable snapshot of the entire screen
data class TherapistHomeState(
    val isLoading: Boolean = false,
    val appointments: List<AppointmentUi> = emptyList(),
    val todayDate: String = "",
    val error: String? = null
)

// 2. Intent — what the user did
sealed class TherapistHomeIntent {
    object LoadAppointments : TherapistHomeIntent()
    data class CompleteAppointment(val id: String) : TherapistHomeIntent()
    object AddAppointmentClicked : TherapistHomeIntent()
    data class CancelAppointment(val id: String, val isLate: Boolean) : TherapistHomeIntent()
}

// 3. Effect — one-time events (navigation, toast, dialog)
sealed class TherapistHomeEffect {
    data class NavigateToAddAppointment(val therapistId: String) : TherapistHomeEffect()
    data class ShowError(val message: String) : TherapistHomeEffect()
    object ShowLateCancel  lationDialog : TherapistHomeEffect()
}

// 4. ViewModel — connects everything
class TherapistHomeViewModel(
    private val getAppointmentsUseCase: GetTodayAppointmentsUseCase,
    private val completeAppointmentUseCase: CompleteAppointmentUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TherapistHomeState())
    val state: StateFlow<TherapistHomeState> = _state.asStateFlow()

    private val _effect = Channel<TherapistHomeEffect>()
    val effect: Flow<TherapistHomeEffect> = _effect.receiveAsFlow()

    fun handleIntent(intent: TherapistHomeIntent) {
        when (intent) {
            is TherapistHomeIntent.LoadAppointments -> loadAppointments()
            is TherapistHomeIntent.CompleteAppointment -> completeAppointment(intent.id)
            is TherapistHomeIntent.AddAppointmentClicked -> {
                viewModelScope.launch {
                    _effect.send(TherapistHomeEffect.NavigateToAddAppointment(...))
                }
            }
        }
    }

    private fun loadAppointments() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getAppointmentsUseCase()
                .onSuccess { appointments ->
                    _state.update { it.copy(isLoading = false, appointments = appointments) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(TherapistHomeEffect.ShowError(error.message ?: "Unknown error"))
                }
        }
    }
}
```

## Layered Architecture

```
UI Layer
  ↑ observes State, sends Intent
  Screen.kt (Composable) — dumb, only renders state
  ViewModel.kt — smart, handles business coordination

Domain Layer (shared with backend via KMP)
  UseCase.kt — single responsibility
  Repository (interface) — data abstraction
  Model.kt — domain entities

Data Layer
  RepositoryImpl.kt — implements domain interface
  RemoteDataSource.kt — Ktor API calls
  LocalDataSource.kt — SQLDelight cache
  Mapper.kt — DTO ↔ Domain model
```

## Unidirectional Data Flow (UDF)

```
User Action → Intent → ViewModel → UseCase → Repository
                                              ↓
                                         Data Source
                                              ↓
Screen ← State update ← ViewModel ← Result/Flow
```

**Rule:** Data flows in ONE direction. Never update UI directly from a callback.

## Use Cases

One use case = one thing. Name them as verbs:

```kotlin
class GetTodayAppointmentsUseCase(private val repo: AppointmentRepository) {
    suspend operator fun invoke(): Result<List<Appointment>> =
        repo.getTodayAppointments()
}

class CompleteAppointmentUseCase(
    private val repo: AppointmentRepository,
    private val payrollRepo: PayrollRepository
) {
    suspend operator fun invoke(appointmentId: String): Result<Unit> {
        return repo.markCompleted(appointmentId)
    }
}

class CancelAppointmentUseCase(private val repo: AppointmentRepository) {
    suspend operator fun invoke(
        appointmentId: String,
        isLateCancellation: Boolean
    ): Result<Unit> {
        val status = if (isLateCancellation) 
            AppointmentStatus.CANCELLED_LATE 
        else 
            AppointmentStatus.CANCELLED_EARLY
        return repo.cancel(appointmentId, status)
    }
}
```

## DI with Koin

```kotlin
val domainModule = module {
    factory { GetTodayAppointmentsUseCase(get()) }
    factory { CompleteAppointmentUseCase(get(), get()) }
    factory { CancelAppointmentUseCase(get()) }
}

val viewModelModule = module {
    viewModel { TherapistHomeViewModel(get(), get()) }
    viewModel { AdminDashboardViewModel(get(), get()) }
}
```

## Navigation

Use Compose Navigation with typed routes:

```kotlin
sealed class Screen(val route: String) {
    object TherapistHome : Screen("therapist_home")
    object AddAppointment : Screen("add_appointment/{therapistId}") {
        fun createRoute(therapistId: String) = "add_appointment/$therapistId"
    }
    object AdminDashboard : Screen("admin_dashboard")
    object PayrollDetail : Screen("payroll/{periodId}") {
        fun createRoute(periodId: String) = "payroll/$periodId"
    }
}
```

## Rule Checklist for Every Screen

- [ ] Single `State` data class — no multiple LiveData/StateFlows
- [ ] `Intent` sealed class for all user actions
- [ ] `Effect` channel for one-time events (never navigation in State)
- [ ] ViewModel does NOT import Android framework (testable)
- [ ] No business logic in Composables
- [ ] No direct API calls in ViewModel — always through UseCase
