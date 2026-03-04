package com.okdriver.sensors.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.okdriver.sensors.data.model.LocationSnapshot
import com.okdriver.sensors.domain.ThresholdConfig
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

class AndroidLocationDataSource(
    context: Context
) : LocationDataSource {

    private val fusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val monitoringState = MutableStateFlow(false)
    private val locationModeState = MutableStateFlow(LocationUpdateMode.MOVING)

    override fun isLocationProviderEnabled(): Boolean {
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        return gpsEnabled || networkEnabled
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override fun locationSnapshots(): Flow<LocationSnapshot> {
        return combine(monitoringState, locationModeState) { isMonitoring, locationMode ->
            if (isMonitoring) locationMode else null
        }.flatMapLatest { locationMode ->
                if (locationMode == null) {
                    emptyFlow()
                } else {
                    callbackFlow {
                        val request = buildLocationRequest(locationMode)
                        val callback = object : LocationCallback() {
                            override fun onLocationResult(locationResult: LocationResult) {
                                val location = locationResult.lastLocation ?: return
                                trySend(
                                    LocationSnapshot(
                                        lat = location.latitude,
                                        lon = location.longitude,
                                        speedMps = location.speed,
                                        accuracyM = location.accuracy,
                                        bearingDeg = if (location.hasBearing()) location.bearing else null,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        }

                        try {
                            fusedLocationProviderClient.requestLocationUpdates(
                                request,
                                callback,
                                Looper.getMainLooper()
                            )
                        } catch (_: SecurityException) {
                            close()
                            return@callbackFlow
                        }

                        awaitClose {
                            fusedLocationProviderClient.removeLocationUpdates(callback)
                        }
                    }
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override fun setLocationMode(mode: LocationUpdateMode) {
        locationModeState.value = mode
    }

    override fun start() {
        monitoringState.value = true
    }

    override fun stop() {
        monitoringState.value = false
    }

    private fun buildLocationRequest(mode: LocationUpdateMode): LocationRequest {
        val intervalMs = when (mode) {
            LocationUpdateMode.MOVING -> ThresholdConfig.GPS_UPDATE_INTERVAL_MOVING_MS
            LocationUpdateMode.STATIONARY -> ThresholdConfig.GPS_UPDATE_INTERVAL_STATIONARY_MS
        }
        val fastestIntervalMs = when (mode) {
            LocationUpdateMode.MOVING -> ThresholdConfig.GPS_FASTEST_INTERVAL_MOVING_MS
            LocationUpdateMode.STATIONARY -> ThresholdConfig.GPS_FASTEST_INTERVAL_STATIONARY_MS
        }

        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .build()
    }
}
