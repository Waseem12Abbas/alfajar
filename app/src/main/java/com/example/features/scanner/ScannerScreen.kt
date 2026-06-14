package com.example.features.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.core.crypto.EncryptionService
import com.example.core.crypto.QrCodeAnalyzer
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.models.User
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface ValidationResult {
    data object Scanning : ValidationResult
    data class Success(
        val uid: String,
        val ownerName: String,
        val vehicleNo: String,
        val cnic: String,
        val expiryDate: String,
        val issuedDate: String
    ) : ValidationResult
    data class Failure(val reasonUrdu: String, val reasonEnglish: String) : ValidationResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    scannerUser: User,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getDatabase(context) }
    val secureStorageService = remember { SecureStorageService(context) }

    // State
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isScanningActive by remember { mutableStateOf(true) }
    var validationResult by remember { mutableStateOf<ValidationResult>(ValidationResult.Scanning) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            cameraPermissionGranted = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    Scaffold(
        containerColor = Color(0xFFF7F9FB), // bg-surface
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign out",
                                tint = Color(0xFFBA1A1A) // M3 error red
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "تلاشی اسکینر ٹرمینل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005129) // primary
                            )
                            Text(
                                "SCANNER | USER: ${scannerUser.username.uppercase()}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFF475569) // text-secondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F9FB),
                    titleContentColor = Color(0xFF005129)
                ),
                modifier = Modifier.border(0.dp, Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!cameraPermissionGranted) {
                // Denied view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Permission Off",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "کیمرہ پرمیشن غائب ہے",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Camera Permission Required",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("اجازت دیں | Enable Camera")
                    }
                }
            } else {
                // Camera Scanner and validation view
                when (val result = validationResult) {
                    is ValidationResult.Scanning -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Camera preview view
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().apply {
                                            surfaceProvider = previewView.surfaceProvider
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        imageAnalysis.setAnalyzer(
                                            cameraExecutor,
                                            QrCodeAnalyzer { rawQrText ->
                                                if (isScanningActive) {
                                                    isScanningActive = false
                                                    scope.launch {
                                                        validateQrPayload(
                                                            rawQrText = rawQrText,
                                                            secureStorageService = secureStorageService,
                                                            db = db,
                                                            onResult = { validationResult = it }
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageAnalysis
                                            )
                                        } catch (e: Exception) {
                                            Log.e("ScannerScreen", "Use case binding failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Semi-transparent Scanning overlay frames
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(260.dp)
                                            .border(2.dp, Color.Green, RoundedCornerShape(8.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "اسکین فریم کے اندر کیو آر کوڈ سیدھا رکھیں\nHold QR inside the green square",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    is ValidationResult.Success -> {
                        // Gather dynamic scan timestamp
                        val scanTimestamp = remember {
                            val sdf = java.text.SimpleDateFormat("hh:mm:ss a | dd-MMM-yyyy", java.util.Locale.US)
                            sdf.format(java.util.Date())
                        }

                        // Gradient background: Lush Green (#22C55E to #15803D)
                        val successBgGradient = Brush.verticalGradient(
                            colors = listOf(Color(0xFF22C55E), Color(0xFF15803D))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(successBgGradient)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Top Success Check Status
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success Status Check",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(76.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "VALID - تصدیق شدہ",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ENTRY AUTHORIZATION GRANTED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            // Detail Card Overlay
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Row 1: Security Status & Avatar Mockup
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "SECURITY PASS STATUS",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Pulsing live indicator dot
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFF22C55E), CircleShape)
                                                )
                                                Text(
                                                    text = "Active & Verified",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }

                                        // Profile picture layout container
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color(0xFFCFE5D0), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Owner avatar",
                                                tint = Color(0xFF005129),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFEDF2F7))

                                    // Row 2: Bento Detail blocks
                                    // Block 1 (Owner Name)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF7F9FB), RoundedCornerShape(10.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Owner Name / مالک کا نام",
                                                fontSize = 11.sp,
                                                color = Color(0xFF475569)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = result.ownerName,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = result.ownerName.uppercase(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }

                                    // Block 2 & 3: Vehicle and CNIC row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFFF7F9FB), RoundedCornerShape(10.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Vehicle / گاڑی",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF475569)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = result.vehicleNo,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .background(Color(0xFFF7F9FB), RoundedCornerShape(10.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "CNIC Number",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF475569)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = result.cnic,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                            }
                                        }
                                    }

                                    // Block 4: Expiry Date Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFCFE5D0).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFCFE5D0).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Expiry Date / تاریخ تنسیخ",
                                                fontSize = 11.sp,
                                                color = Color(0xFF475569)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = result.expiryDate,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF005129)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = Color(0xFF005129),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    // Scan Timestamp info
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Verified at: $scanTimestamp",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }

                            // Bottom dynamic scan again button (Capsule mockup design)
                            Button(
                                onClick = {
                                    validationResult = ValidationResult.Scanning
                                    isScanningActive = true
                                },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF15803D)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 12.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Scan Again / دوبارہ اسکین کریں",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }

                    is ValidationResult.Failure -> {
                        // Failure Gradient background: Crimson Red (#EF4444 to #BA1A1A)
                        val failureBgGradient = Brush.verticalGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFBA1A1A))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(failureBgGradient)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Top Invalid Check Status
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Failure Status Cancel",
                                        tint = Color(0xFFBA1A1A),
                                        modifier = Modifier.size(76.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "INVALID - غیر معتبر",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ENTRY AUTHORIZATION DENIED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            // Error Card details
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Security alert",
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Text(
                                        text = result.reasonUrdu,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = result.reasonEnglish,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.9f),
                                        textAlign = TextAlign.Center
                                    )
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                                    Text(
                                        text = "آف لائن سیکیورٹی پاس کی تصدیق ناکام ہو گئی ہے۔ یہ کوڈ خراب، ایکسپائرڈ، منسوخ شدہ یا جعلی ہو سکتا ہے۔",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            // Bottom Capsule action button
                            Button(
                                onClick = {
                                    validationResult = ValidationResult.Scanning
                                    isScanningActive = true
                                },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFBA1A1A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 12.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = Color(0xFFBA1A1A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Scan Again / دوبارہ اسکین کریں",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFFBA1A1A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 100% offline AES Decryption and validation flow!
// 100% offline AES Decryption and validation flow!
private suspend fun validateQrPayload(
    rawQrText: String,
    secureStorageService: SecureStorageService,
    db: AppDatabase,
    onResult: (ValidationResult) -> Unit
) {
    try {
        // Defensive String Sanitization: Trim input raw payload to eliminate hidden formatting or whitespaces
        val cleanScannedId = rawQrText.trim()

        val key = secureStorageService.getStoredKey()
        if (key == null) {
            onResult(ValidationResult.Failure("مذکورہ چابی غائب ہے", "AES Key generation / retrieval failed"))
            return
        }

        // Decrypt the payload with support for both primary and static fallback keys
        val decryptedJsonStr = try {
            EncryptionService.decrypt(cleanScannedId, key)
        } catch (e: Exception) {
            Log.w("ScannerScreen", "Primary decryption failed. Attempting fallback master key...", e)
            try {
                val fallbackKey = secureStorageService.getFallbackKey()
                EncryptionService.decrypt(cleanScannedId, fallbackKey)
            } catch (ex: Exception) {
                ex.printStackTrace()
                // If both key attempts fail, mark the QR payload as invalid/forged
                onResult(ValidationResult.Failure("غیر معتبر پاس - جعلی پاس", "INVALID - Decryption Failed / Forged Pass"))
                return
            }
        }

        val json = JSONObject(decryptedJsonStr)
        val uid = json.getString("uid").trim() // Trim UID parsed from payload as well
        val name = json.getString("name").trim()
        val cnic = json.getString("cnic").trim()
        val veh = json.getString("veh").trim()
        val exp = json.getString("exp").trim()
        val issued = json.getString("issued").trim()

        // 1. Verify Expiry date with robust 5-minute clock drift margin check
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expiryDateObj = try {
            sdf.parse(exp)
        } catch (e: Exception) {
            null
        }

        if (expiryDateObj != null) {
            // Setup calendar to represent the absolute end of the expiry day (23:59:59.999)
            val cal = Calendar.getInstance().apply {
                time = expiryDateObj
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val expiryTimeMs = cal.timeInMillis
            // Deduct a 5-minute drift buffer (300,000 ms) from current system time
            // to prevent false-negatives due to minor time variations between device clocks
            val currentTimeWithDriftMs = System.currentTimeMillis() - (5 * 60 * 1000)
            if (currentTimeWithDriftMs > expiryTimeMs) {
                onResult(ValidationResult.Failure("میعاد ختم - غیر فعال", "EXPIRED - Pass has expired"))
                return
            }
        } else {
            // Strict lexicographical comparison fallback if date parsing fails
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (exp < todayStr) {
                onResult(ValidationResult.Failure("میعاد ختم - غیر فعال", "EXPIRED - Pass has expired"))
                return
            }
        }

        // 2. Query Local SQLite Table to check if marked as revoked
        // Sanitize the UID string prior to making the database query
        val passInDb = db.vehiclePassDao().getPassByUniqueId(uid)
        
        // Defensive revocation check: explicitly check for the value 1 (revoked).
        // A value of 0, null, or any other integer represents a valid active pass.
        if (passInDb != null && passInDb.is_revoked == 1) {
            onResult(ValidationResult.Failure("منسوخ شدہ پاس - غیر فعال", "REVOKED - Pass manually revoked by Admin"))
            return
        }

        // 3. Success! Valid and active
        onResult(
            ValidationResult.Success(
                uid = uid,
                ownerName = name,
                vehicleNo = veh,
                cnic = cnic,
                expiryDate = exp,
                issuedDate = issued
            )
        )

    } catch (e: Exception) {
        e.printStackTrace()
        onResult(ValidationResult.Failure("تصدیق ناکام", "Unexpected validation error: ${e.message}"))
    }
}
