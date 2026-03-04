package com.okdriver.sensors.ui.battery

data class BatteryUiState(
    val monitoringStatusText: String = "OFF",
    val startBatteryText: String = "--",
    val startTimeText: String = "--",
    val elapsedText: String = "00:00",
    val reportMessageText: String = "Battery report will be available after 30 minutes of monitoring.",
    val endBatteryText: String = "--",
    val endTimeText: String = "--",
    val drainText: String = "--"
)
