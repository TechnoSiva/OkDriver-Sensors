package com.okdriver.sensors.ui.battery

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.okdriver.sensors.R
import kotlinx.coroutines.launch

class BatteryFragment : Fragment(R.layout.fragment_battery) {

    private val viewModel: BatteryViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val monitoringStatusText = view.findViewById<TextView>(R.id.text_battery_monitoring_status)
        val startBatteryText = view.findViewById<TextView>(R.id.text_battery_start_battery)
        val startTimeText = view.findViewById<TextView>(R.id.text_battery_start_time)
        val elapsedText = view.findViewById<TextView>(R.id.text_battery_elapsed)
        val reportMessageText = view.findViewById<TextView>(R.id.text_battery_report_message)
        val endBatteryText = view.findViewById<TextView>(R.id.text_battery_end_battery)
        val endTimeText = view.findViewById<TextView>(R.id.text_battery_end_time)
        val drainText = view.findViewById<TextView>(R.id.text_battery_drain)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    monitoringStatusText.text = state.monitoringStatusText
                    startBatteryText.text = state.startBatteryText
                    startTimeText.text = state.startTimeText
                    elapsedText.text = state.elapsedText
                    reportMessageText.text = state.reportMessageText
                    endBatteryText.text = state.endBatteryText
                    endTimeText.text = state.endTimeText
                    drainText.text = state.drainText
                }
            }
        }
    }
}
