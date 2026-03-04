package com.okdriver.sensors.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.okdriver.sensors.R
import com.okdriver.sensors.data.sensors.SensorSamplingMode
import com.okdriver.sensors.domain.MotionState
import java.util.Locale
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by activityViewModels()
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusText = view.findViewById<TextView>(R.id.text_monitoring_status)
        val elapsedText = view.findViewById<TextView>(R.id.text_elapsed)
        val motionStateText = view.findViewById<TextView>(R.id.text_motion_state)
        val samplingModeText = view.findViewById<TextView>(R.id.text_sampling_mode)
        val gpsIntervalText = view.findViewById<TextView>(R.id.text_gps_interval)
        val optimizationStatusText = view.findViewById<TextView>(R.id.text_optimization_status)
        val sensorUnavailableText = view.findViewById<TextView>(R.id.text_sensor_unavailable)
        val accelerometerText = view.findViewById<TextView>(R.id.text_accel_values)
        val gyroscopeText = view.findViewById<TextView>(R.id.text_gyro_values)
        val magnetometerText = view.findViewById<TextView>(R.id.text_mag_values)
        val gpsStatusText = view.findViewById<TextView>(R.id.text_gps_status)
        val gpsLatLonText = view.findViewById<TextView>(R.id.text_gps_lat_lon)
        val gpsSpeedText = view.findViewById<TextView>(R.id.text_gps_speed)
        val gpsAccuracyText = view.findViewById<TextView>(R.id.text_gps_accuracy)
        val startButton = view.findViewById<Button>(R.id.button_start_monitoring)
        val stopButton = view.findViewById<Button>(R.id.button_stop_monitoring)

        startButton.setOnClickListener {
            val hasPermission = hasLocationPermission()
            viewModel.startMonitoring(hasLocationPermission = hasPermission)
            if (!hasPermission) {
                locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        }
        stopButton.setOnClickListener { viewModel.stopMonitoring() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    statusText.text =
                        getString(R.string.dashboard_status_label) + " " + state.statusText
                    elapsedText.text =
                        getString(R.string.dashboard_elapsed_label) + " " + state.elapsedText
                    startButton.isEnabled = !state.isMonitoring
                    stopButton.isEnabled = state.isMonitoring
                    motionStateText.text = getString(
                        R.string.dashboard_motion_label,
                        getMotionStateLabel(state.motionState)
                    )
                    samplingModeText.text = getString(
                        R.string.dashboard_sampling_label,
                        getSamplingLabel(state.sensorSamplingMode)
                    )
                    gpsIntervalText.text = getString(
                        R.string.dashboard_gps_interval_label,
                        getString(R.string.gps_interval_seconds, state.gpsIntervalMs / 1000f)
                    )
                    optimizationStatusText.text = getString(
                        R.string.dashboard_optimization_label,
                        state.optimizationStatus
                    )

                    accelerometerText.text = getString(
                        R.string.dashboard_accel_format,
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.accelX,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.accelerometerAvailable
                        ),
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.accelY,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.accelerometerAvailable
                        ),
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.accelZ,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.accelerometerAvailable
                        )
                    )
                    gyroscopeText.text = getString(
                        R.string.dashboard_gyro_format,
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.gyroX,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.gyroscopeAvailable &&
                                state.sensorSamplingMode == SensorSamplingMode.MOVING
                        ),
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.gyroY,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.gyroscopeAvailable &&
                                state.sensorSamplingMode == SensorSamplingMode.MOVING
                        ),
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.gyroZ,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.gyroscopeAvailable &&
                                state.sensorSamplingMode == SensorSamplingMode.MOVING
                        )
                    )
                    magnetometerText.text = getString(
                        R.string.dashboard_mag_format,
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.magX,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.magnetometerAvailable
                        ),
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.magY,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.magnetometerAvailable
                        ),
                        formatDecimalOrDash(
                            value = state.sensorSnapshot.magZ,
                            isAvailable = state.isMonitoring &&
                                state.sensorAvailability.magnetometerAvailable
                        )
                    )

                    val missingSensors = mutableListOf<String>()
                    if (!state.sensorAvailability.accelerometerAvailable) {
                        missingSensors.add(getString(R.string.sensor_name_accelerometer))
                    }
                    if (!state.sensorAvailability.gyroscopeAvailable) {
                        missingSensors.add(getString(R.string.sensor_name_gyroscope))
                    }
                    if (!state.sensorAvailability.magnetometerAvailable) {
                        missingSensors.add(getString(R.string.sensor_name_magnetometer))
                    }

                    sensorUnavailableText.isVisible = missingSensors.isNotEmpty()
                    if (missingSensors.isNotEmpty()) {
                        sensorUnavailableText.text = getString(
                            R.string.dashboard_sensor_unavailable,
                            missingSensors.joinToString()
                        )
                    }

                    gpsStatusText.text = when (state.gpsStatus) {
                        GpsStatus.AVAILABLE -> getString(R.string.gps_status_available)
                        GpsStatus.PERMISSION_DENIED -> getString(R.string.gps_status_permission_denied)
                        GpsStatus.PROVIDER_DISABLED -> getString(R.string.gps_status_provider_disabled)
                        GpsStatus.UNAVAILABLE -> getString(R.string.gps_status_unavailable)
                    }

                    val location = state.locationSnapshot
                    if (location == null) {
                        gpsLatLonText.text = getString(R.string.gps_lat_lon_placeholder)
                        gpsSpeedText.text = getString(R.string.gps_speed_placeholder)
                        gpsAccuracyText.text = getString(R.string.gps_accuracy_placeholder)
                    } else {
                        gpsLatLonText.text = getString(
                            R.string.gps_lat_lon_format,
                            formatDecimalOrDash(location.lat.toFloat(), true),
                            formatDecimalOrDash(location.lon.toFloat(), true)
                        )
                        gpsSpeedText.text = getString(
                            R.string.gps_speed_format,
                            formatDecimalOrDash(location.speedMps * 3.6f, true)
                        )
                        gpsAccuracyText.text = getString(
                            R.string.gps_accuracy_format,
                            formatDecimalOrDash(location.accuracyM, true)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission() &&
            viewModel.uiState.value.isMonitoring &&
            viewModel.uiState.value.gpsStatus != GpsStatus.AVAILABLE
        ) {
            viewModel.startMonitoringGps(hasLocationPermission = true)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onHostStopped()
    }

    private fun hasLocationPermission(): Boolean {
        val context = requireContext()
        val finePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return finePermission || coarsePermission
    }

    private fun getMotionStateLabel(motionState: MotionState): String {
        return when (motionState) {
            MotionState.MOVING -> getString(R.string.motion_moving)
            MotionState.STATIONARY -> getString(R.string.motion_stationary)
            MotionState.UNKNOWN -> getString(R.string.motion_unknown)
        }
    }

    private fun getSamplingLabel(samplingMode: SensorSamplingMode): String {
        return when (samplingMode) {
            SensorSamplingMode.MOVING -> getString(R.string.sampling_fast)
            SensorSamplingMode.STATIONARY -> getString(R.string.sampling_slow)
        }
    }

    private fun formatDecimalOrDash(value: Float?, isAvailable: Boolean): String {
        if (!isAvailable || value == null) {
            return DASH
        }
        return String.format(Locale.US, "%.2f", value)
    }

    private companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        const val DASH = "--"
    }
}
