package com.okdriver.sensors.data.sensors

import com.okdriver.sensors.data.model.SensorSnapshot
import kotlinx.coroutines.flow.Flow

interface SensorDataSource {
    val sensorAvailability: SensorAvailability

    fun sensorSnapshots(): Flow<SensorSnapshot>

    fun setSamplingMode(mode: SensorSamplingMode)

    fun start()

    fun stop()
}

data class SensorAvailability(
    val accelerometerAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val magnetometerAvailable: Boolean = false
) {
    val allRequiredSensorsAvailable: Boolean
        get() = accelerometerAvailable && gyroscopeAvailable && magnetometerAvailable
}

enum class SensorSamplingMode {
    MOVING,
    STATIONARY
}
