package com.okdriver.sensors.domain

import com.okdriver.sensors.data.model.DrivingEvent
import com.okdriver.sensors.data.model.DrivingEventType
import com.okdriver.sensors.data.model.LocationSnapshot
import com.okdriver.sensors.data.model.SensorSnapshot

class DrivingEventDetector {
    private val gravity = FloatArray(3) { 0f }
    private val thresholdStartTimes = mutableMapOf<DrivingEventType, Long>()
    private val lastEventTimes = mutableMapOf<DrivingEventType, Long>()

    fun detect(
        sensorSnapshot: SensorSnapshot,
        locationSnapshot: LocationSnapshot?
    ): List<DrivingEvent> {
        val events = mutableListOf<DrivingEvent>()

        gravity[0] = ThresholdConfig.GRAVITY_ALPHA * gravity[0] +
            (1f - ThresholdConfig.GRAVITY_ALPHA) * sensorSnapshot.accelX
        gravity[1] = ThresholdConfig.GRAVITY_ALPHA * gravity[1] +
            (1f - ThresholdConfig.GRAVITY_ALPHA) * sensorSnapshot.accelY
        gravity[2] = ThresholdConfig.GRAVITY_ALPHA * gravity[2] +
            (1f - ThresholdConfig.GRAVITY_ALPHA) * sensorSnapshot.accelZ

        val linearX = sensorSnapshot.accelX - gravity[0]
        val linearY = sensorSnapshot.accelY - gravity[1]
        val linearZ = sensorSnapshot.accelZ - gravity[2]
        val linearMagnitude = kotlin.math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        val timestamp = sensorSnapshot.timestamp
        val speedKmh = locationSnapshot?.speedMps?.times(3.6f)

        if (linearMagnitude > ThresholdConfig.HARSH_ACCEL_THRESHOLD_MS2) {
            maybeCreateEvent(
                type = DrivingEventType.HARSH_ACCEL,
                measuredValue = linearMagnitude,
                thresholdValue = ThresholdConfig.HARSH_ACCEL_THRESHOLD_MS2,
                timestamp = timestamp,
                speedKmh = speedKmh
            )?.let(events::add)
        } else {
            thresholdStartTimes.remove(DrivingEventType.HARSH_ACCEL)
        }

        if (linearY < ThresholdConfig.HARSH_BRAKE_THRESHOLD_MS2) {
            maybeCreateEvent(
                type = DrivingEventType.HARSH_BRAKE,
                measuredValue = kotlin.math.abs(linearY),
                thresholdValue = kotlin.math.abs(ThresholdConfig.HARSH_BRAKE_THRESHOLD_MS2),
                timestamp = timestamp,
                speedKmh = speedKmh
            )?.let(events::add)
        } else {
            thresholdStartTimes.remove(DrivingEventType.HARSH_BRAKE)
        }

        val gyroZAbs = kotlin.math.abs(sensorSnapshot.gyroZ)
        if (gyroZAbs > ThresholdConfig.SHARP_TURN_GYRO_Z_THRESHOLD_RAD_S) {
            maybeCreateEvent(
                type = DrivingEventType.SHARP_TURN,
                measuredValue = gyroZAbs,
                thresholdValue = ThresholdConfig.SHARP_TURN_GYRO_Z_THRESHOLD_RAD_S,
                timestamp = timestamp,
                speedKmh = speedKmh
            )?.let(events::add)
        } else {
            thresholdStartTimes.remove(DrivingEventType.SHARP_TURN)
        }

        return events
    }

    private fun maybeCreateEvent(
        type: DrivingEventType,
        measuredValue: Float,
        thresholdValue: Float,
        timestamp: Long,
        speedKmh: Float?
    ): DrivingEvent? {
        val startTime = thresholdStartTimes.getOrPut(type) { timestamp }
        val sustained = timestamp - startTime >= ThresholdConfig.SUSTAIN_DURATION_MS
        if (!sustained) {
            return null
        }

        val lastTime = lastEventTimes[type] ?: 0L
        val isDebounced = timestamp - lastTime >= ThresholdConfig.EVENT_DEBOUNCE_MS
        if (!isDebounced) {
            return null
        }

        lastEventTimes[type] = timestamp
        thresholdStartTimes[type] = timestamp

        val severity = (measuredValue / thresholdValue)
            .coerceAtLeast(1f)
            .coerceAtMost(ThresholdConfig.MAX_SEVERITY)

        return DrivingEvent(
            type = type,
            severity = severity,
            timestamp = timestamp,
            speedKmh = speedKmh
        )
    }
}
