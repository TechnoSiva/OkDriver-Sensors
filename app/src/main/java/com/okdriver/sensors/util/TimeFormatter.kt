package com.okdriver.sensors.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object TimeFormatter {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun formatElapsed(elapsedMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun formatTimestamp(timestampMs: Long): String {
        return timestampFormatter.format(Instant.ofEpochMilli(timestampMs))
    }
}
