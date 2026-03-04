package com.okdriver.sensors.data.battery

import android.content.Context

interface BatteryDataSource {
    fun getBatteryPercent(context: Context): Int
}
