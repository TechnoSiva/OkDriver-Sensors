package com.okdriver.sensors

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.okdriver.sensors.ui.battery.BatteryFragment
import com.okdriver.sensors.ui.dashboard.DashboardFragment
import com.okdriver.sensors.ui.events.EventsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_nav)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    openDashboard()
                    true
                }
                R.id.navigation_events -> {
                    openEvents()
                    true
                }
                R.id.navigation_battery -> {
                    openBattery()
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_dashboard
        }
    }

    private fun openDashboard() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DashboardFragment())
            .commit()
    }

    private fun openEvents() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EventsFragment())
            .commit()
    }

    private fun openBattery() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BatteryFragment())
            .commit()
    }
}
