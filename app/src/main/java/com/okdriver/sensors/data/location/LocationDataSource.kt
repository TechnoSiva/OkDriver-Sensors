package com.okdriver.sensors.data.location

import com.okdriver.sensors.data.model.LocationSnapshot
import kotlinx.coroutines.flow.Flow

interface LocationDataSource {
    fun isLocationProviderEnabled(): Boolean

    fun locationSnapshots(): Flow<LocationSnapshot>

    fun setLocationMode(mode: LocationUpdateMode)

    fun start()

    fun stop()
}

enum class LocationUpdateMode {
    MOVING,
    STATIONARY
}
