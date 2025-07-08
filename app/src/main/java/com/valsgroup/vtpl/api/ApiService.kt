package com.valsgroup.vtpl.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header

interface ApiService {
    @POST("/mobdata")
    suspend fun sendDeviceData(
        @Header("Authorization") authToken: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body data: DeviceData
    ): Response<Unit>
}

data class DeviceData(
    val imei_id: String,
    val device_date: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val satellites: Int,
    val gsm_signal_level: Int,
    val battery_power: String,  // "Y" or "N"
    val battery_level: Int,
    val battery_voltage: Float,
    val external_voltage: Float
)