package com.valsgroup.vtpl.utils

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SignalMonitor(private val context: Context) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    private val _signalLevel = MutableStateFlow(0)
    val signalLevel: StateFlow<Int> = _signalLevel.asStateFlow()

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            val newSignalLevel = signalStrength?.level ?: 0 // 0-4 (API 23+)
            _signalLevel.value = newSignalLevel
            Log.d(TAG, "GSM signal level: $newSignalLevel")
        }
    }

    fun startMonitoring() {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        Log.d(TAG, "📶 Signal monitoring started")
    }

    fun stopMonitoring() {
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            Log.d(TAG, "📶 Signal monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping signal monitoring", e)
        }
    }

    fun getLastKnownSignalLevel(): Int {
        return _signalLevel.value
    }

    companion object {
        private const val TAG = "SignalMonitor"
    }
} 