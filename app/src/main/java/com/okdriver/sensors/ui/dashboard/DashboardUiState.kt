package com.okdriver.sensors.ui.dashboard

import com.okdriver.sensors.data.model.LocationSnapshot
import com.okdriver.sensors.data.model.SensorSnapshot
import com.okdriver.sensors.data.sensors.SensorSamplingMode
import com.okdriver.sensors.domain.MotionState
import com.okdriver.sensors.data.sensors.SensorAvailability

data class DashboardUiState(
    val isMonitoring: Boolean = false,
    val statusText: String = "OFF",
    val elapsedText: String = "00:00",
    val sensorSnapshot: SensorSnapshot = SensorSnapshot(),
    val sensorAvailability: SensorAvailability = SensorAvailability(),
    val locationSnapshot: LocationSnapshot? = null,
    val gpsStatus: GpsStatus = GpsStatus.UNAVAILABLE,
    val motionState: MotionState = MotionState.UNKNOWN,
    val sensorSamplingMode: SensorSamplingMode = SensorSamplingMode.MOVING,
    val gpsIntervalMs: Long = 1500L,
    val optimizationStatus: String = "Optimizations inactive"
)
