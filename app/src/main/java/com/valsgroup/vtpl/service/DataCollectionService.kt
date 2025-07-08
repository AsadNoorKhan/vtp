package com.valsgroup.vtpl.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.valsgroup.vtpl.MainActivity
import com.valsgroup.vtpl.R
import com.valsgroup.vtpl.api.ApiService
import com.valsgroup.vtpl.api.DeviceData
import com.valsgroup.vtpl.database.TrackingDatabase
import com.valsgroup.vtpl.utils.NetworkUtils
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class DataCollectionService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var apiService: ApiService
    private lateinit var database: TrackingDatabase
    private var job: Job? = null
    private var syncJob: Job? = null
    private var currentLocation: Location? = null
    private var batteryLevel: Float = 0f
    private var isCharging: Boolean = false
    private var authToken: String = "" // Store auth token
    private var isMainDataCollectionInProgress = false // Flag to pause offline sync during main data collection

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            batteryLevel = level * 100 / scale.toFloat()
            isCharging = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                    BatteryManager.BATTERY_STATUS_CHARGING
            
            Log.d(TAG, "Battery update: Level=${batteryLevel}%, Charging=$isCharging")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service onCreate() called")
        
        // Start foreground service IMMEDIATELY to avoid crash
        ensureForegroundService()
        
        // Initialize service components
        initializeService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🎯 Service onStartCommand() called - startId: $startId")
        
        // Ensure foreground service is started (in case onCreate was not called)
        ensureForegroundService()
        
        return START_STICKY // Restart service if killed
    }

    private fun initializeService() {
        try {
            Log.d(TAG, "🔧 Initializing service components...")
            
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            database = TrackingDatabase(this)

        val retrofit = Retrofit.Builder()
                .baseUrl("http://avl.valstracking.com:8080")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        startLocationUpdates()
        startDataCollection()
            startOfflineSync()
            
            Log.d(TAG, "✅ Service initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing service", e)
            stopSelf()
        }
    }

    private fun ensureForegroundService() {
        try {
            Log.d(TAG, "🛡️ Ensuring foreground service is started...")
            
            // Check if already in foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val isForeground = try {
                    val method = Service::class.java.getDeclaredMethod("isForegroundServiceType")
                    method.invoke(this) as Boolean
                } catch (e: Exception) {
                    false
                }
                if (isForeground) {
                    Log.d(TAG, "✅ Service already in foreground")
                    return
                }
            }
            
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ startForeground() called successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calling startForeground()", e)
            
            // Try with fallback notification
            try {
                Log.d(TAG, "🔄 Attempting with fallback notification...")
                val fallbackNotification = createFallbackNotification()
                startForeground(NOTIFICATION_ID, fallbackNotification)
                Log.d(TAG, "✅ startForeground() with fallback successful")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Fatal: Could not start foreground service even with fallback", e2)
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        try {
        val channelId = "data_collection_service"
        val channelName = "Data Collection Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VTPL Data Collection")
            .setContentText("Collecting device data...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating notification", e)
            throw e
        }
    }

    private fun createFallbackNotification(): Notification {
        Log.d(TAG, "🆘 Creating fallback notification...")
        
        val channelId = "fallback_service"
        val channelName = "Fallback Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VTPL Service")
            .setContentText("Service is running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates() {
        try {
            Log.d(TAG, "📍 Starting location updates...")
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // 1 second
                1f,   // 1 meter
                this
            )
            Log.d(TAG, "✅ GPS location updates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception requesting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting location updates", e)
        }
    }

    private fun startDataCollection() {
        job = CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "📊 Starting data collection job...")
            while (isActive) {
                try {
                collectAndSendData()
                    val prefs = getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
                    val mode = prefs.getString("MODE", "Normal") ?: "Normal"
                    val interval = when (mode) {
                        "Realtime" -> 1000L // 1 second
                        else -> 60000L // 1 minute for Normal and others
                    }
                    Log.d(TAG, "⏱️ Data collection completed, waiting ${interval}ms for next cycle (Mode: $mode)")
                    delay(interval)
                } catch (e: CancellationException) {
                    Log.d(TAG, "🛑 Data collection loop cancelled - service stopping")
                    break // Exit the loop when cancelled
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in data collection loop", e)
                    delay(5000) // Wait 5 seconds before retrying
                }
            }
        }
    }

    private fun startOfflineSync() {
        syncJob = CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "🔄 Starting offline sync job...")
            while (isActive) {
                try {
                    // Check if there's offline data to sync and main data collection is not in progress
                    val unsyncedCount = database.getUnsyncedCount()
                    if (unsyncedCount > 0 && NetworkUtils.isNetworkAvailable(this@DataCollectionService) && !isMainDataCollectionInProgress) {
                        Log.d(TAG, "🔄 Found $unsyncedCount offline entries - syncing every 5 seconds")
                        syncOfflineData()
                        delay(OFFLINE_SYNC_INTERVAL) // 5 seconds delay for offline sync
                    } else {
                        if (isMainDataCollectionInProgress) {
                            Log.d(TAG, "⏸️ Offline sync paused - main data collection in progress")
                        }
                        // No offline data or no network - check less frequently
                        delay(30000) // 30 seconds delay when no offline sync needed
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "🛑 Offline sync loop cancelled - service stopping")
                    break // Exit the loop when cancelled
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in offline sync loop", e)
                    delay(10000) // Wait 10 seconds before retrying
                }
            }
        }
    }

    private suspend fun collectAndSendData() {
        try {
            // Set flag to pause offline sync during main data collection
            isMainDataCollectionInProgress = true
            Log.d(TAG, "🚀 Starting main data collection (1-minute interval)")
            
            // Get phone number from SharedPreferences
            val phoneNumber = getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
                .getString("PHONE_NUMBER", "Unknown")

            val location = currentLocation
            if (location != null) {
                // Check if location has changed significantly (1 meter threshold)
                val lastLocation = database.getLastLocation()
                var shouldStoreData = true
                
                if (lastLocation != null) {
                    val lastLocationObj = Location("last").apply {
                        latitude = lastLocation.first
                        longitude = lastLocation.second
                    }
                    
                    val distance = location.distanceTo(lastLocationObj)
                    shouldStoreData = distance >= LOCATION_CHANGE_THRESHOLD
                    
                    Log.d(TAG, "Location change: ${"%.2f".format(distance)}m (threshold: ${LOCATION_CHANGE_THRESHOLD}m), storing: $shouldStoreData")
                }

                if (shouldStoreData) {
                    // Format date as required by server: "YYYY-MM-DD HH:MM:SS"
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val deviceDate = dateFormat.format(Date())

                val deviceData = DeviceData(
                        imei_id = phoneNumber ?: "Unknown",
                        device_date = deviceDate,
                        latitude = String.format(Locale.US, "%.7f", location.latitude).toDouble(),
                        longitude = String.format(Locale.US, "%.7f", location.longitude).toDouble(),
                        altitude = String.format(Locale.US, "%.7f", location.altitude).toDouble().toInt(),
                        satellites = 18, // Hardcoded as we can't get this easily
                        gsm_signal_level = 3, // Hardcoded as we can't get this easily
                        battery_power = if (isCharging) "Y" else "N",
                        battery_level = batteryLevel.toInt(),
                        battery_voltage = 4.2f, // Hardcoded typical battery voltage
                        external_voltage = 0.0f // Hardcoded as we don't have external power
                    )

                    Log.d(TAG, "📡 Main data collection: Phone=${deviceData.imei_id}, Date=${deviceData.device_date}, Lat=${"%.4f".format(deviceData.latitude)}, Lng=${"%.4f".format(deviceData.longitude)}")

                    // Check if network is available for direct send
                    if (NetworkUtils.isNetworkAvailable(this)) {
                        Log.d(TAG, "🌐 Internet available - attempting direct send to server")
                        try {
                            val response = apiService.sendDeviceData("Bearer $AUTH_TOKEN", "application/json", deviceData)
                            if (response.isSuccessful) {
                                Log.d(TAG, "✅ Main data sent successfully to server")
                                // Store in database as synced since it was sent successfully
                                val entryId = database.insertTrackingData(deviceData)
                                database.markAsSynced(entryId)
                                Log.d(TAG, "💾 Stored and marked as synced (ID: $entryId)")
                            } else {
                                Log.e(TAG, "❌ Failed to send main data: ${response.code()} - ${response.message()}")
                                // Store in database as unsynced for later retry
                                val entryId = database.insertTrackingData(deviceData)
                                Log.d(TAG, "💾 Stored as unsynced for later retry (ID: $entryId)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error sending main data", e)
                            // Store in database as unsynced for later retry
                            val entryId = database.insertTrackingData(deviceData)
                            Log.d(TAG, "💾 Stored as unsynced for later retry (ID: $entryId)")
                        }
                    } else {
                        Log.d(TAG, "📱 No internet - storing data offline")
                        // No network available, store offline
                        val entryId = database.insertTrackingData(deviceData)
                        Log.d(TAG, "💾 Stored offline (ID: $entryId) - will sync when internet returns")
                    }
                } else {
                    Log.d(TAG, "⏭️ Skipping data storage - location change below threshold")
                }
            } else {
                Log.d(TAG, "📍 Location not available yet")
            }
            
            // Clear flag to resume offline sync
            isMainDataCollectionInProgress = false
            Log.d(TAG, "✅ Main data collection completed - resuming offline sync")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in collectAndSendData", e)
            // Clear flag even on error to prevent permanent blocking
            isMainDataCollectionInProgress = false
        }
    }

    private suspend fun syncOfflineData() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            return
        }
        
        val unsyncedEntries = database.getUnsyncedEntries()
        if (unsyncedEntries.isNotEmpty()) {
            Log.d(TAG, "🔄 Offline sync: processing ${unsyncedEntries.size} entries")
            
            var successCount = 0
            var failureCount = 0
            
            for (entry in unsyncedEntries) {
                try {
                    val deviceData = entry.toDeviceData()
                    Log.d(TAG, "📤 Syncing offline entry ${entry.id}: Lat=${"%.4f".format(deviceData.latitude)}, Lng=${"%.4f".format(deviceData.longitude)}")
                    
                    val response = apiService.sendDeviceData("Bearer $AUTH_TOKEN", "application/json", deviceData)
                    
                    if (response.isSuccessful) {
                        database.markAsSynced(entry.id)
                        successCount++
                        Log.d(TAG, "✅ Synced offline entry ${entry.id}")
                    } else {
                        failureCount++
                        Log.e(TAG, "❌ Failed to sync offline entry ${entry.id}: ${response.code()}")
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "❌ Error syncing offline entry ${entry.id}: ${e.message}")
                }
            }
            
            Log.d(TAG, "📊 Offline sync completed: $successCount successful, $failureCount failed")
            
            if (failureCount == 0 && successCount > 0) {
                Log.d(TAG, "🎉 All offline data synced successfully!")
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        syncJob?.cancel()
        unregisterReceiver(batteryReceiver)
        locationManager.removeUpdates(this)
        database.close()
    }

    companion object {
        private const val TAG = "DataCollectionService"
        private const val NOTIFICATION_ID = 1
        private const val AUTH_TOKEN = "vtpliveviewvwep" // Replace with your actual auth token
        private const val LOCATION_CHANGE_THRESHOLD = 1.0f // 1 meter threshold for location changes
        private const val OFFLINE_SYNC_INTERVAL = 5000L // 5 seconds for offline data syncing

        fun startService(context: Context) {
            val serviceIntent = Intent(context, DataCollectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        fun stopService(context: Context) {
            val serviceIntent = Intent(context, DataCollectionService::class.java)
            context.stopService(serviceIntent)
        }
    }
}