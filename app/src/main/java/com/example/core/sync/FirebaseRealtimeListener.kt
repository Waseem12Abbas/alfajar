package com.example.core.sync

import android.content.Context
import android.util.Log
import com.example.core.database.AppDatabase
import com.example.models.VehiclePass
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirebaseRealtimeListener {
    private const val TAG = "FirebaseListener"
    private var listenerRegistration: ListenerRegistration? = null

    fun startListening(context: Context) {
        if (listenerRegistration != null) {
            Log.d(TAG, "Listener already active.")
            return
        }

        val firestore = FirebaseInit.getFirestore(context)
        if (firestore == null) {
            Log.e(TAG, "Cannot start listener: Firestore not initialized.")
            return
        }

        val deviceId = FirebaseSyncService.getDeviceId(context)
        Log.d(TAG, "Starting active realtime sync listener.")

        try {
            listenerRegistration = firestore.collection("vehicle_passes")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Listen failed in FirebaseRealtimeListener", error)
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        val db = AppDatabase.getDatabase(context)
                        CoroutineScope(Dispatchers.IO).launch {
                            for (dc in snapshots.documentChanges) {
                                // We are only interested in new/modified documents from other devices
                                if (dc.type == DocumentChange.Type.ADDED || dc.type == DocumentChange.Type.MODIFIED) {
                                    val doc = dc.document
                                    val cloudDeviceId = doc.getString("device_id") ?: ""
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

                                    val createdAtStr = if (createdAtTimestamp != null) formatIsoTimestamp(createdAtTimestamp.toDate()) else VehiclePass.getCurrentIsoTimestamp()
                                    val lastModifiedStr = if (lastModifiedTimestamp != null) formatIsoTimestamp(lastModifiedTimestamp.toDate()) else VehiclePass.getCurrentIsoTimestamp()

                                    // Retrieve local record if exists
                                    val localRecord = db.vehiclePassDao().getPassByUniqueId(uid)

                                    val recordToSave = VehiclePass(
                                        id = localRecord?.id ?: 0,
                                        unique_id = uid,
                                        owner_name = ownerName,
                                        cnic = cnic,
                                        vehicle_no = vehicleNo,
                                        phone = if (phone.isNullOrEmpty()) null else phone,
                                        expiry_date = expiryDate,
                                        created_by = localRecord?.created_by,
                                        created_at = createdAtStr,
                                        is_revoked = isRevoked,
                                        last_modified = lastModifiedStr,
                                        device_id = cloudDeviceId
                                    )

                                    if (localRecord == null) {
                                        db.vehiclePassDao().replacePass(recordToSave)
                                        Log.d(TAG, "Realtime Pull: Inserted new record $uid")
                                    } else {
                                        // Compare timestamps
                                        val isCloudNewer = try {
                                            val localTime = parseIsoTimestamp(localRecord.last_modified)
                                            val cloudTime = parseIsoTimestamp(lastModifiedStr)
                                            cloudTime.after(localTime)
                                        } catch (e: Exception) {
                                            lastModifiedStr > localRecord.last_modified
                                        }

                                        if (isCloudNewer) {
                                            db.vehiclePassDao().replacePass(recordToSave)
                                            Log.d(TAG, "Realtime Pull: Conflict resolved by overriding local with cloud $uid")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting snapshots listener", e)
        }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.d(TAG, "Realtime listener stopped.")
    }

    private fun parseIsoTimestamp(timestamp: String): Date {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(timestamp) ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }

    private fun formatIsoTimestamp(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
