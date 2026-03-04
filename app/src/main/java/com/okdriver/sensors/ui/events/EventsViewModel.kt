package com.okdriver.sensors.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okdriver.sensors.data.events.EventsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EventsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            EventsRepository.events.collect { events ->
                _uiState.update { state ->
                    state.copy(events = events)
                }
            }
        }
    }

    fun clearEvents() {
        EventsRepository.clear()
    }
}
