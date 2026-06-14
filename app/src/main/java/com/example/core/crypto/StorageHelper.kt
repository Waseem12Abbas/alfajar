package com.example.core.crypto

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object StorageHelper {

    /**
     * Saves a compiled pass badge bitmap as a PNG inside the device's public Gallery.
     * Compliant with MediaStore APIs on Android 10+ (Q) as well as older APIs.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        val resolver = context.contentResolver
        val relativeFileName = "${fileName.replace(" ", "_")}.png"
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, relativeFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlFajarPasses")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri).use { outStream ->
                        if (outStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                        }
                    }
                    
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                    true
                } else {
                    false
                }
            } else {
                // Legacy system directories with runtime check
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "AlFajarPasses"
                )
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val targetFile = File(targetDir, relativeFileName)
                FileOutputStream(targetFile).use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }

                // Invalidate MediaScanner to sync immediately
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Generates a secure share URI for the card PNG and triggers Android's standard share sheet.
     */
    fun sharePassCard(context: Context, bitmap: Bitmap, labelText: String) {
        try {
            val cachePath = File(context.cacheDir, "shared_passes")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            
            // Overwrite existing share temp file
            val file = File(cachePath, "AL-FAJAR_VEHICLE_PASS.png")
            FileOutputStream(file).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            
            val shareUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_TEXT, "Scan or use this official security pass for authorization: $labelText")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "Share Vehicle Pass via")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share pass image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
