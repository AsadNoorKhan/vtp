package com.valsgroup.vtpl.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val voltage: Float
)

class BatteryMonitor(private val context: Context) {
    private val _batteryLevel = MutableStateFlow(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _batteryVoltage = MutableStateFlow(0f)
    val batteryVoltage: StateFlow<Float> = _batteryVoltage.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val newBatteryLevel = level * 100 / scale.toFloat()
            
            val newIsCharging = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                    BatteryManager.BATTERY_STATUS_CHARGING

            val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val newBatteryVoltage = voltage / 1000f // Convert mV to V

            _batteryLevel.value = newBatteryLevel
            _isCharging.value = newIsCharging
            _batteryVoltage.value = newBatteryVoltage

            Log.d(TAG, "Battery update: Level=${newBatteryLevel}%, Charging=$newIsCharging, Voltage=${newBatteryVoltage}V")
        }
    }

    fun startMonitoring() {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.d(TAG, "🔋 Battery monitoring started")
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
            Log.d(TAG, "🔋 Battery monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering battery receiver", e)
        }
    }

    fun getLastKnownBattery(): BatteryInfo {
        return BatteryInfo(
            level = _batteryLevel.value.toInt(),
            isCharging = _isCharging.value,
            voltage = _batteryVoltage.value
        )
    }

    companion object {
        private const val TAG = "BatteryMonitor"
    }
} 