package com.okdriver.sensors.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.okdriver.sensors.data.model.SensorSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

class AndroidSensorDataSource(
    context: Context
) : SensorDataSource {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val monitoringState = MutableStateFlow(false)
    private val samplingModeState = MutableStateFlow(SensorSamplingMode.MOVING)
    private var latestSnapshot: SensorSnapshot = SensorSnapshot()

    override val sensorAvailability: SensorAvailability = SensorAvailability(
        accelerometerAvailable = accelerometer != null,
        gyroscopeAvailable = gyroscope != null,
        magnetometerAvailable = magnetometer != null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun sensorSnapshots(): Flow<SensorSnapshot> {
        return combine(monitoringState, samplingModeState) { isMonitoring, samplingMode ->
            if (isMonitoring) samplingMode else null
        }.flatMapLatest { samplingMode ->
                if (samplingMode == null) {
                    emptyFlow()
                } else {
                    callbackFlow {
                        val sensorThread = HandlerThread("SensorStreamThread").apply { start() }
                        val sensorHandler = Handler(sensorThread.looper)
                        val samplingDelay = samplingDelayForMode(samplingMode)
                        val shouldUseGyroscope = samplingMode == SensorSamplingMode.MOVING

                        val listener = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent) {
                                val values = event.values
                                latestSnapshot = when (event.sensor.type) {
                                    Sensor.TYPE_ACCELEROMETER -> latestSnapshot.copy(
                                        accelX = values[0],
                                        accelY = values[1],
                                        accelZ = values[2],
                                        timestamp = System.currentTimeMillis()
                                    )
                                    Sensor.TYPE_GYROSCOPE -> latestSnapshot.copy(
                                        gyroX = values[0],
                                        gyroY = values[1],
                                        gyroZ = values[2],
                                        timestamp = System.currentTimeMillis()
                                    )
                                    Sensor.TYPE_MAGNETIC_FIELD -> latestSnapshot.copy(
                                        magX = values[0],
                                        magY = values[1],
                                        magZ = values[2],
                                        timestamp = System.currentTimeMillis()
                                    )
                                    else -> latestSnapshot
                                }
                                trySend(latestSnapshot)
                            }

                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                        }

                        accelerometer?.let {
                            sensorManager.registerListener(listener, it, samplingDelay, sensorHandler)
                        }
                        if (shouldUseGyroscope) {
                            gyroscope?.let {
                                sensorManager.registerListener(listener, it, samplingDelay, sensorHandler)
                            }
                        } else {
                            latestSnapshot = latestSnapshot.copy(
                                gyroX = 0f,
                                gyroY = 0f,
                                gyroZ = 0f
                            )
                        }
                        magnetometer?.let {
                            sensorManager.registerListener(listener, it, samplingDelay, sensorHandler)
                        }

                        trySend(latestSnapshot)

                        awaitClose {
                            sensorManager.unregisterListener(listener)
                            sensorThread.quitSafely()
                        }
                    }
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override fun setSamplingMode(mode: SensorSamplingMode) {
        samplingModeState.value = mode
    }

    override fun start() {
        monitoringState.value = true
    }

    override fun stop() {
        monitoringState.value = false
    }

    private fun samplingDelayForMode(mode: SensorSamplingMode): Int {
        return when (mode) {
            SensorSamplingMode.MOVING -> SensorManager.SENSOR_DELAY_GAME
            SensorSamplingMode.STATIONARY -> SensorManager.SENSOR_DELAY_UI
        }
    }
}
