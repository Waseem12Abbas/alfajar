package com.example.features.admin

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.crypto.EncryptionService
import com.example.core.crypto.QrCodeHelper
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.models.VehiclePass
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassesListScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val secureStorageService = remember { SecureStorageService(context) }

    // State
    val passesList by db.vehiclePassDao().getAllPassesFlow().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var currentFilter by remember { mutableStateOf("All") } // "All", "Active", "Revoked"

    // Dialog state for viewing QR
    var selectedPass by remember { mutableStateOf<VehiclePass?>(null) }
    var detailQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // Unified filtering logic
    val filteredPasses = remember(passesList, searchQuery, currentFilter) {
        val searchLower = searchQuery.lowercase().trim()
        val queryResult = if (searchLower.isEmpty()) {
            passesList
        } else {
            passesList.filter {
                it.owner_name.lowercase().contains(searchLower) ||
                it.cnic.contains(searchLower) ||
                it.vehicle_no.lowercase().contains(searchLower)
            }
        }
        
        when (currentFilter) {
            "Active" -> queryResult.filter { it.is_revoked == 0 }
            "Revoked" -> queryResult.filter { it.is_revoked == 1 }
            else -> queryResult
        }
    }

    // Detail dialog
    if (showDetailDialog && selectedPass != null && detailQrBitmap != null) {
        Dialog(onDismissRequest = { showDetailDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REGISTERED PASS DETAIL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005129)
                    )
                    Text(
                        text = "رجسٹرڈ پاس کی تفصیل",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = detailQrBitmap!!.asImageBitmap(),
                            contentDescription = "Pass QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("نام (Owner): ${selectedPass!!.owner_name}", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("گاڑی نمبر (Vehicle): ${selectedPass!!.vehicle_no}", fontWeight = FontWeight.Bold, color = Color(0xFF005129))
                        Text("شناختی کارڈ (CNIC): ${selectedPass!!.cnic}", fontSize = 14.sp, color = Color(0xFF475569))
                        if (!selectedPass!!.phone.isNullOrBlank()) {
                            Text("فون (Phone): ${selectedPass!!.phone}", fontSize = 14.sp, color = Color(0xFF475569))
                        }
                        Text("میعاد (Expiry): ${selectedPass!!.expiry_date}", fontWeight = FontWeight.Bold, color = if (selectedPass!!.is_revoked == 1) Color(0xFFBA1A1A) else Color(0xFF005129))
                        Text(
                            "حیثیت (Status): " + if (selectedPass!!.is_revoked == 1) "منسوخ شدہ | REVOKED" else "فعال | ACTIVE",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedPass!!.is_revoked == 1) Color(0xFFBA1A1A) else Color(0xFF005129)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showDetailDialog = false },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("ٹھیک ہے | Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FB)) // bg-surface
            .padding(16.dp)
    ) {
        // Search Bar (Urdu/English unified search)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("تلاش کریں / Search Passes", color = Color(0xFF94A3B8)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF475569)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFF475569))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF005129),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedLabelColor = Color(0xFF005129),
                unfocusedLabelColor = Color(0xFF475569)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Geometric balance design filter chips row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            // "All" chip
            Button(
                onClick = { currentFilter = "All" },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentFilter == "All") Color(0xFF005129) else Color.White,
                    contentColor = if (currentFilter == "All") Color.White else Color(0xFF475569)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .border(
                        width = if (currentFilter == "All") 0.dp else 1.dp,
                        color = Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .height(38.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text("سب (All)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // "Active" chip
            Button(
                onClick = { currentFilter = "Active" },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentFilter == "Active") Color(0xFF005129) else Color.White,
                    contentColor = if (currentFilter == "Active") Color.White else Color(0xFF475569)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .border(
                        width = if (currentFilter == "Active") 0.dp else 1.dp,
                        color = Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .height(38.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text("فعال (Active)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // "Revoked" chip
            Button(
                onClick = { currentFilter = "Revoked" },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentFilter == "Revoked") Color(0xFF005129) else Color.White,
                    contentColor = if (currentFilter == "Revoked") Color.White else Color(0xFF475569)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .border(
                        width = if (currentFilter == "Revoked") 0.dp else 1.dp,
                        color = Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .height(38.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text("منسوخ (Revoked)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "رجسٹرڈ پاسز | PASSES COUNT (${filteredPasses.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF475569), // text-secondary
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        if (filteredPasses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Empty list",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "کوئی پاس نہیں ملا | No passes registered",
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredPasses) { pass ->
                    val isRevoked = pass.is_revoked == 1
                    val accentColor = if (isRevoked) Color(0xFFBA1A1A) else Color(0xFF005129)
                    
                    // Asymmetric Bento Card with vertical accent bar
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEDF2F7), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // Left accent status bar
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .fillMaxHeight()
                                    .background(accentColor)
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(16.dp)
                            ) {
                                // Card Top: Owner Name & Status Pill
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Owner Name",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.5.sp,
                                            color = Color(0xFF475569)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = pass.owner_name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }

                                    // Active/Revoked Pill indicator
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isRevoked) Color(0xFFFFF1F2) else Color(0xFFCFE5D0),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isRevoked) "Revoked" else "Active",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRevoked) Color(0xFFBA1A1A) else Color(0xFF005129)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Middle Row: Vehicle & Expiry with separators
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = pass.vehicle_no,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF475569)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))
                                    // Vertical separator line
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(14.dp)
                                            .background(Color(0xFFCBD5E1))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isRevoked) "منسوخ" else "Expires: ${pass.expiry_date.take(12)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFFEDF2F7))
                                Spacer(modifier = Modifier.height(12.dp))

                                // Bottom Row: View QR & Revoke / Restore buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val activeKey = secureStorageService.getStoredKey()
                                                if (activeKey == null) {
                                                    Toast.makeText(context, "AES key decryption error!", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }

                                                val json = JSONObject().apply {
                                                    put("uid", pass.unique_id)
                                                    put("name", pass.owner_name)
                                                    put("cnic", pass.cnic)
                                                    put("veh", pass.vehicle_no)
                                                    put("exp", pass.expiry_date)
                                                    put("issued", pass.created_at.take(10))
                                                }

                                                val encryptedB64 = EncryptionService.encrypt(json.toString(), activeKey)
                                                val bitmap = QrCodeHelper.generateQrCode(encryptedB64, 300)
                                                if (bitmap != null) {
                                                    selectedPass = pass
                                                    detailQrBitmap = bitmap
                                                    showDetailDialog = true
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF2F4F6),
                                            contentColor = Color(0xFF005129)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCode,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View QR", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val nextRevokeState = if (isRevoked) 0 else 1
                                                val existing = db.vehiclePassDao().getPassByUniqueId(pass.unique_id)
                                                if (existing != null) {
                                                    val updated = existing.copy(
                                                        is_revoked = nextRevokeState,
                                                        last_modified = com.example.models.VehiclePass.getCurrentIsoTimestamp()
                                                    )
                                                    db.vehiclePassDao().replacePass(updated)
                                                } else {
                                                    db.vehiclePassDao().setRevoked(pass.id, nextRevokeState)
                                                }
                                                val msg = if (nextRevokeState == 1) "پاس منسوخ کر دیا گیا ہے! Pass Revoked." else "پاس بحال کر دیا گیا ہے! Pass Restored."
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                                                // Trigger auto-sync in background so revocation spreads to cloud immediately
                                                scope.launch {
                                                    com.example.core.sync.SupabaseSyncService.sync(context)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isRevoked) Color(0xFFCFE5D0) else Color(0xFFFFF1F2),
                                            contentColor = if (isRevoked) Color(0xFF005129) else Color(0xFFBA1A1A)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isRevoked) Icons.Default.Refresh else Icons.Default.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isRevoked) "Restore" else "Revoke",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
