package com.okdriver.sensors.data.model

data class LocationSnapshot(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val speedMps: Float = 0f,
    val accuracyM: Float = 0f,
    val bearingDeg: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
