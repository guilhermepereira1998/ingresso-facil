package com.wildrift.aicoach.thermal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class ThermalGuard(
    private val context: Context,
    private val onThermalStateChanged: (captureIntervalMs: Long, enableMinimapModel: Boolean, tempCelsius: Float) -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 350) ?: 350
            val tempCelsius = tempTenths / 10.0f

            val (intervalMs, enableModel) = when {
                tempCelsius >= 43.0f -> 4000L to false
                tempCelsius >= 41.0f -> 2500L to false
                tempCelsius >= 40.0f -> 2500L to true
                else -> 1000L to true
            }
            onThermalStateChanged(intervalMs, enableModel, tempCelsius)
        }
    }

    fun register() {
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
