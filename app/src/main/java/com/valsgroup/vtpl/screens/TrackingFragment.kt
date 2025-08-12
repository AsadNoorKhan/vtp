package com.valsgroup.vtpl.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.valsgroup.vtpl.R
import com.valsgroup.vtpl.service.DataCollectionService
import com.valsgroup.vtpl.utils.BatteryMonitor
import com.valsgroup.vtpl.utils.SignalMonitor
import com.valsgroup.vtpl.utils.SatelliteMonitor
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.valsgroup.vtpl.database.TrackingDatabase
import com.valsgroup.vtpl.database.TrackingEntry
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts

class TrackingFragment : Fragment(), OnMapReadyCallback {
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var serviceRunning: Boolean = false

    // Google Maps
    private var googleMap: GoogleMap? = null
    private var currentLocationMarker: com.google.android.gms.maps.model.Marker? = null

    // Monitor instances for getting real-time data
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var satelliteMonitor: SatelliteMonitor

    // History tracking
    private var database: TrackingDatabase? = null
    private var historyPolyline: com.google.android.gms.maps.model.Polyline? = null
    private var historyMarkers = mutableListOf<com.google.android.gms.maps.model.Marker>()
    
    // Timeline and history
    private var currentTimeline = "Current"
    private var allLocationHistory = listOf<TrackingEntry>()
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d("TrackingFragment", "✅ Location permissions granted - enabling location features")
            // Re-enable location features on the map
            googleMap?.let { map ->
                if (hasLocationPermissions()) {
                    map.isMyLocationEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = true
                    startLiveLocationUpdates()
                    loadLocationHistory()
                    centerMapOnCurrentLocation()
                }
            }
        } else {
            Log.w("TrackingFragment", "⚠️ Location permissions denied - location features remain disabled")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        // Initialize monitors
        batteryMonitor = BatteryMonitor(requireContext())
        signalMonitor = SignalMonitor(requireContext())
        satelliteMonitor = SatelliteMonitor(requireContext())

        // Start monitoring
        batteryMonitor.startMonitoring()
        signalMonitor.startMonitoring()
        satelliteMonitor.startMonitoring()

        // Initialize map
        initializeMap()

        // Initialize database
        database = TrackingDatabase(requireContext())

        // Setup timeline chips
        setupTimelineChips()
        
        startBackgroundService()
        requestPermissions()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize map
        initializeMap()
        
        // Setup timeline chips
        setupTimelineChips()
        
        // Request permissions
        requestPermissions()
        
        // Start background service
        startBackgroundService()
    }
    
    override fun onResume() {
        super.onResume()
        // Check if service is running, if not start it
        if (!DataCollectionService.isServiceRunning(requireContext())) {
            startBackgroundService()
        }
    }

    private fun initializeMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Check location permissions before enabling location features
        if (hasLocationPermissions()) {
            // Configure map settings for better accuracy
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            
            // Start location updates for map
            startLiveLocationUpdates()
            
            // Load initial location history
            loadLocationHistory()
            
            // Center map on current location if available
            centerMapOnCurrentLocation()
        } else {
            Log.w("TrackingFragment", "⚠️ Location permissions not granted - location features disabled")
            // Request permissions
            requestPermissions()
        }
        
        // Configure other map settings that don't require location
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        
        // Set map type to normal for better detail
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        
        Log.d("TrackingFragment", "Map is ready")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop monitoring when fragment is destroyed
        batteryMonitor.stopMonitoring()
        signalMonitor.stopMonitoring()
        satelliteMonitor.stopMonitoring()
        stopLiveLocationUpdates()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (!hasPermissions(permissions)) {
            permissionLauncher.launch(permissions)
        }
    }
    
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun startBackgroundService() {
        try {
            Log.d("TrackingFragment", "🚀 Starting background service...")
            if (!serviceRunning) {
                DataCollectionService.startService(requireContext())
                serviceRunning = true
                Log.d("TrackingFragment", "✅ Background service started successfully")
            }
        } catch (e: Exception) {
            Log.e("TrackingFragment", "❌ Error starting background service", e)
        }
    }

    private fun setupTimelineChips() {
        Log.d("TrackingFragment", "🔧 Setting up timeline chips...")
        
        val chipGroup = view?.findViewById<ChipGroup>(R.id.timeline_chip_group)
        
        chipGroup?.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.first())
            currentTimeline = chip.text.toString()
            Log.d("TrackingFragment", "⏰ Timeline changed to: $currentTimeline")
            updateMapWithHistory()
        }
        
        Log.d("TrackingFragment", "✅ Timeline chips setup complete")
    }
    
    private fun updateMapWithHistory() {
        if (googleMap == null) return
        
        Log.d("TrackingFragment", "🗺️ Updating map with history for timeline: $currentTimeline")
        
        // Clear existing history and show loading message
        clearHistoryAndShowMessage(currentTimeline)
        
        val now = System.currentTimeMillis()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        
        fun within(hours: Long): (TrackingEntry) -> Boolean = { entry ->
            try {
                val entryTime = dateFormat.parse(entry.device_date)?.time ?: 0L
                now - entryTime <= hours * 60L * 60L * 1000L
            } catch (e: Exception) {
                Log.e("TrackingFragment", "❌ Error parsing date: ${entry.device_date}", e)
                false
            }
        }
        
        fun withinMinutes(minutes: Long): (TrackingEntry) -> Boolean = { entry ->
            try {
                val entryTime = dateFormat.parse(entry.device_date)?.time ?: 0L
                now - entryTime <= minutes * 60L * 1000L
            } catch (e: Exception) {
                Log.e("TrackingFragment", "❌ Error parsing date: ${entry.device_date}", e)
                false
            }
        }

        // Filter locations based on timeline
        val filteredLocations = when (currentTimeline) {
            "Last 30 Minutes" -> allLocationHistory.filter(withinMinutes(30))
            "Last Hour" -> allLocationHistory.filter(within(1))
            "Last 6 Hours" -> allLocationHistory.filter(within(6))
            "Last 12 Hours" -> allLocationHistory.filter(within(12))
            "Last 18 Hours" -> allLocationHistory.filter(within(18))
            "Last 24 Hours" -> allLocationHistory.filter(within(24))
            "Current" -> emptyList()
            else -> emptyList()
        }
        
        Log.d("TrackingFragment", "📊 Filtered ${filteredLocations.size} locations for timeline: $currentTimeline")
        
        // No history markers - we only draw the route polyline
        
        // Draw polyline only (no history markers), connect from first point to current if available
        if (filteredLocations.size >= 1) {
            val points = filteredLocations.map { LatLng(it.latitude, it.longitude) }.toMutableList()
            // Append current location at the end if we have it
            currentLocationMarker?.position?.let { currentPos ->
                points.add(LatLng(currentPos.latitude, currentPos.longitude))
            }
            val polylineOptions = PolylineOptions()
                .addAll(points)
                .width(8f)
                .color(requireContext().getColor(android.R.color.holo_blue_dark))
                .geodesic(true)
            
            historyPolyline = googleMap?.addPolyline(polylineOptions)
        }
        
        // Update location history for dialog (visible subset)
    }
    
    private fun loadLocationHistory() {
        Log.d("TrackingFragment", "📊 Loading location history from database...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load all locations from the last year
                val cutoffTime = System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L)
                val allLocations = database?.getEntriesAfterTime(cutoffTime) ?: emptyList()
                
                withContext(Dispatchers.Main) {
                    allLocationHistory = allLocations
                    Log.d("TrackingFragment", "📊 Loaded ${allLocations.size} locations from database")
                    updateMapWithHistory()
                }
            } catch (e: Exception) {
                Log.e("TrackingFragment", "❌ Error loading location history", e)
            }
        }
    }
    


    private fun startLiveLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    updateMapLocation(location)
                    Log.d("TrackingFragment", "📍 Location updated: ${location.latitude}, ${location.longitude}, Accuracy: ${location.accuracy}m")
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            
            try { 
                // Request high-accuracy GPS updates with shorter intervals
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    1000L, // 1 second
                    0f, // 0 meters minimum distance
                    locationListener!!
                ) 
            } catch (e: Exception) {
                Log.e("TrackingFragment", "Error requesting GPS location updates", e)
            }
            
            try { 
                // Also request network provider updates as backup
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 
                    2000L, // 2 seconds
                    5f, // 5 meters minimum distance
                    locationListener!!
                ) 
            } catch (e: Exception) {
                Log.e("TrackingFragment", "Error requesting network location updates", e)
            }
            
            // Try to get last known location immediately
            try {
                val lastKnownLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnownLocation != null) {
                    updateMapLocation(lastKnownLocation)
                    Log.d("TrackingFragment", "📍 Last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                }
            } catch (e: Exception) {
                Log.e("TrackingFragment", "Error getting last known location", e)
            }
        }
    }
    
    private fun updateMapLocation(location: Location) {
        googleMap?.let { map ->
            val latLng = LatLng(location.latitude, location.longitude)
            
            // Update or create marker
            if (currentLocationMarker == null) {
                currentLocationMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Current Location")
                        .snippet("Accuracy: ${String.format("%.1f", location.accuracy)}m")
                )
                // Move camera to current location on first update with higher zoom
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
                Log.d("TrackingFragment", "📍 First location - centered map on: ${latLng.latitude}, ${latLng.longitude}")
            } else {
                currentLocationMarker?.position = latLng
                currentLocationMarker?.snippet = "Accuracy: ${String.format("%.1f", location.accuracy)}m"
                // Only center if this is a significant location change (more than 50 meters)
                val lastPos = currentLocationMarker?.position
                if (lastPos != null) {
                    val distance = location.distanceTo(android.location.Location("").apply {
                        latitude = lastPos.latitude
                        longitude = lastPos.longitude
                    })
                    if (distance > 50) {
                        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                        Log.d("TrackingFragment", "📍 Significant location change - recentered map")
                    }
                }
            }
        }
    }
    
    private fun stopLiveLocationUpdates() {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
    }
    

    private fun clearHistory() {
        Log.d("TrackingFragment", "🧹 Clearing history from map and list")
        
        // Remove all history markers
        historyMarkers.forEach { marker ->
            marker.remove()
        }
        historyMarkers.clear()
        
        // Remove polyline
        historyPolyline?.remove()
        historyPolyline = null
        
        // Reset camera to current location if available
        currentLocationMarker?.let { marker ->
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
        }
    }
    
    private fun clearHistoryAndShowMessage(timeline: String) {
        clearHistory()
        Toast.makeText(requireContext(), "Loading $timeline...", Toast.LENGTH_SHORT).show()
    }

    private fun centerMapOnCurrentLocation() {
        googleMap?.let { map ->
            currentLocationMarker?.position?.let { currentPos ->
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 18f))
            }
        }
    }
    
    /**
     * Public method to center the map on current location
     * Can be called from UI elements or other parts of the app
     */
    fun centerOnCurrentLocation() {
        currentLocationMarker?.position?.let { currentPos ->
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 18f))
            Log.d("TrackingFragment", "📍 Manually centered map on current location")
        } ?: run {
            Log.d("TrackingFragment", "📍 No current location available to center on")
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        // No constants needed
    }
} 