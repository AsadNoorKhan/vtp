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
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.valsgroup.vtpl.R
import com.valsgroup.vtpl.api.ApiService
import com.valsgroup.vtpl.api.DeviceData
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainFragment : Fragment() {
    private lateinit var statusText: TextView
    private lateinit var permissionText: TextView
    private lateinit var serviceText: TextView
    private lateinit var statusLayout: LinearLayout
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var altitudeText: TextView
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private lateinit var modeSpinner: MaterialAutoCompleteTextView
    private lateinit var spinnerLayout: TextInputLayout
    private var selectedMode: String = "Normal"
    private var serviceRunning: Boolean = false
    private var lastServiceStartTime: Long = 0
    private val MIN_SERVICE_LIFETIME = 2000L // 2 seconds minimum
    private lateinit var attendanceLayout: LinearLayout
    private lateinit var timeInCard: View
    private lateinit var timeOutCard: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)
        statusText = view.findViewById(R.id.statusText)
        permissionText = view.findViewById(R.id.permissionText)
        serviceText = view.findViewById(R.id.serviceText)
        statusLayout = view.findViewById(R.id.statusLayout)
        latitudeText = view.findViewById(R.id.latitudeText)
        longitudeText = view.findViewById(R.id.longitudeText)
        altitudeText = view.findViewById(R.id.altitudeText)
        spinnerLayout = view.findViewById(R.id.spinnerLayout)
        modeSpinner = view.findViewById(R.id.modeSpinner)
        attendanceLayout = view.findViewById(R.id.attendanceLayout)
        timeInCard = attendanceLayout.getChildAt(0)
        timeOutCard = attendanceLayout.getChildAt(1)

        val prefs = requireContext().getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
        selectedMode = prefs.getString("MODE", "Normal") ?: "Normal"
        val modes = listOf("Normal", "Realtime", "Attendance")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, modes)
        modeSpinner.setAdapter(adapter)
        spinnerLayout.hint = "Select Mode"
        modeSpinner.setText(selectedMode, false)
        modeSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedMode = modes[position]
            prefs.edit().putString("MODE", selectedMode).apply()
            Log.d("MainFragment", "🔄 Mode changed to: $selectedMode")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateModeUI()
            }, 100)
        }
        updateModeUI()
        timeInCard.setOnClickListener { sendAttendancePayload("clock_in") }
        timeOutCard.setOnClickListener { sendAttendancePayload("clock_out") }
        requestPermissions()
        return view
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (!hasPermissions(permissions)) {
            statusText.text = "Requesting permissions..."
            permissionText.text = "Permissions: Requesting..."
            ActivityCompat.requestPermissions(requireActivity(), permissions, PERMISSION_REQUEST_CODE)
        } else {
            statusText.text = "Permissions granted"
            permissionText.text = "Permissions: Granted"
        }
    }
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun updateModeUI() {
        when (selectedMode) {
            "Normal" -> {
                statusLayout.visibility = View.VISIBLE
                attendanceLayout.visibility = View.GONE
                startBackgroundService()
                latitudeText.visibility = View.GONE
                longitudeText.visibility = View.GONE
                altitudeText.visibility = View.GONE
                stopLiveLocationUpdates()
            }
            "Realtime" -> {
                statusLayout.visibility = View.VISIBLE
                attendanceLayout.visibility = View.GONE
                startBackgroundService()
                latitudeText.visibility = View.VISIBLE
                longitudeText.visibility = View.VISIBLE
                altitudeText.visibility = View.VISIBLE
                startLiveLocationUpdates()
            }
            "Attendance" -> {
                statusLayout.visibility = View.GONE
                attendanceLayout.visibility = View.VISIBLE
                stopBackgroundService()
                stopLiveLocationUpdates()
            }
        }
    }
    private fun startBackgroundService() {
        try {
            Log.d("MainFragment", "🚀 Attempting to start background service...")
            if (selectedMode == "Attendance") {
                Log.d("MainFragment", "⚠️ Service not needed for Attendance mode, skipping start")
                return
            }
            if (serviceRunning) {
                Log.d("MainFragment", "⚠️ Service already running, skipping start")
                return
            }
            com.valsgroup.vtpl.service.DataCollectionService.startService(requireContext())
            serviceRunning = true
            lastServiceStartTime = System.currentTimeMillis()
            serviceText.text = "Service: Running"
            statusText.text = "Data collection active"
            Log.d("MainFragment", "✅ Background service started successfully at $lastServiceStartTime")
        } catch (e: Exception) {
            serviceText.text = "Service: Failed to start"
            Log.e("MainFragment", "❌ Error starting background service", e)
        }
    }
    private fun stopBackgroundService() {
        try {
            Log.d("MainFragment", "🛑 Attempting to stop background service...")
            if (!serviceRunning) {
                Log.d("MainFragment", "⚠️ Service not running, skipping stop")
                return
            }
            val currentTime = System.currentTimeMillis()
            val serviceLifetime = currentTime - lastServiceStartTime
            if (serviceLifetime < MIN_SERVICE_LIFETIME) {
                Log.d("MainFragment", "⏰ Service lifetime (${serviceLifetime}ms) < minimum (${MIN_SERVICE_LIFETIME}ms), delaying stop")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d("MainFragment", "⏰ Minimum lifetime reached, stopping service now")
                    performServiceStop()
                }, MIN_SERVICE_LIFETIME - serviceLifetime)
                return
            }
            performServiceStop()
        } catch (e: Exception) {
            Log.e("MainFragment", "❌ Error stopping background service", e)
        }
    }
    private fun performServiceStop() {
        try {
            com.valsgroup.vtpl.service.DataCollectionService.stopService(requireContext())
            serviceRunning = false
            serviceText.text = "Service: Stopped"
            Log.d("MainFragment", "✅ Background service stopped successfully")
        } catch (e: Exception) {
            Log.e("MainFragment", "❌ Error in performServiceStop", e)
        }
    }
    private fun startLiveLocationUpdates() {
        if (selectedMode != "Realtime") return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    latitudeText.text = "Latitude: ${location.latitude}"
                    longitudeText.text = "Longitude: ${location.longitude}"
                    altitudeText.text = "Altitude: ${location.altitude}"
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try { locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener!!) } catch (e: Exception) {}
            try { locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener!!) } catch (e: Exception) {}
        }
    }
    private fun stopLiveLocationUpdates() {
        locationManager?.removeUpdates(locationListener!!)
    }
    private fun sendAttendancePayload(type: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val prefs = requireContext().getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
            val phoneNumber = prefs.getString("PHONE_NUMBER", "Unknown") ?: "Unknown"
            val location = getLastKnownLocation()
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val deviceDate = dateFormat.format(java.util.Date())
            val deviceData = DeviceData(
                imei_id = phoneNumber,
                device_date = deviceDate,
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                altitude = location?.altitude?.toInt() ?: 0,
                satellites = 18,
                gsm_signal_level = 3,
                battery_power = "N",
                battery_level = 100,
                battery_voltage = 4.2f,
                external_voltage = 0.0f
            )
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://avl.valstracking.com:8080")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val apiService = retrofit.create(ApiService::class.java)
                val response = withContext(Dispatchers.IO) {
                    apiService.sendDeviceData("Bearer vtpliveviewvwep", "application/json", deviceData)
                }
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), if (type == "clock_in") "Time In sent!" else "Time Out sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to send attendance", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error sending attendance", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun getLastKnownLocation(): Location? {
        return try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            null
        }
    }
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }
} 