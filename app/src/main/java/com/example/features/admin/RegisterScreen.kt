package com.example.features.admin

import android.app.DatePickerDialog
import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.bluetooth.BluetoothPrinterService
import com.example.core.crypto.EncryptionService
import com.example.core.crypto.QrCodeHelper
import com.example.core.crypto.StorageHelper
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.models.VehiclePass
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(adminUserId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var ownerName by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var vehicleNo by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    
    // Default Expiry is +1 year
    val calendar = Calendar.getInstance()
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    
    calendar.add(Calendar.YEAR, 1)
    val defaultExpiryStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    var expiryDate by remember { mutableStateOf(defaultExpiryStr) }

    // Error feedback
    var nameError by remember { mutableStateOf(false) }
    var cnicError by remember { mutableStateOf(false) }
    var vehError by remember { mutableStateOf(false) }

    // Generated Pass state
    var generatedPass by remember { mutableStateOf<VehiclePass?>(null) }
    var qrBase64Text by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val secureStorageService = remember { SecureStorageService(context) }
    val db = remember { AppDatabase.getDatabase(context) }

    // Format CNIC physically as the user types: XXXXX-XXXXXXX-X
    fun formatCnic(input: String): String {
        val digits = input.filter { it.isDigit() }
        val sb = StringBuilder()
        for (i in digits.indices) {
            if (i == 5 || i == 12) {
                sb.append("-")
            }
            sb.append(digits[i])
        }
        return sb.toString().take(15) // Maximum 15 characters
    }

    if (showQrDialog && qrBitmap != null && generatedPass != null) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AL-FAJAR VEHICLE PASS",
                        fontSize = 18.sp,
                        color = Color(0xFF005129),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "سیکیورٹی ڈیجیٹل پاس جاری ہو گیا",
                        fontSize = 14.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code Preview Display Frame
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White)
                            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Pass QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Info parameters list
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "نام (Owner): ${generatedPass!!.owner_name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "گاڑی نمبر (Vehicle No): ${generatedPass!!.vehicle_no}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "شناختی کارڈ (CNIC): ${generatedPass!!.cnic}",
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "میعاد تاریخ (Expiry): ${generatedPass!!.expiry_date}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBA1A1A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Modern Action Stack for saving/sharing
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Action 1: Download to Gallery
                        Button(
                            onClick = {
                                val fullBadge = QrCodeHelper.generatePassBadge(
                                    ownerName = generatedPass!!.owner_name,
                                    vehicleNo = generatedPass!!.vehicle_no,
                                    cnic = generatedPass!!.cnic,
                                    expiryDate = generatedPass!!.expiry_date,
                                    issuedDate = todayStr,
                                    qrBitmap = qrBitmap!!
                                )
                                val success = StorageHelper.saveBitmapToGallery(
                                    context = context,
                                    bitmap = fullBadge,
                                    fileName = "AL_FAJAR_PASS_${generatedPass!!.vehicle_no}"
                                )
                                if (success) {
                                    Toast.makeText(context, "پاس گیلری میں کامیابی سے محفوظ ہو گیا! Saved to Gallery.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "گیلری میں محفوظ نہ ہو سکا۔ Error saving to Gallery.", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ڈاؤن لوڈ کریں | Save to Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Action 2: Share Pass Image
                        Button(
                            onClick = {
                                val fullBadge = QrCodeHelper.generatePassBadge(
                                    ownerName = generatedPass!!.owner_name,
                                    vehicleNo = generatedPass!!.vehicle_no,
                                    cnic = generatedPass!!.cnic,
                                    expiryDate = generatedPass!!.expiry_date,
                                    issuedDate = todayStr,
                                    qrBitmap = qrBitmap!!
                                )
                                StorageHelper.sharePassCard(
                                    context = context,
                                    bitmap = fullBadge,
                                    labelText = "AL-FAJAR VEHICLE PASS: Name: ${generatedPass!!.owner_name}, Veh: ${generatedPass!!.vehicle_no}, Exp: ${generatedPass!!.expiry_date}"
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("شیئر کریں | Share Pass Image", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Action 3: Close
                        TextButton(
                            onClick = { showQrDialog = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("بند کریں | Cancel", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FB)) // bg-surface
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Section: Dashboard Greeting
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "گاڑی کی رجسٹریشن",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A), // text-primary
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "نیا سکیورٹی پاس جاری کرنے کے لیے تفصیلات درج کریں۔",
                fontSize = 14.sp,
                color = Color(0xFF475569), // text-secondary
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Main Registration Card Form Container
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)), // border-idle
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Owner Name
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "مالک کا نام",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = {
                            ownerName = it
                            nameError = false
                        },
                        placeholder = { Text("نام درج کریں", color = Color(0xFF94A3B8)) },
                        isError = nameError,
                        supportingText = { if (nameError) Text("Name is required! | نام درج کرنا ضروری ہے") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFCBD5E1)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF7F9FB),
                            unfocusedContainerColor = Color(0xFFF7F9FB),
                            focusedBorderColor = Color(0xFF005129),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = Color(0xFF005129)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // CNIC Field
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "شناختی کارڈ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cnic,
                        onValueChange = {
                            cnic = formatCnic(it)
                            cnicError = false
                        },
                        placeholder = { Text("00000-0000000-0", color = Color(0xFF94A3B8)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = cnicError,
                        supportingText = { if (cnicError) Text("CNIC must be formatted XXXXX-XXXXXXX-X") },
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFFCBD5E1)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF7F9FB),
                            unfocusedContainerColor = Color(0xFFF7F9FB),
                            focusedBorderColor = Color(0xFF005129),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Side-by-Side: Vehicle Number & Phone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Vehicle Number (Left/First col)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "گاڑی کا نمبر",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vehicleNo,
                            onValueChange = {
                                vehicleNo = it.trim().uppercase()
                                vehError = false
                            },
                            placeholder = { Text("ABC-1234", color = Color(0xFF94A3B8)) },
                            isError = vehError,
                            supportingText = { if (vehError) Text("Required | لازمی") },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFFCBD5E1)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF7F9FB),
                                unfocusedContainerColor = Color(0xFFF7F9FB),
                                focusedBorderColor = Color(0xFF005129),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Phone Number (Right/Second col)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "فون نمبر",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            placeholder = { Text("03XX-XXXXXXX", color = Color(0xFF94A3B8)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFFCBD5E1)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF7F9FB),
                                unfocusedContainerColor = Color(0xFFF7F9FB),
                                focusedBorderColor = Color(0xFF005129),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Expiry Date Selector
                val datePickerDialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val monthStr = String.format("%02d", month + 1)
                        val dayStr = String.format("%02d", dayOfMonth)
                        expiryDate = "$year-$monthStr-$dayStr"
                    },
                    Calendar.getInstance().get(Calendar.YEAR) + 1,
                    Calendar.getInstance().get(Calendar.MONTH),
                    Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "میعاد کی تاریخ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Date", tint = Color(0xFF005129))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF7F9FB),
                            unfocusedContainerColor = Color(0xFFF7F9FB),
                            focusedBorderColor = Color(0xFF005129),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Generate Pass Submit Button
                Button(
                    onClick = {
                        // Form validation
                        var isValid = true
                        if (ownerName.trim().isEmpty()) {
                            nameError = true
                            isValid = false
                        }
                        if (cnic.length != 15) {
                            cnicError = true
                            isValid = false
                        }
                        if (vehicleNo.trim().isEmpty()) {
                            vehError = true
                            isValid = false
                        }

                        if (!isValid) return@Button

                        scope.launch {
                            val activeKey = secureStorageService.getStoredKey()
                            if (activeKey == null) {
                                Toast.makeText(context, "مذکورہ چابی غائب ہے! AES key not decrypted.", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val passUuid = UUID.randomUUID().toString()

                            val json = JSONObject().apply {
                                put("uid", passUuid)
                                put("name", ownerName.trim())
                                put("cnic", cnic.trim())
                                put("veh", vehicleNo.trim())
                                put("exp", expiryDate)
                                put("issued", todayStr)
                            }

                            val jsonStr = json.toString()
                            val encryptedB64 = EncryptionService.encrypt(jsonStr, activeKey)

                            val generatedQr = QrCodeHelper.generateQrCode(encryptedB64, 300)
                            if (generatedQr == null) {
                                Toast.makeText(context, "QR code generation failed!", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val nowTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            val pass = VehiclePass(
                                unique_id = passUuid,
                                owner_name = ownerName.trim(),
                                cnic = cnic.trim(),
                                vehicle_no = vehicleNo.trim(),
                                phone = phone.trim().ifEmpty { null },
                                expiry_date = expiryDate,
                                created_by = adminUserId,
                                created_at = nowTimestamp,
                                is_revoked = 0
                            )

                            val insertId = db.vehiclePassDao().insertPass(pass)
                            if (insertId > 0) {
                                generatedPass = pass.copy(id = insertId.toInt())
                                qrBase64Text = encryptedB64
                                qrBitmap = generatedQr
                                showQrDialog = true

                                // Reset form fields
                                ownerName = ""
                                cnic = ""
                                vehicleNo = ""
                                phone = ""
                                expiryDate = defaultExpiryStr
                                Toast.makeText(context, "پاس کامیابی سے رجسٹر ہوا! Pass generated.", Toast.LENGTH_SHORT).show()

                                // Auto-trigger instant sync
                                scope.launch {
                                    com.example.core.sync.SupabaseSyncService.sync(context)
                                }
                            } else {
                                Toast.makeText(context, "کامیابی سے ثبت نہیں سکا۔ Duplicate unique ID error.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129)), // primary/primary-container base
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "پاس تیار کریں",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        // Bottom Asymmetric Security/Info Row blocks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Box 1 (Weight 3f): Secured System Info with Rounded outline
            Card(
                modifier = Modifier.weight(3f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFCFE5D0)) // secondary-container (#cfe5d0)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Secured",
                            tint = Color(0xFF005129), // Deep green primary
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "محفوظ سسٹم",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF005129),
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = "آپ کا ڈیٹا مکمل طور پر انکرپٹڈ ہے۔",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF546756), // on-secondary-container
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }

            // Box 2 (Weight 2f): AL-FAJAR Digital Auth info layout
            Card(
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEF0)) // surface-container (#eceef0)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AL-FAJAR",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005129)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "DIGITAL AUTH",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF475569) // text-secondary
                    )
                }
            }
        }
    }
}
