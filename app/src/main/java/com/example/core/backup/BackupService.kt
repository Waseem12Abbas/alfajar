package com.example.core.backup

import android.content.Context
import android.os.Environment
import com.example.core.database.AppDatabase
import com.example.models.VehiclePass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupService {

    // Export passes to standard JSON String and save to Downloads
    suspend fun exportBackup(context: Context): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val passes = db.vehiclePassDao().getAllPassesStatic()

                val jsonArray = JSONArray()
                for (pass in passes) {
                    val obj = JSONObject().apply {
                        put("unique_id", pass.unique_id)
                        put("owner_name", pass.owner_name)
                        put("cnic", pass.cnic)
                        put("vehicle_no", pass.vehicle_no)
                        put("phone", pass.phone ?: JSONObject.NULL)
                        put("expiry_date", pass.expiry_date)
                        put("created_at", pass.created_at)
                        put("is_revoked", pass.is_revoked)
                    }
                    jsonArray.put(obj)
                }

                val jsonStr = jsonArray.toString(4)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "vehicle_passes_backup_$timeStamp.json"

                // Save to downloads directory
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                var file = File(downloadsDir, fileName)

                try {
                    file.writeText(jsonStr)
                } catch (e: Exception) {
                    // Fallback to internal/external app files if public Downloads is write-restricted
                    val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                    file = File(fallbackDir, fileName)
                    file.writeText(jsonStr)
                }

                Result.success(file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // Import backup from JSON string (verifying structural validity)
    suspend fun importBackup(context: Context, jsonContent: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val passDao = db.vehiclePassDao()
                val jsonArray = JSONArray(jsonContent)

                var insertedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val uid = obj.getString("unique_id")
                    
                    // Verify if duplicate exists
                    val existing = passDao.getPassByUniqueId(uid)
                    if (existing == null) {
                        val pass = VehiclePass(
                            unique_id = uid,
                            owner_name = obj.getString("owner_name"),
                            cnic = obj.getString("cnic"),
                            vehicle_no = obj.getString("vehicle_no"),
                            phone = if (obj.isNull("phone")) null else obj.getString("phone"),
                            expiry_date = obj.getString("expiry_date"),
                            created_at = obj.getString("created_at"),
                            created_by = null, // Set to default offline admin
                            is_revoked = obj.optInt("is_revoked", 0)
                        )
                        passDao.insertPass(pass)
                        insertedCount++
                    }
                }
                Result.success(insertedCount)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}
