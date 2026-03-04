package com.okdriver.sensors.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.okdriver.sensors.data.battery.AndroidBatteryDataSource
import com.okdriver.sensors.data.battery.BatteryDataSource
import com.okdriver.sensors.data.events.EventsRepository
import com.okdriver.sensors.data.location.AndroidLocationDataSource
import com.okdriver.sensors.data.location.LocationDataSource
import com.okdriver.sensors.data.location.LocationUpdateMode
import com.okdriver.sensors.data.model.MonitoringSession
import com.okdriver.sensors.data.monitoring.MonitoringSessionRepository
import com.okdriver.sensors.data.sensors.AndroidSensorDataSource
import com.okdriver.sensors.data.sensors.SensorDataSource
import com.okdriver.sensors.data.sensors.SensorSamplingMode
import com.okdriver.sensors.domain.DrivingEventDetector
import com.okdriver.sensors.domain.MotionState
import com.okdriver.sensors.domain.MotionStateEvaluator
import com.okdriver.sensors.domain.ThresholdConfig
import com.okdriver.sensors.util.TimeFormatter
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val sensorDataSource: SensorDataSource =
        AndroidSensorDataSource(application.applicationContext)
    private val locationDataSource: LocationDataSource =
        AndroidLocationDataSource(application.applicationContext)
    private val batteryDataSource: BatteryDataSource = AndroidBatteryDataSource()
    private val drivingEventDetector = DrivingEventDetector()
    private val motionStateEvaluator = MotionStateEvaluator()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var monitorStartTimeMs: Long = 0L
    private var sensorCollectionJob: Job? = null
    private var locationCollectionJob: Job? = null
    private var elapsedJob: Job? = null
    private var batteryReportJob: Job? = null
    private var latestLocationSnapshot = _uiState.value.locationSnapshot

    private var activeMotionState = MotionState.UNKNOWN
    private var activeSamplingMode = SensorSamplingMode.MOVING
    private var activeLocationMode = LocationUpdateMode.MOVING

    init {
        _uiState.update { current ->
            current.copy(sensorAvailability = sensorDataSource.sensorAvailability)
        }
        restoreSession()
    }

    fun startMonitoring(hasLocationPermission: Boolean) {
        if (_uiState.value.isMonitoring) {
            return
        }

        savedStateHandle[KEY_HAS_LOCATION_PERMISSION] = hasLocationPermission
        startMonitoringSession()
        startSensorStreaming()
        startElapsedTicker()
        startMonitoringGps(hasLocationPermission)
        startBatteryReportTimer()
    }

    fun stopMonitoring() {
        if (!_uiState.value.isMonitoring) {
            return
        }

        batteryReportJob?.cancel()
        stopMonitoringGps()
        stopSensorStreaming()
        stopElapsedTicker()

        val currentSession = MonitoringSessionRepository.sessionState.value
        val elapsedMs = currentElapsedMs()
        val updatedSession = if (currentSession.reportReady) {
            currentSession.copy(
                isMonitoring = false,
                elapsedMs = elapsedMs
            )
        } else {
            currentSession.copy(
                isMonitoring = false,
                elapsedMs = elapsedMs,
                reportReady = false,
                endTimestampMs = null,
                endBatteryPct = null
            )
        }
        setSession(updatedSession)
        resetOptimizationState(isMonitoring = false)

        _uiState.update { state ->
            state.copy(
                isMonitoring = false,
                statusText = "OFF",
                elapsedText = TimeFormatter.formatElapsed(elapsedMs),
                motionState = MotionState.UNKNOWN,
                optimizationStatus = "Optimizations inactive"
            )
        }
    }

    fun startMonitoringSensors() {
        startMonitoring(hasLocationPermission = false)
    }

    fun stopMonitoringSensors() {
        stopMonitoring()
    }

    fun startMonitoringGps(hasLocationPermission: Boolean) {
        if (!_uiState.value.isMonitoring) {
            _uiState.update { state ->
                state.copy(gpsStatus = GpsStatus.UNAVAILABLE)
            }
            return
        }
        savedStateHandle[KEY_HAS_LOCATION_PERMISSION] = hasLocationPermission

        if (!hasLocationPermission) {
            stopMonitoringGpsInternal(GpsStatus.PERMISSION_DENIED)
            return
        }

        if (!locationDataSource.isLocationProviderEnabled()) {
            stopMonitoringGpsInternal(GpsStatus.PROVIDER_DISABLED)
            return
        }

        locationDataSource.setLocationMode(activeLocationMode)
        locationDataSource.start()
        _uiState.update { state ->
            state.copy(gpsStatus = GpsStatus.AVAILABLE)
        }

        locationCollectionJob?.cancel()
        locationCollectionJob = viewModelScope.launch {
            try {
                locationDataSource.locationSnapshots().collect { locationSnapshot ->
                    latestLocationSnapshot = locationSnapshot
                    _uiState.update { state ->
                        state.copy(
                            locationSnapshot = locationSnapshot,
                            gpsStatus = GpsStatus.AVAILABLE
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                stopMonitoringGpsInternal(GpsStatus.UNAVAILABLE)
            }
        }
    }

    fun stopMonitoringGps() {
        stopMonitoringGpsInternal(GpsStatus.UNAVAILABLE)
    }

    fun onLocationPermissionResult(granted: Boolean) {
        savedStateHandle[KEY_HAS_LOCATION_PERMISSION] = granted
        if (granted) {
            startMonitoringGps(hasLocationPermission = true)
        } else {
            stopMonitoringGpsInternal(GpsStatus.PERMISSION_DENIED)
        }
    }

    fun onHostStopped() {
        if (_uiState.value.isMonitoring) {
            return
        }
        stopSensorStreaming()
        locationDataSource.stop()
        locationCollectionJob?.cancel()
    }

    private fun stopMonitoringGpsInternal(status: GpsStatus) {
        locationDataSource.stop()
        locationCollectionJob?.cancel()
        _uiState.update { state ->
            latestLocationSnapshot = if (status == GpsStatus.AVAILABLE) {
                state.locationSnapshot
            } else {
                null
            }
            state.copy(
                locationSnapshot = if (status == GpsStatus.AVAILABLE) state.locationSnapshot else null,
                gpsStatus = status
            )
        }
    }

    private fun startMonitoringSession() {
        val startTimestamp = System.currentTimeMillis()
        val startBattery = batteryDataSource.getBatteryPercent(getApplication())
        monitorStartTimeMs = startTimestamp
        motionStateEvaluator.reset()
        resetOptimizationState(isMonitoring = true)
        setSession(
            MonitoringSession(
                isMonitoring = true,
                startTimestampMs = startTimestamp,
                startBatteryPct = startBattery,
                reportReady = false,
                endTimestampMs = null,
                endBatteryPct = null,
                elapsedMs = 0L
            )
        )
        _uiState.update { state ->
            state.copy(
                isMonitoring = true,
                statusText = "ON",
                elapsedText = "00:00",
                motionState = MotionState.UNKNOWN,
                optimizationStatus = "Motion calibration in progress"
            )
        }
    }

    private fun startSensorStreaming() {
        sensorDataSource.setSamplingMode(activeSamplingMode)
        sensorDataSource.start()
        sensorCollectionJob?.cancel()
        sensorCollectionJob = viewModelScope.launch {
            sensorDataSource.sensorSnapshots().collect { snapshot ->
                val detectedEvents = drivingEventDetector.detect(snapshot, latestLocationSnapshot)
                EventsRepository.addEvents(detectedEvents)
                val motionState = motionStateEvaluator.update(snapshot, latestLocationSnapshot)
                applyMotionOptimizations(motionState)
                _uiState.update { current ->
                    current.copy(sensorSnapshot = snapshot)
                }
            }
        }
    }

    private fun stopSensorStreaming() {
        sensorDataSource.stop()
        sensorCollectionJob?.cancel()
    }

    private fun startElapsedTicker() {
        stopElapsedTicker()
        elapsedJob = viewModelScope.launch {
            while (isActive && _uiState.value.isMonitoring) {
                val elapsedMs = currentElapsedMs()
                _uiState.update { state ->
                    state.copy(elapsedText = TimeFormatter.formatElapsed(elapsedMs))
                }
                val currentSession = MonitoringSessionRepository.sessionState.value
                if (currentSession.startTimestampMs > 0L) {
                    setSession(currentSession.copy(elapsedMs = elapsedMs, isMonitoring = true))
                }
                if (_uiState.value.gpsStatus == GpsStatus.AVAILABLE &&
                    !locationDataSource.isLocationProviderEnabled()
                ) {
                    stopMonitoringGpsInternal(GpsStatus.PROVIDER_DISABLED)
                }
                delay(1000)
            }
        }
    }

    private fun stopElapsedTicker() {
        elapsedJob?.cancel()
    }

    private fun startBatteryReportTimer() {
        batteryReportJob?.cancel()
        val session = MonitoringSessionRepository.sessionState.value
        if (!session.isMonitoring || session.reportReady || session.startTimestampMs <= 0L) {
            return
        }

        val elapsedMs = currentElapsedMs()
        val remainingMs = BATTERY_REPORT_DURATION_MS - elapsedMs
        if (remainingMs <= 0L) {
            completeBatteryReport()
            return
        }

        batteryReportJob = viewModelScope.launch {
            delay(remainingMs)
            if (!_uiState.value.isMonitoring) {
                return@launch
            }
            completeBatteryReport()
        }
    }

    private fun completeBatteryReport() {
        val currentSession = MonitoringSessionRepository.sessionState.value
        if (!currentSession.isMonitoring || currentSession.startTimestampMs <= 0L) {
            return
        }

        val endTimestamp = System.currentTimeMillis()
        val endBattery = batteryDataSource.getBatteryPercent(getApplication())
        val elapsedMs = (endTimestamp - currentSession.startTimestampMs).coerceAtLeast(0L)

        setSession(
            currentSession.copy(
                reportReady = true,
                endTimestampMs = endTimestamp,
                endBatteryPct = endBattery,
                elapsedMs = elapsedMs
            )
        )
    }

    private fun applyMotionOptimizations(motionState: MotionState) {
        if (!_uiState.value.isMonitoring) {
            return
        }
        if (motionState == activeMotionState) {
            return
        }

        activeMotionState = motionState
        val newSamplingMode = if (motionState == MotionState.STATIONARY) {
            SensorSamplingMode.STATIONARY
        } else {
            SensorSamplingMode.MOVING
        }
        val newLocationMode = if (motionState == MotionState.STATIONARY) {
            LocationUpdateMode.STATIONARY
        } else {
            LocationUpdateMode.MOVING
        }

        if (newSamplingMode != activeSamplingMode) {
            activeSamplingMode = newSamplingMode
            sensorDataSource.setSamplingMode(newSamplingMode)
        }
        if (newLocationMode != activeLocationMode) {
            activeLocationMode = newLocationMode
            locationDataSource.setLocationMode(newLocationMode)
        }

        val gpsIntervalMs = when (activeLocationMode) {
            LocationUpdateMode.MOVING -> ThresholdConfig.GPS_UPDATE_INTERVAL_MOVING_MS
            LocationUpdateMode.STATIONARY -> ThresholdConfig.GPS_UPDATE_INTERVAL_STATIONARY_MS
        }
        val optimizationStatus = when (motionState) {
            MotionState.STATIONARY -> "Reduced sampling active, gyro disabled, GPS slowed"
            MotionState.MOVING -> "Normal sampling active"
            MotionState.UNKNOWN -> "Motion calibration in progress"
        }

        _uiState.update { state ->
            state.copy(
                motionState = motionState,
                sensorSamplingMode = activeSamplingMode,
                gpsIntervalMs = gpsIntervalMs,
                optimizationStatus = optimizationStatus
            )
        }
    }

    private fun resetOptimizationState(isMonitoring: Boolean) {
        motionStateEvaluator.reset()
        activeMotionState = MotionState.UNKNOWN
        activeSamplingMode = SensorSamplingMode.MOVING
        activeLocationMode = LocationUpdateMode.MOVING
        sensorDataSource.setSamplingMode(activeSamplingMode)
        locationDataSource.setLocationMode(activeLocationMode)
        _uiState.update { state ->
            state.copy(
                motionState = MotionState.UNKNOWN,
                sensorSamplingMode = activeSamplingMode,
                gpsIntervalMs = ThresholdConfig.GPS_UPDATE_INTERVAL_MOVING_MS,
                optimizationStatus = if (isMonitoring) {
                    "Motion calibration in progress"
                } else {
                    "Optimizations inactive"
                }
            )
        }
    }

    private fun currentElapsedMs(): Long {
        if (monitorStartTimeMs <= 0L) {
            return 0L
        }
        return (System.currentTimeMillis() - monitorStartTimeMs).coerceAtLeast(0L)
    }

    private fun restoreSession() {
        val restoredSession = MonitoringSession(
            isMonitoring = savedStateHandle[KEY_IS_MONITORING] ?: false,
            startTimestampMs = savedStateHandle[KEY_START_TIMESTAMP_MS] ?: 0L,
            startBatteryPct = savedStateHandle[KEY_START_BATTERY_PCT] ?: -1,
            reportReady = savedStateHandle[KEY_REPORT_READY] ?: false,
            endTimestampMs = savedStateHandle.get<Long>(KEY_END_TIMESTAMP_MS),
            endBatteryPct = savedStateHandle.get<Int>(KEY_END_BATTERY_PCT),
            elapsedMs = savedStateHandle[KEY_ELAPSED_MS] ?: 0L
        )
        setSession(restoredSession)

        if (restoredSession.startTimestampMs <= 0L) {
            resetOptimizationState(isMonitoring = false)
            return
        }

        monitorStartTimeMs = restoredSession.startTimestampMs
        resetOptimizationState(isMonitoring = restoredSession.isMonitoring)

        val elapsedMs = if (restoredSession.isMonitoring) {
            currentElapsedMs()
        } else {
            restoredSession.elapsedMs
        }
        _uiState.update { state ->
            state.copy(
                isMonitoring = restoredSession.isMonitoring,
                statusText = if (restoredSession.isMonitoring) "ON" else "OFF",
                elapsedText = TimeFormatter.formatElapsed(elapsedMs)
            )
        }
        if (restoredSession.isMonitoring) {
            startSensorStreaming()
            startElapsedTicker()
            startBatteryReportTimer()
            val hadLocationPermission = savedStateHandle[KEY_HAS_LOCATION_PERMISSION] ?: false
            startMonitoringGps(hadLocationPermission)
        }
    }

    private fun setSession(session: MonitoringSession) {
        MonitoringSessionRepository.setSession(session)
        savedStateHandle[KEY_IS_MONITORING] = session.isMonitoring
        savedStateHandle[KEY_START_TIMESTAMP_MS] = session.startTimestampMs
        savedStateHandle[KEY_START_BATTERY_PCT] = session.startBatteryPct
        savedStateHandle[KEY_REPORT_READY] = session.reportReady
        savedStateHandle[KEY_END_TIMESTAMP_MS] = session.endTimestampMs
        savedStateHandle[KEY_END_BATTERY_PCT] = session.endBatteryPct
        savedStateHandle[KEY_ELAPSED_MS] = session.elapsedMs
    }

    override fun onCleared() {
        batteryReportJob?.cancel()
        stopElapsedTicker()
        sensorDataSource.stop()
        locationDataSource.stop()
        sensorCollectionJob?.cancel()
        locationCollectionJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val BATTERY_REPORT_DURATION_MS = 30 * 60 * 1000L

        const val KEY_IS_MONITORING = "session_is_monitoring"
        const val KEY_START_TIMESTAMP_MS = "session_start_timestamp_ms"
        const val KEY_START_BATTERY_PCT = "session_start_battery_pct"
        const val KEY_REPORT_READY = "session_report_ready"
        const val KEY_END_TIMESTAMP_MS = "session_end_timestamp_ms"
        const val KEY_END_BATTERY_PCT = "session_end_battery_pct"
        const val KEY_ELAPSED_MS = "session_elapsed_ms"
        const val KEY_HAS_LOCATION_PERMISSION = "session_has_location_permission"
    }
}
