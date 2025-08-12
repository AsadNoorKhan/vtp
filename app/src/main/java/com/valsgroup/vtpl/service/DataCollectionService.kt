package com.valsgroup.vtpl.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.valsgroup.vtpl.MainActivity
import com.valsgroup.vtpl.api.ApiService
import com.valsgroup.vtpl.api.DeviceData
import com.valsgroup.vtpl.database.TrackingDatabase
import com.valsgroup.vtpl.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class DataCollectionService : Service() {
    private lateinit var apiService: ApiService
    private lateinit var database: TrackingDatabase
    
    // Modularized monitors
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var satelliteMonitor: SatelliteMonitor
    private lateinit var locationManager: com.valsgroup.vtpl.utils.LocationManager
    
    // Coroutine scope for the service
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationCheckJob: Job? = null
    private var syncJob: Job? = null
    private var locationJob: Job? = null
    
    private var currentLocation: Location? = null
    private var lastStoredLocation: Location? = null
    private var lastSyncTime = 0L
    private var isServiceDestroyed = false
    private var foregroundAttempted = false

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
        
        // Handle pause/stop action from notification
        when (intent?.action) {
            ACTION_PAUSE_SERVICE -> {
                Log.d(TAG, "⏸️ Pause action received from notification")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "🛑 Stop action received from notification")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        // Check if we have necessary permissions to continue
        if (!hasMinimumPermissions()) {
            Log.w(TAG, "⚠️ Insufficient permissions - stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Ensure foreground service is started (in case onCreate was not called)
        ensureForegroundService()
        
        return START_STICKY // Restart service if killed
    }
    
    private fun hasMinimumPermissions(): Boolean {
        // At minimum, we need phone state permission for signal monitoring
        val hasPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasNetworkPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPhonePermission || !hasNetworkPermission) {
            Log.w(TAG, "⚠️ Missing minimum permissions: PHONE_STATE=$hasPhonePermission, NETWORK_STATE=$hasNetworkPermission")
            return false
        }
        
        return true
    }

    private fun initializeService() {
        try {
            Log.d(TAG, "🔧 Initializing service components...")
            
            // Check permissions before starting location features
            if (!hasLocationPermission()) {
                Log.w(TAG, "⚠️ Location permission not granted - skipping location features")
                // Still initialize other components
                initializeBasicComponents()
                return
            }
            
            // Initialize database and API
            database = TrackingDatabase(this)
            val retrofit = Retrofit.Builder()
                .baseUrl("http://avl.valstracking.com:8080")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(ApiService::class.java)

            // Initialize modularized monitors
            batteryMonitor = BatteryMonitor(this)
            signalMonitor = SignalMonitor(this)
            satelliteMonitor = SatelliteMonitor(this)
            locationManager = com.valsgroup.vtpl.utils.LocationManager(this)

            // Initialize network monitoring
            NetworkUtils.initializeNetworkMonitoring(this)
            NetworkUtils.onNetworkRestored = {
                serviceScope.launch {
                    Log.d(TAG, "🔄 Network restored - triggering immediate sync")
                    syncOldestUnsyncedEntry()
                }
            }

            // Start all monitors
            batteryMonitor.startMonitoring()
            signalMonitor.startMonitoring()
            satelliteMonitor.startMonitoring()
            startLocationUpdates()
            
            // Start new flow jobs
            startLocationChecking()
            startPeriodicSync()
            
            Log.d(TAG, "✅ Service initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing service", e)
            stopSelf()
        }
    }
    
    private fun initializeBasicComponents() {
        try {
            Log.d(TAG, "🔧 Initializing basic service components...")
            
            // Initialize database and API
            database = TrackingDatabase(this)
            val retrofit = Retrofit.Builder()
                .baseUrl("http://avl.valstracking.com:8080")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(ApiService::class.java)

            // Initialize basic monitors (no location)
            batteryMonitor = BatteryMonitor(this)
            signalMonitor = SignalMonitor(this)

            // Initialize network monitoring
            NetworkUtils.initializeNetworkMonitoring(this)
            NetworkUtils.onNetworkRestored = {
                serviceScope.launch {
                    Log.d(TAG, "🔄 Network restored - triggering immediate sync")
                    syncOldestUnsyncedEntry()
                }
            }

            // Start basic monitors
            batteryMonitor.startMonitoring()
            signalMonitor.startMonitoring()
            
            // Start periodic sync for existing data
            startPeriodicSync()
            
            Log.d(TAG, "✅ Basic service initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing basic service components", e)
            stopSelf()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureForegroundService() {
        try {
            // Only attempt foreground once per service lifecycle
            if (foregroundAttempted) {
                Log.d(TAG, "🔄 Foreground already attempted - continuing without foreground mode")
                return
            }
            
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
            
            // Try to start foreground without specifying type first
            try {
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "✅ startForeground() called successfully")
                foregroundAttempted = true
            } catch (e: SecurityException) {
                Log.w(TAG, "⚠️ Security exception starting foreground service, trying alternative approach", e)
                // Try alternative approach - just start the service normally
                Log.d(TAG, "🔄 Starting service without foreground mode")
                foregroundAttempted = true
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "⚠️ Foreground service time limit exhausted, continuing without foreground mode", e)
                // Time limit hit - continue without foreground
                Log.d(TAG, "🔄 Continuing service without foreground mode due to time limit")
                foregroundAttempted = true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error calling startForeground()", e)
                // Continue without foreground mode
                Log.d(TAG, "🔄 Continuing service without foreground mode")
                foregroundAttempted = true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in ensureForegroundService", e)
            // Continue without foreground mode
            Log.d(TAG, "🔄 Continuing service without foreground mode")
            foregroundAttempted = true
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

        // Create pause action
        val pauseIntent = Intent(this, DataCollectionService::class.java).apply {
            action = ACTION_PAUSE_SERVICE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 0, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create stop action
        val stopIntent = Intent(this, DataCollectionService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VTPL Data Collection")
            .setContentText("Collecting device data...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
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
        locationJob = serviceScope.launch {
            try {
                Log.d(TAG, "📍 Starting location updates...")
                locationManager.getLocationUpdates()
                    .collect { location ->
                        currentLocation = location
                        Log.d(TAG, "📍 Location updated: ${location.latitude}, ${location.longitude}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in location updates", e)
            }
        }
    }

    private fun startLocationChecking() {
        locationCheckJob = serviceScope.launch {
            Log.d(TAG, "📍 Starting location checking job (every 1 second)...")
            while (isActive) {
                try {
                    checkLocationAndSave()
                    delay(1000) // Check every 1 second
                } catch (e: CancellationException) {
                    Log.d(TAG, "🛑 Location checking loop cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in location checking loop", e)
                    delay(5000)
                }
            }
        }
    }

    private fun startPeriodicSync() {
        syncJob = serviceScope.launch {
            Log.d(TAG, "🔄 Starting periodic sync job (every 15 seconds)...")
            while (isActive) {
                try {
                    syncOldestUnsyncedEntry()
                    delay(15_000) // Sync every 15 seconds
                } catch (e: CancellationException) {
                    Log.d(TAG, "🛑 Periodic sync loop cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in periodic sync loop", e)
                    delay(5000)
                }
            }
        }
    }

    private suspend fun checkLocationAndSave() {
        val location = currentLocation ?: return
        
        // Check if we have a previous location to compare with
        if (lastStoredLocation != null) {
            val distance = location.distanceTo(lastStoredLocation!!)
            
            if (distance >= LOCATION_CHANGE_THRESHOLD) {
                Log.d(TAG, "📍 Location changed by ${"%.2f".format(distance)}m (threshold: ${LOCATION_CHANGE_THRESHOLD}m) - saving to database")
                saveLocationToDatabase(location)
                lastStoredLocation = location
            } else {
                Log.d(TAG, "📍 Location change: ${"%.2f".format(distance)}m (below threshold) - skipping save")
            }
        } else {
            // First location, always save
            Log.d(TAG, "📍 First location received - saving to database")
            saveLocationToDatabase(location)
            lastStoredLocation = location
        }
    }

    private suspend fun saveLocationToDatabase(location: Location) {
        try {
            // Check if service is being destroyed
            if (isServiceDestroyed) {
                Log.d(TAG, "🛑 Service is being destroyed - skipping location save")
                return
            }
            
            val phoneNumber = getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
                .getString("PHONE_NUMBER", "Unknown")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val deviceDate = dateFormat.format(Date())

            // Collect current values from monitors
            val currentBatteryLevel = batteryMonitor.batteryLevel.value
            val currentIsCharging = batteryMonitor.isCharging.value
            val currentBatteryVoltage = batteryMonitor.batteryVoltage.value
            val currentSignalLevel = signalMonitor.signalLevel.value
            val currentSatelliteCount = satelliteMonitor.satelliteCount.value

            val deviceData = DeviceData(
                imei_id = phoneNumber ?: "Unknown",
                device_date = deviceDate,
                latitude = String.format(Locale.US, "%.7f", location.latitude).toDouble(),
                longitude = String.format(Locale.US, "%.7f", location.longitude).toDouble(),
                altitude = String.format(Locale.US, "%.7f", location.altitude).toDouble().toInt(),
                satellites = currentSatelliteCount,
                gsm_signal_level = currentSignalLevel,
                battery_power = if (currentIsCharging) "Y" else "N",
                battery_level = currentBatteryLevel.toInt(),
                battery_voltage = currentBatteryVoltage,
                external_voltage = 0.0f
            )

            // Save to database as unsynced
            val entryId = database.insertTrackingData(deviceData)
            Log.d(TAG, "💾 Saved location to database (ID: $entryId): Lat=${"%.4f".format(deviceData.latitude)}, Lng=${"%.4f".format(deviceData.longitude)}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving location to database", e)
        }
    }



    private suspend fun syncOldestUnsyncedEntry() {
        if (!NetworkUtils.isNetworkAvailable.value) {
            Log.d(TAG, "📱 No network available - skipping sync")
            return
        }

        // Check if service is being destroyed
        if (isServiceDestroyed) {
            Log.d(TAG, "🛑 Service is being destroyed - skipping sync")
            return
        }

        val unsyncedEntries = database.getUnsyncedEntries()
        if (unsyncedEntries.isNotEmpty()) {
            // Get the oldest unsynced entry
            val oldestEntry = unsyncedEntries.first()
            
            try {
                Log.d(TAG, "📤 Syncing oldest unsynced entry ${oldestEntry.id}: Lat=${"%.4f".format(oldestEntry.latitude)}, Lng=${"%.4f".format(oldestEntry.longitude)}")
                
                val deviceData = oldestEntry.toDeviceData()
                val response = apiService.sendDeviceData("Bearer $AUTH_TOKEN", "application/json", deviceData)
                
                if (response.isSuccessful) {
                    database.markAsSynced(oldestEntry.id)
                    Log.d(TAG, "✅ Synced entry ${oldestEntry.id} successfully")
                    Log.i(TAG, "📤 SYNCED: ${deviceData.imei_id} | ${deviceData.device_date} | Lat:${"%.4f".format(deviceData.latitude)} | Lng:${"%.4f".format(deviceData.longitude)} | Battery:${deviceData.battery_level}% | Signal:${deviceData.gsm_signal_level}")
                    
                    // Clean up old synced entries (older than 24 hours)
                    deleteOldSyncedEntries()
                    
                } else {
                    Log.e(TAG, "❌ Failed to sync entry ${oldestEntry.id}: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing entry ${oldestEntry.id}", e)
            }
        } else {
            Log.d(TAG, "📭 No unsynced entries to sync")
        }
    }

    private suspend fun deleteOldSyncedEntries() {
        try {
            // Check if service is being destroyed
            if (isServiceDestroyed) {
                Log.d(TAG, "🛑 Service is being destroyed - skipping cleanup")
                return
            }
            
            // Calculate timestamp for 24 hours ago
            val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val deletedCount = database.deleteOldSyncedEntries(twentyFourHoursAgo)
            if (deletedCount > 0) {
                Log.d(TAG, "🗑️ Deleted $deletedCount old synced entries (older than 24 hours)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting old synced entries", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "🛑 Service onDestroy() called")
        
        // Set flag to prevent new operations
        isServiceDestroyed = true
        
        // Cancel all coroutine jobs first
        locationCheckJob?.cancel()
        syncJob?.cancel()
        locationJob?.cancel()
        serviceScope.cancel()
        
        // Wait a bit for jobs to finish gracefully
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            Log.d(TAG, "Interrupted while waiting for jobs to finish")
        }
        
        // Stop all monitors
        try {
            batteryMonitor.stopMonitoring()
            signalMonitor.stopMonitoring()
            satelliteMonitor.stopMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping monitors", e)
        }
        
        // Clean up network monitoring
        NetworkUtils.cleanupNetworkMonitoring(this)
        
        // Close database last
        try {
            database.close()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error closing database", e)
        }
        
        Log.d(TAG, "🛑 Service destroyed and cleaned up")
    }

    companion object {
        private const val TAG = "DataCollectionService"
        private const val NOTIFICATION_ID = 1
        private const val AUTH_TOKEN = "vtpliveviewvwep"
        // Distance filter for when to persist a new point. Adjust as needed.
        private const val LOCATION_CHANGE_THRESHOLD = 10.0f // meters
        const val ACTION_STOP_SERVICE = "com.valsgroup.vtpl.STOP_SERVICE"
        const val ACTION_PAUSE_SERVICE = "com.valsgroup.vtpl.PAUSE_SERVICE"

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

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (DataCollectionService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}