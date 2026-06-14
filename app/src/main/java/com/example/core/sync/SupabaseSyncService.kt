package com.example.core.sync

import android.content.Context

/**
 * Backward compatibility adapter that redirects the legacy PostgreSQL sync layer calls
 * to the modern Firebase Cloud Firestore Sync engine seamlessly.
 */
object SupabaseSyncService {
    fun isOnline(context: Context): Boolean {
        return FirebaseSyncService.isOnline(context)
    }

    fun getDeviceId(context: Context): String {
        return FirebaseSyncService.getDeviceId(context)
    }

    fun parsePostgresUri(uri: String): String {
        return ""
    }

    suspend fun sync(context: Context, onStatusUpdate: ((String) -> Unit)? = null): Boolean {
        return FirebaseSyncService.sync(context, onStatusUpdate)
    }
}
