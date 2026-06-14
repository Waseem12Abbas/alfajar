package com.example.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val success = SupabaseSyncService.sync(applicationContext)
        return if (success) {
            Result.success()
        } else {
            // Support retry if network fails or temporary backend issue
            Result.retry()
        }
    }
}
