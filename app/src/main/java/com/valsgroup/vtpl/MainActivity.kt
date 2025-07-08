package com.valsgroup.vtpl

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.valsgroup.vtpl.api.ApiService
import com.valsgroup.vtpl.api.DeviceData
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.valsgroup.vtpl.screens.PhoneNumberEntryFragment
import com.valsgroup.vtpl.screens.MainFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("VTPL_PREFS", Context.MODE_PRIVATE)
        val storedPhoneNumber = prefs.getString("PHONE_NUMBER", null)

        if (savedInstanceState == null) {
            if (storedPhoneNumber == null) {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, PhoneNumberEntryFragment())
                }
            } else {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, MainFragment())
                }
            }
        }
    }
}