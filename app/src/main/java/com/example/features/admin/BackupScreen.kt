package com.example.features.admin

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.backup.BackupService
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.core.sync.SupabaseSyncService
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val db = remember { AppDatabase.getDatabase(context) }

    val secureStorage = remember { SecureStorageService(context) }
    var supabaseUrl by remember { mutableStateOf(secureStorage.getSupabaseUrl() ?: "") }
    var supabaseKey by remember { mutableStateOf(secureStorage.getSupabaseAnonKey() ?: "") }
    var isSyncingNow by remember { mutableStateOf(false) }
    var lastSyncStamp by remember { mutableStateOf(secureStorage.getLastSyncTime()) }

    var pastedBackupText by remember { mutableStateOf("") }
    var exportStatusText by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FB)) // bg-surface
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Block
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "ڈیٹا بیک اپ اور امپورٹ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF005129), // primary
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "BACKUP & RESTORE DATA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF475569), // text-secondary
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }
        }

        // Supabase Sync Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سپابیس کلاؤڈ سنکرونائزیشن | Cloud Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    
                    val parsedTimeLabel = if (lastSyncStamp != "2020-01-01T00:00:00Z" && lastSyncStamp.contains("T")) {
                        try {
                            lastSyncStamp.substringAfter("T").substring(0, 5)
                        } catch (e: Exception) {
                            ""
                        }
                    } else ""

                    SyncStatusWidget(
                        isOnline = SupabaseSyncService.isOnline(context),
                        isSyncing = isSyncingNow,
                        lastSyncLabel = parsedTimeLabel
                    )
                }

                Text(
                    text = "ڈیٹا کو آن لائن سنکرونائز کرنے کے لیے اپنے سپابیس کلاؤڈ کی تفصیلات درج کریں۔ اور پش / پل ایکشن کے ذریعے تمام ڈیوائسز برابر رکھیں۔",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )

                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = { supabaseUrl = it },
                    label = { Text("Supabase URL (https://...)") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF005129),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFF005129),
                        unfocusedLabelColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = supabaseKey,
                    onValueChange = { supabaseKey = it },
                    label = { Text("Supabase Anon Key") },
                    placeholder = { Text("your-anon-or-project-api-key") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF005129),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFF005129),
                        unfocusedLabelColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (supabaseUrl.trim().isEmpty() || supabaseKey.trim().isEmpty()) {
                                Toast.makeText(context, "براہ کرم سپابیس کی معلومات مکمل درج کریں | Enter URL & Key", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            secureStorage.setSupabaseUrl(supabaseUrl.trim())
                            secureStorage.setSupabaseAnonKey(supabaseKey.trim())
                            Toast.makeText(context, "سیٹنگز محفوظ ہو گئیں! Setup saved.", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                        modifier = Modifier
                            .weight(1.0f)
                            .height(48.dp)
                    ) {
                        Text("محفوظ کریں | Save Setup", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (supabaseUrl.trim().isEmpty() || supabaseKey.trim().isEmpty()) {
                                Toast.makeText(context, "پہلے سسٹم سیٹنگز محفوظ کریں! Save settings first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            // Autocommit settings
                            secureStorage.setSupabaseUrl(supabaseUrl.trim())
                            secureStorage.setSupabaseAnonKey(supabaseKey.trim())

                            scope.launch {
                                isSyncingNow = true
                                val success = SupabaseSyncService.sync(context)
                                isSyncingNow = false
                                if (success) {
                                    lastSyncStamp = secureStorage.getLastSyncTime()
                                    Toast.makeText(context, "سنک مکمل ہو گیا! Sync completed successfully.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "مطابقت ناکام رہی۔ چیک کریں کہ انٹرنیٹ آن ہے یا نہیں | Sync failed.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129)),
                        enabled = !isSyncingNow,
                        modifier = Modifier
                            .weight(1.0f)
                            .height(48.dp)
                    ) {
                        Text("ابھی سنک کریں | Sync Now", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Export Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1. ڈیٹا ایکسپورٹ کریں | Data Export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "تمام گاڑیوں کے رجسٹرڈ پاسز کو آف لائن بیک اپ فائل کے طور پر ڈرائیو پر محفوظ کریں۔",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )

                Button(
                    onClick = {
                        scope.launch {
                            isOperating = true
                            val result = BackupService.exportBackup(context)
                            isOperating = false
                            if (result.isSuccess) {
                                val path = result.getOrThrow()
                                exportStatusText = "بیک اپ فائل کامیابی کے ساتھ محفوظ ہو گئی!\nPath: $path"
                                Toast.makeText(context, "بیک اپ مکمل! Backup exported.", Toast.LENGTH_LONG).show()
                            } else {
                                exportStatusText = "ایکسپورٹ ناکام رہا: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129)),
                    enabled = !isOperating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بیک اپ فائل بنائیں | Export JSON File", fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (exportStatusText.isNotEmpty()) {
                    Text(
                        text = exportStatusText,
                        fontSize = 12.sp,
                        color = Color(0xFF005129),
                        modifier = Modifier.padding(top = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Import Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "2. ڈیٹا امپورٹ کریں | Data Import",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "بیک اپ ورڈ فائل یا پیش کردہ JSON سٹرنگ کو نیچے پیسٹ کریں اور امپورٹ کریں۔",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )

                OutlinedTextField(
                    value = pastedBackupText,
                    onValueChange = { pastedBackupText = it },
                    label = { Text("Paste JSON Backup here | بیک اپ پیسٹ کریں") },
                    placeholder = { Text("[{\"unique_id\": \"...\", ...}]", color = Color(0xFF94A3B8)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF005129),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFF005129),
                        unfocusedLabelColor = Color(0xFF475569)
                    ),
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (pastedBackupText.trim().isEmpty()) {
                                Toast.makeText(context, "برائے مہربانی پہلے بیک اپ پیسٹ کریں! Paste text first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                isOperating = true
                                val result = BackupService.importBackup(context, pastedBackupText)
                                isOperating = false
                                if (result.isSuccess) {
                                    val count = result.getOrThrow()
                                    Toast.makeText(context, "کامیابی سے امپورٹ ہوا! $count pass(es) integrated.", Toast.LENGTH_LONG).show()
                                    pastedBackupText = ""
                                } else {
                                    Toast.makeText(context, "امپورٹ ناکام رہا: فارمیٹ غلط پایا گیا۔ Invalid layout/format.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129)),
                        enabled = !isOperating,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("امپورٹ | Import", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isOperating = true
                                try {
                                    val passes = db.vehiclePassDao().getAllPassesStatic()
                                    val jsonArray = JSONArray()
                                    for (pass in passes) {
                                        val obj = org.json.JSONObject().apply {
                                            put("unique_id", pass.unique_id)
                                            put("owner_name", pass.owner_name)
                                            put("cnic", pass.cnic)
                                            put("vehicle_no", pass.vehicle_no)
                                            put("phone", pass.phone ?: org.json.JSONObject.NULL)
                                            put("expiry_date", pass.expiry_date)
                                            put("created_at", pass.created_at)
                                            put("is_revoked", pass.is_revoked)
                                        }
                                        jsonArray.put(obj)
                                    }
                                    val rawString = jsonArray.toString(4)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("AL-FAJAR VEHICLE PASS Data Backup", rawString)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "کاپی مکمل ہو گئی! Text copied to clipboard.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "کاپی ناکام رہی: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isOperating = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isOperating,
                        border = BorderStroke(1.dp, Color(0xFF005129)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF005129)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF005129))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("کاپی | Copy DB", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
