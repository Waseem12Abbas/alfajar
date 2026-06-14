package com.example.features.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.crypto.EncryptionService
import com.example.core.database.AppDatabase
import com.example.models.User
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (User) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val db = remember { AppDatabase.getDatabase(context) }
    val scrollState = rememberScrollState()

    // Pulse animation for the Secure status badge at the bottom
    val infiniteTransition = rememberInfiniteTransition(label = "badgePulseLogin")
    val badgeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulseAlphaLogin"
    )

    Scaffold(
        containerColor = Color(0xFFF7F9FB), // bg-surface
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF005129), // primary
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AL-FAJAR VEHICLE PASS",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF005129)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF7F9FB)
                ),
                modifier = Modifier.height(64.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Header Description
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AL-FAJAR VEHICLE PASS Terminal Login",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A), // text-primary
                textAlign = TextAlign.Center
            )
            Text(
                text = "آف لائن سیکیورٹی لاگ ان",
                fontSize = 16.sp,
                color = Color(0xFF475569), // text-secondary
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Setup Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEDF2F7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Lock Reset Icon Container
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFCFE5D0), CircleShape), // secondary-container
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Lock reset feedback",
                            tint = Color(0xFF005129), // primary
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Field 1: Username
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Username | صارف نام",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { input ->
                                username = input.trim().lowercase()
                                errorMsg = ""
                            },
                            placeholder = {
                                Text(
                                    "e.g. admin or scanner1",
                                    color = Color(0xFFCBD5E1)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFCBD5E1)
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF7F9FB),
                                unfocusedContainerColor = Color(0xFFF7F9FB),
                                focusedBorderColor = Color(0xFF005129),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                disabledBorderColor = Color(0xFFCBD5E1)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Field 2: PIN
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "6-Digit PIN | پن کوڈ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    pin = input
                                    errorMsg = ""
                                }
                            },
                            placeholder = {
                                Text(
                                    "••••••",
                                    color = Color(0xFFCBD5E1),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFCBD5E1)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { pinVisible = !pinVisible }) {
                                    Icon(
                                        imageVector = if (pinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (pinVisible) "Hide PIN" else "Show PIN",
                                        tint = Color(0xFF475569)
                                    )
                                }
                            },
                            textStyle = TextStyle(
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF7F9FB),
                                unfocusedContainerColor = Color(0xFFF7F9FB),
                                focusedBorderColor = Color(0xFF005129),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                disabledBorderColor = Color(0xFFCBD5E1)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorMsg.isNotEmpty()) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Primary Login Submit button
                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            if (username.isEmpty()) {
                                errorMsg = "Please enter username! | صارف کا نام درج کریں"
                                return@Button
                            }
                            if (pin.length != 6) {
                                errorMsg = "PIN must be exactly 6 digits! | پن 6 ہندسوں کا ہونا ضروری ہے"
                                return@Button
                            }

                            isSubmitting = true
                            scope.launch {
                                try {
                                    val user = db.userDao().getUserByUsername(username)
                                    if (user == null) {
                                        errorMsg = "User not found! | صارف موجود نہیں ہے"
                                        isSubmitting = false
                                        return@launch
                                    }

                                    val calculatedHash = EncryptionService.hashSha256(pin)
                                    if (user.pin_hash == calculatedHash) {
                                        errorMsg = ""
                                        onLoginSuccess(user)
                                    } else {
                                        errorMsg = "Incorrect PIN! | پن کوڈ غلط ہے"
                                    }
                                } catch (e: Exception) {
                                    errorMsg = "Login failed: ${e.message}"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF005129), // primary
                            disabledContainerColor = Color(0xFF005129).copy(alpha = 0.5f)
                        ),
                        enabled = !isSubmitting && username.isNotEmpty() && pin.length == 6
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Logging in... | لاگ ان ہو رہا ہے",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "لاگ ان کریں | Log In",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Footer branding security badge
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .background(Color(0xFFECEEF0), RoundedCornerShape(20.dp)) // bg-surface-container
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF005129).copy(alpha = badgeAlpha)) // Pulsing green point
                )
                Text(
                    text = "End-to-End Encrypted",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF475569) // text-secondary
                )
            }
        }
    }
}
