package com.valsgroup.vtpl.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkUtils {
    
    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkReceiver: BroadcastReceiver? = null
    
    fun initializeNetworkMonitoring(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // Use NetworkCallback for API 24+
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !_isNetworkAvailable.value
                    _isNetworkAvailable.value = true
                    Log.d("NetworkUtils", "🌐 Network available: $network")
                    if (wasOffline) {
                        Log.d("NetworkUtils", "🔄 Network restored - triggering sync")
                        onNetworkRestored?.invoke()
                    }
                }
                
                override fun onLost(network: Network) {
                    _isNetworkAvailable.value = false
                    Log.d("NetworkUtils", "📱 Network lost: $network")
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    
                    Log.d("NetworkUtils", "📊 Network capabilities changed: Internet=$hasInternet, Validated=$isValidated")
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.d("NetworkUtils", "📡 Network monitoring started (NetworkCallback)")
            
        } else {
            // Use BroadcastReceiver for older APIs
            networkReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                        val wasOffline = !_isNetworkAvailable.value
                        val isAvailable = isNetworkAvailable(context ?: return)
                        _isNetworkAvailable.value = isAvailable
                        
                        if (isAvailable && wasOffline) {
                            Log.d("NetworkUtils", "🔄 Network restored - triggering sync")
                            onNetworkRestored?.invoke()
                        }
                    }
                }
            }
            
            context.registerReceiver(
                networkReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            )
            Log.d("NetworkUtils", "📡 Network monitoring started (BroadcastReceiver)")
        }
        
        // Set initial state
        _isNetworkAvailable.value = isNetworkAvailable(context)
    }
    
    fun cleanupNetworkMonitoring(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
                networkCallback = null
                Log.d("NetworkUtils", "📡 Network monitoring stopped (NetworkCallback)")
            }
            
            networkReceiver?.let {
                context.unregisterReceiver(it)
                networkReceiver = null
                Log.d("NetworkUtils", "📡 Network monitoring stopped (BroadcastReceiver)")
            }
        } catch (e: Exception) {
            Log.e("NetworkUtils", "❌ Error cleaning up network monitoring", e)
        }
    }
    
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }
    
    fun isInternetAvailable(context: Context): Boolean {
        return isNetworkAvailable(context)
    }
    
    // Callback for when network is restored
    var onNetworkRestored: (() -> Unit)? = null
} 