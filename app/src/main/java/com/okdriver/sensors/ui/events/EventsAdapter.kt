package com.okdriver.sensors.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.okdriver.sensors.R
import com.okdriver.sensors.data.model.DrivingEvent
import com.okdriver.sensors.data.model.DrivingEventType
import com.okdriver.sensors.util.TimeFormatter

class EventsAdapter : ListAdapter<DrivingEvent, EventsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.text_event_timestamp)
        private val typeText: TextView = itemView.findViewById(R.id.text_event_type)
        private val severityText: TextView = itemView.findViewById(R.id.text_event_severity)
        private val speedText: TextView = itemView.findViewById(R.id.text_event_speed)

        fun bind(event: DrivingEvent) {
            val context = itemView.context
            timestampText.text = context.getString(
                R.string.event_timestamp_format,
                TimeFormatter.formatTimestamp(event.timestamp)
            )
            typeText.text = context.getString(
                R.string.event_type_format,
                context.getString(event.type.toDisplayLabelRes())
            )
            severityText.text = context.getString(
                R.string.event_severity_format,
                event.severity
            )
            speedText.text = if (event.speedKmh == null) {
                context.getString(R.string.event_speed_not_available)
            } else {
                context.getString(R.string.event_speed_format, event.speedKmh)
            }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<DrivingEvent>() {
            override fun areItemsTheSame(oldItem: DrivingEvent, newItem: DrivingEvent): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: DrivingEvent, newItem: DrivingEvent): Boolean {
                return oldItem == newItem
            }
        }
    }
}

private fun DrivingEventType.toDisplayLabelRes(): Int {
    return when (this) {
        DrivingEventType.HARSH_ACCEL -> R.string.event_type_harsh_accel
        DrivingEventType.HARSH_BRAKE -> R.string.event_type_harsh_brake
        DrivingEventType.SHARP_TURN -> R.string.event_type_sharp_turn
    }
}
