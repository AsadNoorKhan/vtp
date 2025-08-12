package com.valsgroup.vtpl.utils

import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SatelliteMonitor(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private val _satelliteCount = MutableStateFlow(0)
    val satelliteCount: StateFlow<Int> = _satelliteCount.asStateFlow()

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val newSatelliteCount = status.satelliteCount
            _satelliteCount.value = newSatelliteCount
            Log.d(TAG, "GNSS satellites: $newSatelliteCount")
        }
    }

    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                locationManager.registerGnssStatusCallback(
                    gnssCallback, 
                    Handler(Looper.getMainLooper())
                )
                Log.d(TAG, "🛰️ Satellite monitoring started")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting satellite monitoring", e)
            }
        } else {
            Log.w(TAG, "⚠️ GNSS status callback not supported on this API level")
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssCallback)
                Log.d(TAG, "🛰️ Satellite monitoring stopped")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping satellite monitoring", e)
            }
        }
    }

    fun getLastKnownSatelliteCount(): Int {
        return _satelliteCount.value
    }

    companion object {
        private const val TAG = "SatelliteMonitor"
    }
} 