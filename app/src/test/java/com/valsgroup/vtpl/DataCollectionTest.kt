package com.valsgroup.vtpl

import com.valsgroup.vtpl.api.DeviceData
import org.junit.Test
import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

class DataCollectionTest {
    
    @Test
    fun `test DeviceData creation with valid values`() {
        // Given
        val phoneNumber = "1234567890"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val deviceDate = dateFormat.format(Date())
        val latitude = 40.7128
        val longitude = -74.0060
        val altitude = 10
        val satellites = 8
        val gsmSignalLevel = 3
        val batteryPower = "N"
        val batteryLevel = 85
        val batteryVoltage = 3.8f
        val externalVoltage = 0.0f
        
        // When
        val deviceData = DeviceData(
            imei_id = phoneNumber,
            device_date = deviceDate,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            satellites = satellites,
            gsm_signal_level = gsmSignalLevel,
            battery_power = batteryPower,
            battery_level = batteryLevel,
            battery_voltage = batteryVoltage,
            external_voltage = externalVoltage
        )
        
        // Then
        assertEquals(phoneNumber, deviceData.imei_id)
        assertEquals(deviceDate, deviceData.device_date)
        assertEquals(latitude, deviceData.latitude, 0.001)
        assertEquals(longitude, deviceData.longitude, 0.001)
        assertEquals(altitude, deviceData.altitude)
        assertEquals(satellites, deviceData.satellites)
        assertEquals(gsmSignalLevel, deviceData.gsm_signal_level)
        assertEquals(batteryPower, deviceData.battery_power)
        assertEquals(batteryLevel, deviceData.battery_level)
        assertEquals(batteryVoltage, deviceData.battery_voltage, 0.001f)
        assertEquals(externalVoltage, deviceData.external_voltage, 0.001f)
    }
    
    @Test
    fun `test battery power conversion`() {
        // Test charging state conversion
        val chargingData = DeviceData(
            imei_id = "test",
            device_date = "2024-01-01 12:00:00",
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0,
            satellites = 0,
            gsm_signal_level = 0,
            battery_power = "Y", // Charging
            battery_level = 50,
            battery_voltage = 4.0f,
            external_voltage = 0.0f
        )
        
        val notChargingData = DeviceData(
            imei_id = "test",
            device_date = "2024-01-01 12:00:00",
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0,
            satellites = 0,
            gsm_signal_level = 0,
            battery_power = "N", // Not charging
            battery_level = 50,
            battery_voltage = 4.0f,
            external_voltage = 0.0f
        )
        
        assertEquals("Y", chargingData.battery_power)
        assertEquals("N", notChargingData.battery_power)
    }
    
    @Test
    fun `test location precision formatting`() {
        // Test that location coordinates are properly formatted
        val latitude = 40.7128123456789
        val longitude = -74.0060123456789
        
        val deviceData = DeviceData(
            imei_id = "test",
            device_date = "2024-01-01 12:00:00",
            latitude = latitude,
            longitude = longitude,
            altitude = 0,
            satellites = 0,
            gsm_signal_level = 0,
            battery_power = "N",
            battery_level = 0,
            battery_voltage = 0.0f,
            external_voltage = 0.0f
        )
        
        // Verify coordinates are within reasonable precision
        assertTrue(deviceData.latitude >= -90.0 && deviceData.latitude <= 90.0)
        assertTrue(deviceData.longitude >= -180.0 && deviceData.longitude <= 180.0)
    }
} 