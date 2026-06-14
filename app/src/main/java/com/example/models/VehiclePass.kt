package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Entity(tableName = "vehicle_passes")
data class VehiclePass(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val unique_id: String, // UUID v4
    val owner_name: String,
    val cnic: String, // format: 00000-0000000-0
    val vehicle_no: String,
    val phone: String?,
    val expiry_date: String, // ISO 8601 YYYY-MM-DD
    val created_by: Int?,
    val created_at: String,
    val is_revoked: Int = 0, // 0=active, 1=revoked
    val last_modified: String = getCurrentIsoTimestamp(),
    val device_id: String = ""
) {
    companion object {
        fun getCurrentIsoTimestamp(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}
