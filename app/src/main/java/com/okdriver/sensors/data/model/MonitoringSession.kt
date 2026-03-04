package com.okdriver.sensors.data.model

data class MonitoringSession(
    val isMonitoring: Boolean = false,
    val startTimestampMs: Long = 0L,
    val startBatteryPct: Int = -1,
    val reportReady: Boolean = false,
    val endTimestampMs: Long? = null,
    val endBatteryPct: Int? = null,
    val elapsedMs: Long = 0L
) {
    val drainPct: Int?
        get() = if (reportReady && endBatteryPct != null && startBatteryPct >= 0) {
            (startBatteryPct - endBatteryPct).coerceAtLeast(0)
        } else {
            null
        }
}
