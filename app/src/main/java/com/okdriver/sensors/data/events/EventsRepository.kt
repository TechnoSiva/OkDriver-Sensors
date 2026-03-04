package com.okdriver.sensors.data.events

import com.okdriver.sensors.data.model.DrivingEvent
import com.okdriver.sensors.domain.ThresholdConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object EventsRepository {
    private val _events = MutableStateFlow<List<DrivingEvent>>(emptyList())
    val events: StateFlow<List<DrivingEvent>> = _events.asStateFlow()

    fun addEvents(newEvents: List<DrivingEvent>) {
        if (newEvents.isEmpty()) {
            return
        }
        _events.update { existing ->
            (newEvents + existing).take(ThresholdConfig.MAX_EVENTS)
        }
    }

    fun clear() {
        _events.value = emptyList()
    }
}
