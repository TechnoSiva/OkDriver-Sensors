package com.okdriver.sensors.data.model

import java.util.UUID

data class DrivingEvent(
    val id: UUID = UUID.randomUUID(),
    val type: DrivingEventType,
    val severity: Float,
    val timestamp: Long,
    val speedKmh: Float? = null
)

enum class DrivingEventType {
    HARSH_ACCEL,
    HARSH_BRAKE,
    SHARP_TURN
}
