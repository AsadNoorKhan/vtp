package com.valsgroup.vtpl.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.valsgroup.vtpl.api.DeviceData
import java.text.SimpleDateFormat
import java.util.*

class TrackingDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tracking_data.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "tracking_entries"
        
        // Column names
        private const val COLUMN_ID = "id"
        private const val COLUMN_IMEI_ID = "imei_id"
        private const val COLUMN_DEVICE_DATE = "device_date"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_ALTITUDE = "altitude"
        private const val COLUMN_SATELLITES = "satellites"
        private const val COLUMN_GSM_SIGNAL_LEVEL = "gsm_signal_level"
        private const val COLUMN_BATTERY_POWER = "battery_power"
        private const val COLUMN_BATTERY_LEVEL = "battery_level"
        private const val COLUMN_BATTERY_VOLTAGE = "battery_voltage"
        private const val COLUMN_EXTERNAL_VOLTAGE = "external_voltage"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_SYNCED = "synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
             CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_IMEI_ID TEXT NOT NULL,
                $COLUMN_DEVICE_DATE TEXT NOT NULL,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_ALTITUDE INTEGER NOT NULL,
                $COLUMN_SATELLITES INTEGER NOT NULL,
                $COLUMN_GSM_SIGNAL_LEVEL INTEGER NOT NULL,
                $COLUMN_BATTERY_POWER TEXT NOT NULL,
                $COLUMN_BATTERY_LEVEL INTEGER NOT NULL,
                $COLUMN_BATTERY_VOLTAGE REAL NOT NULL,
                $COLUMN_EXTERNAL_VOLTAGE REAL NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        Log.d("TrackingDatabase", "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertTrackingData(deviceData: DeviceData): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IMEI_ID, deviceData.imei_id)
            put(COLUMN_DEVICE_DATE, deviceData.device_date)
            put(COLUMN_LATITUDE, deviceData.latitude)
            put(COLUMN_LONGITUDE, deviceData.longitude)
            put(COLUMN_ALTITUDE, deviceData.altitude)
            put(COLUMN_SATELLITES, deviceData.satellites)
            put(COLUMN_GSM_SIGNAL_LEVEL, deviceData.gsm_signal_level)
            put(COLUMN_BATTERY_POWER, deviceData.battery_power)
            put(COLUMN_BATTERY_LEVEL, deviceData.battery_level)
            put(COLUMN_BATTERY_VOLTAGE, deviceData.battery_voltage)
            put(COLUMN_EXTERNAL_VOLTAGE, deviceData.external_voltage)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
            put(COLUMN_SYNCED, 0) // 0 = not synced, 1 = synced
        }

        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        
        Log.d("TrackingDatabase", "Inserted tracking data with ID: $id")
        return id
    }

    fun getUnsyncedEntries(): List<TrackingEntry> {
        val entries = mutableListOf<TrackingEntry>()
        val db = this.readableDatabase
        
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_SYNCED = ?",
            arrayOf("0"),
            null,
            null,
            "$COLUMN_CREATED_AT ASC"
        )

        while (cursor.moveToNext()) {
            val entry = TrackingEntry(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                imei_id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMEI_ID)),
                device_date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_DATE)),
                latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)),
                longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)),
                altitude = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ALTITUDE)),
                satellites = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SATELLITES)),
                gsm_signal_level = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GSM_SIGNAL_LEVEL)),
                battery_power = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BATTERY_POWER)),
                battery_level = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATTERY_LEVEL)),
                battery_voltage = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_BATTERY_VOLTAGE)),
                external_voltage = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_EXTERNAL_VOLTAGE)),
                created_at = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                synced = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SYNCED)) == 1
            )
            entries.add(entry)
        }
        
        cursor.close()
        db.close()
        return entries
    }

    fun getEntriesAfterTime(timestamp: Long): List<TrackingEntry> {
        val entries = mutableListOf<TrackingEntry>()
        val db = this.readableDatabase
        
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_CREATED_AT >= ?",
            arrayOf(timestamp.toString()),
            null,
            null,
            "$COLUMN_CREATED_AT ASC"
        )

        while (cursor.moveToNext()) {
            val entry = TrackingEntry(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                imei_id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMEI_ID)),
                device_date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_DATE)),
                latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)),
                longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)),
                altitude = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ALTITUDE)),
                satellites = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SATELLITES)),
                gsm_signal_level = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GSM_SIGNAL_LEVEL)),
                battery_power = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BATTERY_POWER)),
                battery_level = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATTERY_LEVEL)),
                battery_voltage = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_BATTERY_VOLTAGE)),
                external_voltage = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_EXTERNAL_VOLTAGE)),
                created_at = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                synced = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SYNCED)) == 1
            )
            entries.add(entry)
        }
        
        cursor.close()
        db.close()
        return entries
    }

    fun markAsSynced(id: Long) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SYNCED, 1)
        }
        
        db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        
        Log.d("TrackingDatabase", "Marked entry $id as synced")
    }

    fun getLastLocation(): Pair<Double, Double>? {
        val db = this.readableDatabase
        
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_LATITUDE, COLUMN_LONGITUDE),
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC",
            "1"
        )

        return if (cursor.moveToFirst()) {
            val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
            val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
            cursor.close()
            db.close()
            Pair(latitude, longitude)
        } else {
            cursor.close()
            db.close()
            null
        }
    }

    fun getUnsyncedCount(): Int {
        val db = this.readableDatabase
        
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME WHERE $COLUMN_SYNCED = 0", null)
        val count = if (cursor.moveToFirst()) {
            cursor.getInt(0)
        } else {
            0
        }
        
        cursor.close()
        db.close()
        return count
    }

    fun clearSyncedEntries() {
        val db = this.writableDatabase
        val deletedRows = db.delete(TABLE_NAME, "$COLUMN_SYNCED = ?", arrayOf("1"))
        db.close()
        
        Log.d("TrackingDatabase", "Cleared $deletedRows synced entries")
    }
    
    fun deleteOldSyncedEntries(olderThanTimestamp: Long): Int {
        val db = this.writableDatabase
        val deletedRows = db.delete(
            TABLE_NAME, 
            "$COLUMN_SYNCED = ? AND $COLUMN_CREATED_AT < ?", 
            arrayOf("1", olderThanTimestamp.toString())
        )
        db.close()
        
        Log.d("TrackingDatabase", "Deleted $deletedRows old synced entries (older than ${Date(olderThanTimestamp)})")
        return deletedRows
    }
    
    fun deleteOldestSyncedEntry(): Int {
        val db = this.writableDatabase
        
        // First, find the oldest synced entry
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID),
            "$COLUMN_SYNCED = ?",
            arrayOf("1"),
            null,
            null,
            "$COLUMN_CREATED_AT ASC",
            "1"
        )
        
        return if (cursor.moveToFirst()) {
            val oldestId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            cursor.close()
            
            // Delete the oldest synced entry
            val deletedRows = db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(oldestId.toString()))
            db.close()
            
            Log.d("TrackingDatabase", "Deleted oldest synced entry with ID: $oldestId")
            deletedRows
        } else {
            cursor.close()
            db.close()
            Log.d("TrackingDatabase", "No synced entries found to delete")
            0
        }
    }
    
    fun getDatabaseStats(): DatabaseStats {
        val db = this.readableDatabase
        
        val totalCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        val syncedCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME WHERE $COLUMN_SYNCED = 1", null)
        val unsyncedCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME WHERE $COLUMN_SYNCED = 0", null)
        
        val total = if (totalCursor.moveToFirst()) totalCursor.getInt(0) else 0
        val synced = if (syncedCursor.moveToFirst()) syncedCursor.getInt(0) else 0
        val unsynced = if (unsyncedCursor.moveToFirst()) unsyncedCursor.getInt(0) else 0
        
        totalCursor.close()
        syncedCursor.close()
        unsyncedCursor.close()
        db.close()
        
        return DatabaseStats(total, synced, unsynced)
    }
}

data class TrackingEntry(
    val id: Long,
    val imei_id: String,
    val device_date: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val satellites: Int,
    val gsm_signal_level: Int,
    val battery_power: String,
    val battery_level: Int,
    val battery_voltage: Float,
    val external_voltage: Float,
    val created_at: Long,
    val synced: Boolean
) {
    fun toDeviceData(): DeviceData {
        return DeviceData(
            imei_id = imei_id,
            device_date = device_date,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            satellites = satellites,
            gsm_signal_level = gsm_signal_level,
            battery_power = battery_power,
            battery_level = battery_level,
            battery_voltage = battery_voltage,
            external_voltage = external_voltage
        )
    }
}

data class DatabaseStats(
    val totalEntries: Int,
    val syncedEntries: Int,
    val unsyncedEntries: Int
) {
    val syncPercentage: Double
        get() = if (totalEntries > 0) (syncedEntries.toDouble() / totalEntries) * 100 else 0.0
} 