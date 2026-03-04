package com.okdriver.sensors.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okdriver.sensors.data.model.MonitoringSession
import com.okdriver.sensors.data.monitoring.MonitoringSessionRepository
import com.okdriver.sensors.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BatteryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            MonitoringSessionRepository.sessionState.collect { session ->
                _uiState.update { session.toUiState() }
            }
        }
    }

    private fun MonitoringSession.toUiState(): BatteryUiState {
        if (startTimestampMs <= 0L || startBatteryPct < 0) {
            return BatteryUiState(
                monitoringStatusText = "OFF"
            )
        }

        val elapsedMs = if (isMonitoring) {
            (System.currentTimeMillis() - startTimestampMs).coerceAtLeast(0L)
        } else {
            elapsedMs
        }

        val reportMessage = if (reportReady) {
            "Battery report ready."
        } else {
            "Battery report will be available after 30 minutes of monitoring."
        }

        return BatteryUiState(
            monitoringStatusText = if (isMonitoring) "ON" else "OFF",
            startBatteryText = "$startBatteryPct%",
            startTimeText = TimeFormatter.formatTimestamp(startTimestampMs),
            elapsedText = TimeFormatter.formatElapsed(elapsedMs),
            reportMessageText = reportMessage,
            endBatteryText = if (reportReady && endBatteryPct != null) "$endBatteryPct%" else "--",
            endTimeText = if (reportReady && endTimestampMs != null) {
                TimeFormatter.formatTimestamp(endTimestampMs)
            } else {
                "--"
            },
            drainText = if (reportReady && drainPct != null) "$drainPct%" else "--"
        )
    }
}
