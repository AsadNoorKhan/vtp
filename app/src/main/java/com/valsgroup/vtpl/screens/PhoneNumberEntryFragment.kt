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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.valsgroup.vtpl.screens.TrackingFragment
import android.util.Log

class PhoneNumberEntryFragment : Fragment() {
    
    companion object {
        private const val AUTH_TOKEN = "vtpliveviewvwep"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_phone_number_entry, container, false)
        val phoneInput = view.findViewById<EditText>(R.id.phoneNumberInput)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        submitButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (phoneNumber.matches(Regex("^92\\d{10}$"))) {
                // Disable button to prevent multiple clicks
                submitButton.isEnabled = false
                // Show loading in logText
                val logText = view.findViewById<TextView>(R.id.logText)
                logText.text = "Verifying number..."

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val retrofit = Retrofit.Builder()
                            .baseUrl("http://avl.valstracking.com:8080")
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        val apiService = retrofit.create(ApiService::class.java)
                        val response = withContext(Dispatchers.IO) {
                            apiService.checkImeiStatus("Bearer $AUTH_TOKEN", phoneNumber)
                        }
                        
                        Log.d("PhoneNumberEntry", "🔍 API Response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                        Log.d("PhoneNumberEntry", "🔍 Response body: ${response.body()}")
                        Log.d("PhoneNumberEntry", "🔍 Error body: ${response.errorBody()?.string()}")
                        
                        if (response.isSuccessful && response.body() != null) {
                            val statusResponse = response.body()!!
                            Log.d("PhoneNumberEntry", "✅ Parsed response: status=${statusResponse.status}, imei_id=${statusResponse.imei_id}")
                            
                            if (statusResponse.status == "registered") {
                                Log.d("PhoneNumberEntry", "✅ Number is registered - proceeding to tracking")
                                // Save number and proceed
                                requireContext().getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
                                    .edit().putString("PHONE_NUMBER", phoneNumber).apply()

                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container, TrackingFragment())
                                    .commit()
                            } else {
                                Log.d("PhoneNumberEntry", "❌ Number status is: ${statusResponse.status} - not registered")
                                logText.text = ""
                                Toast.makeText(requireContext(), "Number not registered", Toast.LENGTH_LONG).show()
                                submitButton.isEnabled = true
                            }
                        } else {
                            Log.d("PhoneNumberEntry", "❌ Response not successful or body is null")
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
                Toast.makeText(requireContext(), "Enter number starting with 92 and 12 digits (e.g., 92xxxxxxxxxx)", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
} 