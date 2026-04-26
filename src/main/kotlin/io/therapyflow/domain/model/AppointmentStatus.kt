package io.therapyflow.domain.model

enum class AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED_EARLY,
    CANCELLED_LATE,
    NO_SHOW
}
