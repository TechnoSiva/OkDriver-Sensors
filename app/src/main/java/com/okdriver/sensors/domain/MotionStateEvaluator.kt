package com.okdriver.sensors.domain

import com.okdriver.sensors.data.model.LocationSnapshot
import com.okdriver.sensors.data.model.SensorSnapshot
import kotlin.math.sqrt

class MotionStateEvaluator {
    private val gravity = FloatArray(3) { 0f }
    private val windowSamples = ArrayDeque<Pair<Long, Float>>()

    private var currentState = MotionState.UNKNOWN
    private var candidateState = MotionState.UNKNOWN
    private var candidateSinceMs = 0L

    fun reset() {
        gravity[0] = 0f
        gravity[1] = 0f
        gravity[2] = 0f
        windowSamples.clear()
        currentState = MotionState.UNKNOWN
        candidateState = MotionState.UNKNOWN
        candidateSinceMs = 0L
    }

    fun update(
        sensorSnapshot: SensorSnapshot,
        locationSnapshot: LocationSnapshot?
    ): MotionState {
        val timestampMs = sensorSnapshot.timestamp
        val linearMagnitude = computeLinearAccelerationMagnitude(sensorSnapshot)
        addSample(timestampMs, linearMagnitude)

        if (windowSamples.size < ThresholdConfig.MIN_WINDOW_SAMPLE_COUNT) {
            return currentState
        }

        val mean = windowSamples.map { it.second }.average().toFloat()
        val variance = windowSamples
            .map { sample -> (sample.second - mean) * (sample.second - mean) }
            .average()
            .toFloat()

        val speedKmh = locationSnapshot?.speedMps?.times(3.6f)
        val lowSpeed = speedKmh?.let { it < ThresholdConfig.STATIONARY_SPEED_THRESHOLD_KMH } ?: false
        val highSpeed = speedKmh?.let { it >= ThresholdConfig.MOVING_SPEED_THRESHOLD_KMH } ?: false

        val lowAcceleration = mean <= ThresholdConfig.STATIONARY_LINEAR_ACCEL_MEAN_THRESHOLD_MS2 &&
            variance <= ThresholdConfig.STATIONARY_LINEAR_ACCEL_VARIANCE_THRESHOLD
        val highAcceleration = mean >= ThresholdConfig.MOVING_LINEAR_ACCEL_MEAN_THRESHOLD_MS2 ||
            variance >= ThresholdConfig.MOVING_LINEAR_ACCEL_VARIANCE_THRESHOLD

        val nextCandidate = when {
            highSpeed || highAcceleration -> MotionState.MOVING
            lowAcceleration && (lowSpeed || speedKmh == null) -> MotionState.STATIONARY
            else -> currentState
        }

        if (nextCandidate != candidateState) {
            candidateState = nextCandidate
            candidateSinceMs = timestampMs
        }

        val holdMs = when (candidateState) {
            MotionState.STATIONARY -> ThresholdConfig.STATIONARY_HOLD_MS
            MotionState.MOVING -> ThresholdConfig.MOVING_HOLD_MS
            MotionState.UNKNOWN -> 0L
        }

        if (candidateState != currentState &&
            timestampMs - candidateSinceMs >= holdMs
        ) {
            currentState = candidateState
        }

        return currentState
    }

    private fun computeLinearAccelerationMagnitude(sensorSnapshot: SensorSnapshot): Float {
        gravity[0] = ThresholdConfig.GRAVITY_ALPHA * gravity[0] +
            (1f - ThresholdConfig.GRAVITY_ALPHA) * sensorSnapshot.accelX
        gravity[1] = ThresholdConfig.GRAVITY_ALPHA * gravity[1] +
            (1f - ThresholdConfig.GRAVITY_ALPHA) * sensorSnapshot.accelY
        gravity[2] = ThresholdConfig.GRAVITY_ALPHA * gravity[2] +
            (1f - ThresholdConfig.GRAVITY_ALPHA) * sensorSnapshot.accelZ

        val linearX = sensorSnapshot.accelX - gravity[0]
        val linearY = sensorSnapshot.accelY - gravity[1]
        val linearZ = sensorSnapshot.accelZ - gravity[2]

        return sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
    }

    private fun addSample(timestampMs: Long, linearMagnitude: Float) {
        windowSamples.addLast(timestampMs to linearMagnitude)
        val minTimestamp = timestampMs - ThresholdConfig.STATIONARY_WINDOW_MS
        while (windowSamples.isNotEmpty() && windowSamples.first().first < minTimestamp) {
            windowSamples.removeFirst()
        }
    }
}
