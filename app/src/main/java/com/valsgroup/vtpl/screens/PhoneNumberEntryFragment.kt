package com.valsgroup.vtpl.screens

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.valsgroup.vtpl.R
import com.valsgroup.vtpl.api.ApiService
import com.valsgroup.vtpl.api.DeviceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PhoneNumberEntryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_phone_number_entry, container, false)
        val phoneInput = view.findViewById<EditText>(R.id.phoneNumberInput)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        submitButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                // Disable button to prevent multiple clicks
                submitButton.isEnabled = false
                // Show loading in logText
                val logText = view.findViewById<TextView>(R.id.logText)
                logText.text = "Verifying number..."
                // Prepare minimal DeviceData payload
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val deviceDate = dateFormat.format(java.util.Date())
                val deviceData = DeviceData(
                    imei_id = phoneNumber,
                    device_date = deviceDate,
                    latitude = 0.0,
                    longitude = 0.0,
                    altitude = 0,
                    satellites = 0,
                    gsm_signal_level = 0,
                    battery_power = "N",
                    battery_level = 0,
                    battery_voltage = 0.0f,
                    external_voltage = 0.0f
                )
                CoroutineScope(Dispatchers.Main).launch {
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
                            // Save number and proceed
                            requireContext().getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
                                .edit().putString("PHONE_NUMBER", phoneNumber).apply()
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, MainFragment())
                                .commit()
                        } else {
                            logText.text = ""
                            Toast.makeText(requireContext(), "Number not registered", Toast.LENGTH_LONG).show()
                            submitButton.isEnabled = true
                        }
                    } catch (e: Exception) {
                        logText.text = ""
                        Toast.makeText(requireContext(), "Network error. Try again.", Toast.LENGTH_LONG).show()
                        submitButton.isEnabled = true
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a phone number", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
} 