package com.valsgroup.vtpl.utils

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }
            
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Ensure we're on the main thread when requesting location updates
            withContext(Dispatchers.Main) {
                locationManager.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    1000, // 1 second
                    1f,   // 1 meter
                    locationListener,
                    mainHandler.looper
                )
                Log.d(TAG, "📍 Location updates started with traditional LocationManager")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception requesting location updates", e)
            close(e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting location updates", e)
            close(e)
        }

        awaitClose {
            try {
                // Use mainHandler to post the removal to main thread
                mainHandler.post {
                    locationManager.removeUpdates(locationListener)
                    Log.d(TAG, "📍 Location updates stopped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping location updates", e)
            }
        }
    }

    fun getLastKnownLocation(): Location? {
        return try {
            locationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception getting last known location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting last known location", e)
            null
        }
    }

    companion object {
        private const val TAG = "LocationManager"
    }
} 