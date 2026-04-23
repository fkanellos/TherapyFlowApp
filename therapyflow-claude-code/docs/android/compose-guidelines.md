# Compose UI Guidelines
# Based on: Google MAD Skills — Jetpack Compose
# Reference: https://developer.android.com/jetpack/compose/documentation

## Core Principles

### 1. State Hoisting
State lives in ViewModel. Composables are stateless — they only receive state and emit events.

```kotlin
// ✅ CORRECT — stateless composable
@Composable
fun AppointmentCard(
    appointment: AppointmentUi,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        // render appointment
        Button(onClick = onComplete) { Text("Complete") }
        Button(onClick = onCancel) { Text("Cancel") }
    }
}

// ✅ CORRECT — Screen collects state, passes down
@Composable
fun TherapistHomeScreen(
    viewModel: TherapistHomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TherapistHomeEffect.NavigateToAddAppointment -> { /* navigate */ }
                is TherapistHomeEffect.ShowError -> { /* show snackbar */ }
            }
        }
    }
    
    TherapistHomeContent(
        state = state,
        onIntent = viewModel::handleIntent
    )
}

// ✅ CORRECT — content is pure, testable
@Composable
fun TherapistHomeContent(
    state: TherapistHomeState,
    onIntent: (TherapistHomeIntent) -> Unit
) {
    // pure rendering of state
}

// ❌ WRONG — state inside composable
@Composable
fun AppointmentCard() {
    var isExpanded by remember { mutableStateOf(false) }  // only if truly local UI state
    val appointments = remember { mutableListOf<Appointment>() }  // NEVER business data here
}
```

### 2. Stability & Recomposition

Unstable types cause unnecessary recompositions (performance issue).

```kotlin
// ✅ CORRECT — immutable data class (stable)
data class AppointmentUi(
    val id: String,
    val clientName: String,
    val timeDisplay: String,
    val price: String,           // pre-formatted: "€80.00"
    val statusColor: Color,
    val isCompleted: Boolean
)

// ❌ WRONG — mutable or unstable types in UI model
data class AppointmentUi(
    val appointment: Appointment,  // domain object — unstable
    val date: Date,                // Date is unstable
    val items: MutableList<Item>   // mutable — unstable
)
```

**Rule:** Map domain models to UI models before passing to Compose.
Pre-format all strings and values in the ViewModel/Mapper.

### 3. Side Effects

```kotlin
// LaunchedEffect — run once or when key changes
LaunchedEffect(Unit) {
    viewModel.handleIntent(TherapistHomeIntent.LoadAppointments)
}

// LaunchedEffect for collecting effects from ViewModel
LaunchedEffect(Unit) {
    viewModel.effect.collect { effect ->
        when (effect) {
            is TherapistHomeEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is TherapistHomeEffect.NavigateToAdd -> navController.navigate(...)
        }
    }
}

// DisposableEffect — cleanup when leaving composition
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { ... }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

### 4. Performance Rules

```kotlin
// ✅ Use keys in LazyColumn for stable identity
LazyColumn {
    items(
        items = state.appointments,
        key = { it.id }  // ALWAYS provide a stable key
    ) { appointment ->
        AppointmentCard(appointment = appointment)
    }
}

// ✅ Use derivedStateOf for derived values
val hasAppointments by remember {
    derivedStateOf { state.appointments.isNotEmpty() }
}

// ❌ Don't calculate in composition
// BAD:
val total = state.appointments.sumOf { it.price }  // recalculates on every recomposition

// GOOD: calculate in ViewModel, pass as part of State
data class TherapistHomeState(
    val appointments: List<AppointmentUi> = emptyList(),
    val todayTotal: String = "€0.00"  // pre-calculated in ViewModel
)
```

## TherapyFlow Design System

### Colors
```kotlin
object TherapyFlowColors {
    // Therapist role
    val therapistPrimary = Color(0xFF1976D2)
    val therapistPrimaryContainer = Color(0xFFE3F2FD)
    
    // Admin/Owner role
    val adminPrimary = Color(0xFF673AB7)
    val adminPrimaryContainer = Color(0xFFF3E5F5)
    
    // Appointment status
    val completed = Color(0xFF2E7D32)
    val cancelledLate = Color(0xFFC62828)    // RED — must pay
    val cancelledEarly = Color(0xFF9E9E9E)   // GREY — no charge
    val scheduled = Color(0xFF1976D2)
    val pendingCharge = Color(0xFFE65100)    // ORANGE — pending late fee
}
```

### Typography
```kotlin
// Use MaterialTheme typography — don't define custom sizes everywhere
MaterialTheme.typography.titleLarge   // screen titles
MaterialTheme.typography.bodyLarge    // main content
MaterialTheme.typography.bodyMedium   // secondary content
MaterialTheme.typography.labelSmall   // tags, chips
```

### Appointment Card States (matches existing Google Calendar colors)
```kotlin
@Composable
fun AppointmentStatusIndicator(status: AppointmentStatus) {
    val (color, label) = when (status) {
        COMPLETED         → TherapyFlowColors.completed to "Ολοκληρώθηκε"
        CANCELLED_LATE    → TherapyFlowColors.cancelledLate to "Ακύρωση (χρέωση)"
        CANCELLED_EARLY   → TherapyFlowColors.cancelledEarly to "Ακύρωση"
        SCHEDULED         → TherapyFlowColors.scheduled to "Προγραμματισμένο"
        NO_SHOW           → TherapyFlowColors.cancelledLate to "Δεν ήρθε (χρέωση)"
    }
    // render indicator
}
```

## Composable Naming Conventions

```
TherapistHomeScreen   → top-level screen (has ViewModel)
TherapistHomeContent  → pure content (no ViewModel, testable)
AppointmentCard       → reusable component
AppointmentStatusChip → small reusable piece
```

## Previews

Every composable should have a `@Preview`:

```kotlin
@Preview(showBackground = true)
@Composable
fun AppointmentCardPreview() {
    TherapyFlowTheme {
        AppointmentCard(
            appointment = AppointmentUi(
                id = "1",
                clientName = "Μαρία Παπαδοπούλου",
                timeDisplay = "10:00 - 11:00",
                price = "€80.00",
                statusColor = TherapyFlowColors.completed,
                isCompleted = true
            ),
            onComplete = {},
            onCancel = {}
        )
    }
}
```
