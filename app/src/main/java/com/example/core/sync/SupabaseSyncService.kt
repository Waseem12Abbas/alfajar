package com.example.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.models.VehiclePass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SupabaseSyncService {
    private const val TAG = "SupabaseSync"
    private val client = OkHttpClient()

    /**
     * Standard utility to check if Internet is active on the device.
     */
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieve the unique string ID of this device.
     */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
    }

    /**
     * Conducts a full automated synchronization (PUSH then PULL).
     */
    suspend fun sync(context: Context, onStatusUpdate: ((String) -> Unit)? = null): Boolean {
        val secureStorage = SecureStorageService(context)
        val url = secureStorage.getSupabaseUrl()
        val key = secureStorage.getSupabaseAnonKey()

        if (url.isNullOrEmpty() || key.isNullOrEmpty()) {
            Log.d(TAG, "Sync skipped: Supabase credentials not configured.")
            onStatusUpdate?.invoke("Credentials Not Configured")
            return false
        }

        if (!isOnline(context)) {
            Log.d(TAG, "Sync skipped: Device offline.")
            onStatusUpdate?.invoke("Offline")
            return false
        }

        return try {
            onStatusUpdate?.invoke("Syncing...")
            Log.d(TAG, "Starting push local passes to cloud...")
            val pushSuccess = pushToCloud(context, url, key)
            if (!pushSuccess) {
                Log.e(TAG, "Push failed, continuing to pull anyway.")
            }

            Log.d(TAG, "Starting pull passes from cloud...")
            val pullSuccess = pullFromCloud(context, url, key)
            
            if (pullSuccess) {
                Log.d(TAG, "Sync completed successfully.")
                onStatusUpdate?.invoke("Synced")
                true
            } else {
                Log.e(TAG, "Pull failed.")
                onStatusUpdate?.invoke("Sync Error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync execution crashed", e)
            onStatusUpdate?.invoke("Sync Error")
            false
        }
    }

    /**
     * Sends local modifications since last sync to Supabase.
     */
    private suspend fun pushToCloud(context: Context, url: String, key: String): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context)
            val secureStorage = SecureStorageService(context)
            val lastSyncTime = secureStorage.getLastSyncTime()
            val deviceId = getDeviceId(context)

            // Select local records that are newer than last sync time
            val recordsToPush = db.vehiclePassDao().getModifiedSince(lastSyncTime)
            if (recordsToPush.isEmpty()) {
                Log.d(TAG, "Push: No local changes to sync.")
                return true
            }

            Log.d(TAG, "Pushing ${recordsToPush.size} local records to cloud.")

            val jsonArray = JSONArray()
            for (record in recordsToPush) {
                val obj = JSONObject().apply {
                    put("unique_id", record.unique_id)
                    put("owner_name", record.owner_name)
                    put("cnic", record.cnic)
                    put("vehicle_no", record.vehicle_no)
                    put("phone", record.phone ?: JSONObject.NULL)
                    put("expiry_date", record.expiry_date)
                    put("created_at", record.created_at)
                    put("is_revoked", record.is_revoked)
                    // If device_id is empty, associate with this device ID
                    put("device_id", record.device_id.ifEmpty { deviceId })
                    // Standard ISO timestamp for last_modified
                    put("last_modified", record.last_modified.ifEmpty { VehiclePass.getCurrentIsoTimestamp() })
                }
                jsonArray.put(obj)
            }

            // Perform REST upsert call
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonArray.toString().toRequestBody(mediaType)
            val endpoint = "$url/rest/v1/vehicle_passes"

            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Prefer", "resolution=merge-duplicates") // postgrest upsert instruction
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Push successful: ${response.code}")
                    true
                } else {
                    Log.e(TAG, "Push error returned from Supabase: ${response.code} / ${response.body?.string()}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Push operation crashed", e)
            false
        }
    }

    /**
     * Pulls newer cloud modifications from other peers and merges them locally.
     */
    private suspend fun pullFromCloud(context: Context, url: String, key: String): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context)
            val secureStorage = SecureStorageService(context)
            val lastSyncTime = secureStorage.getLastSyncTime()
            val deviceId = getDeviceId(context)

            // Select where last_modified > lastSyncTime and device_id != deviceId
            val endpoint = "$url/rest/v1/vehicle_passes?last_modified=gt.$lastSyncTime&device_id=neq.$deviceId"
            Log.d(TAG, "Pull URL: $endpoint")

            val request = Request.Builder()
                .url(endpoint)
                .get()
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .build()

            var responseSuccess = false
            var currentSyncCompletedTime = VehiclePass.getCurrentIsoTimestamp()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    responseSuccess = true
                    val responseBody = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(responseBody)
                    
                    Log.d(TAG, "Pulled ${jsonArray.length()} newer records from cloud.")

                    if (jsonArray.length() > 0) {
                        for (i in 0 until jsonArray.length()) {
                            val row = jsonArray.getJSONObject(i)
                            val uid = row.getString("unique_id")
                            val cloudLastModified = row.getString("last_modified")

                            // Retrieve local record if exists
                            val localRecord = db.vehiclePassDao().getPassByUniqueId(uid)
                            
                            val recordToSave = VehiclePass(
                                id = localRecord?.id ?: 0, // preserve local sqlite ID if exists
                                unique_id = uid,
                                owner_name = row.getString("owner_name"),
                                cnic = row.getString("cnic"),
                                vehicle_no = row.getString("vehicle_no"),
                                phone = if (row.isNull("phone")) null else row.getString("phone"),
                                expiry_date = row.getString("expiry_date"),
                                created_by = localRecord?.created_by, // retain original creator if any
                                created_at = row.getString("created_at"),
                                is_revoked = row.getInt("is_revoked"),
                                last_modified = cloudLastModified,
                                device_id = row.getString("device_id")
                            )

                            if (localRecord == null) {
                                // Direct insert
                                db.vehiclePassDao().replacePass(recordToSave)
                                Log.d(TAG, "Pull: Inserted new record $uid")
                            } else {
                                // Conflict resolution: Compare timestamps
                                val isCloudNewer = try {
                                    val localTime = parseIsoTimestamp(localRecord.last_modified)
                                    val cloudTime = parseIsoTimestamp(cloudLastModified)
                                    cloudTime.after(localTime)
                                } catch (e: Exception) {
                                    // Fallback to lexicographical check
                                    cloudLastModified > localRecord.last_modified
                                }

                                if (isCloudNewer) {
                                    db.vehiclePassDao().replacePass(recordToSave)
                                    Log.d(TAG, "Pull: Conflict resolved by overriding local with cloud $uid")
                                } else {
                                    Log.d(TAG, "Pull: Local is newer or equal, keeping local for $uid")
                                }
                            }
                        }
                    }

                    // Save last synchronized stamp
                    secureStorage.setLastSyncTime(currentSyncCompletedTime)
                } else {
                    Log.e(TAG, "Pull error from Supabase: ${response.code} / ${response.body?.string()}")
                }
            }
            responseSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Pull operation crashed", e)
            false
        }
    }

    /**
     * Parses standard ISO timestamp safely.
     */
    private fun parseIsoTimestamp(timestamp: String): Date {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(timestamp.substring(0, 19)) ?: Date(0)
        } catch (e: Exception) {
            try {
                // Secondary fallback format
                val sdf2 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf2.parse(timestamp) ?: Date(0)
            } catch (e2: Exception) {
                Date(0)
            }
        }
    }
}
