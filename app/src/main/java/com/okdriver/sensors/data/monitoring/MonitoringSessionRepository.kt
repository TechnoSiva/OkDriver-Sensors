package com.okdriver.sensors.data.monitoring

import com.okdriver.sensors.data.model.MonitoringSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MonitoringSessionRepository {
    private val _sessionState = MutableStateFlow(MonitoringSession())
    val sessionState: StateFlow<MonitoringSession> = _sessionState.asStateFlow()

    fun setSession(session: MonitoringSession) {
        _sessionState.value = session
    }
}
