package com.example.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.models.VehiclePass
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FirebaseInit {
    private const val TAG = "FirebaseInit"

    fun getFirestore(context: Context): FirebaseFirestore? {
        val secureStorage = SecureStorageService(context)
        val projId = secureStorage.getFirebaseProjectId() ?: ""
        val apiKey = secureStorage.getFirebaseApiKey() ?: ""
        val appId = secureStorage.getFirebaseAppId() ?: ""

        if (projId.isEmpty() || apiKey.isEmpty() || appId.isEmpty()) {
            Log.d(TAG, "Firebase not fully configured. Checking if default Firebase app exists.")
            val apps = try { FirebaseApp.getApps(context) } catch (e: Exception) { emptyList<FirebaseApp>() }
            if (apps.isEmpty()) {
                Log.d(TAG, "No default FirebaseApp found and custom credentials are not configured yet.")
                return null
            }
            return try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "No active default Firebase app initialization available.")
                null
            }
        }

        return try {
            val options = FirebaseOptions.Builder()
                .setProjectId(projId)
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .build()

            val appName = "dynamic_app"
            val app = try {
                val existingApp = try { FirebaseApp.getInstance(appName) } catch (e: Exception) { null }
                if (existingApp != null) {
                    existingApp
                } else {
                    FirebaseApp.initializeApp(context.applicationContext, options, appName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize custom named FirebaseApp: ${e.message}")
                val defaultApps = try { FirebaseApp.getApps(context) } catch (ex: Exception) { emptyList<FirebaseApp>() }
                if (defaultApps.isNotEmpty()) {
                    FirebaseApp.getInstance()
                } else {
                    FirebaseApp.initializeApp(context.applicationContext, options)
                }
            }
            FirebaseFirestore.getInstance(app)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing dynamic Firebase App", e)
            try {
                val apps = try { FirebaseApp.getApps(context) } catch (ex: Exception) { emptyList<FirebaseApp>() }
                if (apps.isNotEmpty()) {
                    FirebaseFirestore.getInstance()
                } else {
                    null
                }
            } catch (ex: Exception) {
                null
            }
        }
    }
}

object FirebaseSyncService {
    private const val TAG = "FirebaseSync"

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

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
    }

    suspend fun sync(context: Context, onStatusUpdate: ((String) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        val secureStorage = SecureStorageService(context)
        val projId = secureStorage.getFirebaseProjectId() ?: ""

        // Quick fallback check
        val firestore = FirebaseInit.getFirestore(context)
        if (firestore == null) {
            Log.d(TAG, "Sync skipped: Firestore not configured and default init failed.")
            onStatusUpdate?.let {
                withContext(Dispatchers.Main) { it("Credentials Not Configured") }
            }
            return@withContext false
        }

        if (!isOnline(context)) {
            Log.d(TAG, "Sync skipped: Device offline.")
            onStatusUpdate?.let {
                withContext(Dispatchers.Main) { it("Offline") }
            }
            return@withContext false
        }

        try {
            onStatusUpdate?.let {
                withContext(Dispatchers.Main) { it("Syncing...") }
            }

            Log.d(TAG, "Starting push local passes to Firestore...")
            val pushSuccess = pushToCloud(context, firestore)
            if (!pushSuccess) {
                Log.e(TAG, "Push to Firestore failed, continuing anyway.")
            }

            Log.d(TAG, "Starting pull passes from Firestore...")
            val pullSuccess = pullFromCloud(context, firestore)

            if (pullSuccess) {
                Log.d(TAG, "Sync completed successfully.")
                onStatusUpdate?.let {
                    withContext(Dispatchers.Main) { it("Synced") }
                }
                true
            } else {
                Log.e(TAG, "Pull from Firestore failed.")
                onStatusUpdate?.let {
                    withContext(Dispatchers.Main) { it("Sync Error") }
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync execution crashed", e)
            onStatusUpdate?.let {
                withContext(Dispatchers.Main) { it("Sync Error") }
            }
            false
        }
    }

    private suspend fun pushToCloud(context: Context, firestore: FirebaseFirestore): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context)
            val secureStorage = SecureStorageService(context)
            val lastSyncTime = secureStorage.getLastSyncTime()
            val deviceId = getDeviceId(context)

            // Select local records that are newer than last sync-time
            val recordsToPush = db.vehiclePassDao().getModifiedSince(lastSyncTime)
            if (recordsToPush.isEmpty()) {
                Log.d(TAG, "Push: No local changes to sync.")
                return true
            }

            Log.d(TAG, "Pushing ${recordsToPush.size} local records to Firestore.")

            val batchSize = 400
            var batch = firestore.batch()
            var count = 0

            for (record in recordsToPush) {
                val docRef = firestore.collection("vehicle_passes").document(record.unique_id)
                val recordDeviceId = record.device_id.ifEmpty { deviceId }
                val recordLastModified = record.last_modified.ifEmpty { VehiclePass.getCurrentIsoTimestamp() }

                val createdAtDate = parseIsoTimestamp(record.created_at)
                val lastModDate = parseIsoTimestamp(recordLastModified)

                val data = hashMapOf(
                    "unique_id" to record.unique_id,
                    "owner_name" to record.owner_name,
                    "cnic" to record.cnic,
                    "vehicle_no" to record.vehicle_no,
                    "phone" to (record.phone ?: ""),
                    "expiry_date" to record.expiry_date,
                    "is_revoked" to record.is_revoked,
                    "created_at" to Timestamp(createdAtDate),
                    "last_modified" to Timestamp(lastModDate),
                    "device_id" to recordDeviceId
                )

                batch.set(docRef, data, SetOptions.merge())
                count++

                if (count % batchSize == 0) {
                    Tasks.await(batch.commit())
                    batch = firestore.batch()
                }
            }

            if (count % batchSize != 0) {
                Tasks.await(batch.commit())
            }

            // Write sync log
            val logDocRef = firestore.collection("sync_logs").document(deviceId)
            val logData = hashMapOf(
                "device_id" to deviceId,
                "last_synced_at" to Timestamp.now()
            )
            Tasks.await(logDocRef.set(logData, SetOptions.merge()))

            Log.d(TAG, "Push completed for $count records.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Push operation to Firestore failed", e)
            false
        }
    }

    private suspend fun pullFromCloud(context: Context, firestore: FirebaseFirestore): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context)
            val secureStorage = SecureStorageService(context)
            val lastSyncTime = secureStorage.getLastSyncTime()
            val deviceId = getDeviceId(context)

            val lastSyncDate = parseIsoTimestamp(lastSyncTime)
            val lastSyncTimestamp = Timestamp(lastSyncDate)

            // Select elements where last_modified > lastSyncTimestamp and device_id != our deviceId
            val queryTask = firestore.collection("vehicle_passes")
                .whereGreaterThan("last_modified", lastSyncTimestamp)
                .get()

            val snapshot = Tasks.await(queryTask)
            var count = 0
            val currentSyncCompletedTime = VehiclePass.getCurrentIsoTimestamp()

            for (doc in snapshot.documents) {
                val cloudDeviceId = doc.getString("device_id") ?: ""
                // Filter out changes from self
                if (cloudDeviceId == deviceId) {
                    continue
                }

                val uid = doc.getString("unique_id") ?: doc.id
                val ownerName = doc.getString("owner_name") ?: ""
                val cnic = doc.getString("cnic") ?: ""
                val vehicleNo = doc.getString("vehicle_no") ?: ""
                val phone = doc.getString("phone")
                val expiryDate = doc.getString("expiry_date") ?: ""
                val isRevoked = doc.getLong("is_revoked")?.toInt() ?: 0

                val createdAtTimestamp = doc.getTimestamp("created_at")
                val lastModifiedTimestamp = doc.getTimestamp("last_modified")

                val createdAtStr = if (createdAtTimestamp != null) formatIsoTimestamp(createdAtTimestamp.toDate()) else currentSyncCompletedTime
                val lastModifiedStr = if (lastModifiedTimestamp != null) formatIsoTimestamp(lastModifiedTimestamp.toDate()) else currentSyncCompletedTime

                // Retrieve local record if exists
                val localRecord = db.vehiclePassDao().getPassByUniqueId(uid)

                val recordToSave = VehiclePass(
                    id = localRecord?.id ?: 0, // preserve local sqlite ID if exists
                    unique_id = uid,
                    owner_name = ownerName,
                    cnic = cnic,
                    vehicle_no = vehicleNo,
                    phone = if (phone.isNullOrEmpty()) null else phone,
                    expiry_date = expiryDate,
                    created_by = localRecord?.created_by, // retain original creator if any
                    created_at = createdAtStr,
                    is_revoked = isRevoked,
                    last_modified = lastModifiedStr,
                    device_id = cloudDeviceId
                )

                if (localRecord == null) {
                    db.vehiclePassDao().replacePass(recordToSave)
                    Log.d(TAG, "Pull: Inserted new record $uid")
                } else {
                    // Conflict resolution: Compare timestamps
                    val isCloudNewer = try {
                        val localTime = parseIsoTimestamp(localRecord.last_modified)
                        val cloudTime = parseIsoTimestamp(lastModifiedStr)
                        cloudTime.after(localTime)
                    } catch (e: Exception) {
                        lastModifiedStr > localRecord.last_modified
                    }

                    if (isCloudNewer) {
                        db.vehiclePassDao().replacePass(recordToSave)
                        Log.d(TAG, "Pull: Conflict resolved by overriding local with cloud $uid")
                    } else {
                        Log.d(TAG, "Pull: Local is newer or equal, keeping local for $uid")
                    }
                }
                count++
            }

            Log.d(TAG, "Pulled and processed $count newer records from Firestore.")
            secureStorage.setLastSyncTime(currentSyncCompletedTime)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pull operation from Firestore failed", e)
            false
        }
    }

    private fun parseIsoTimestamp(timestamp: String): Date {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(timestamp) ?: Date(0)
        } catch (e: Exception) {
            try {
                val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf2.timeZone = TimeZone.getTimeZone("UTC")
                sdf2.parse(timestamp) ?: Date(0)
            } catch (e2: Exception) {
                try {
                    val sdf3 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    sdf3.parse(timestamp) ?: Date(0)
                } catch (e3: Exception) {
                    Date(0)
                }
            }
        }
    }

    private fun formatIsoTimestamp(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
