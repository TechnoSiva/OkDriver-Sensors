package com.okdriver.sensors.ui.events

import com.okdriver.sensors.data.model.DrivingEvent

data class EventsUiState(
    val events: List<DrivingEvent> = emptyList()
)
