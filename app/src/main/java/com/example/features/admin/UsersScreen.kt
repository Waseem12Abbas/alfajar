package com.example.features.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.crypto.EncryptionService
import com.example.core.database.AppDatabase
import com.example.models.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // State
    val scannersList by db.userDao().getAllScannersFlow().collectAsState(initial = emptyList())
    var newUsername by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    var usernameError by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FB)) // bg-surface
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature Screen title block
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
                    text = "کوڈ اسکینر اکاؤنٹ بنائیں",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF005129), // primary
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ADD NEW SCANNER ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF475569), // text-secondary
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Username Field
        OutlinedTextField(
            value = newUsername,
            onValueChange = {
                newUsername = it.trim().lowercase()
                usernameError = ""
            },
            label = { Text("Scanner Username | صارف نام") },
            placeholder = { Text("e.g. scanner1", color = Color(0xFF94A3B8)) },
            isError = usernameError.isNotEmpty(),
            supportingText = { if (usernameError.isNotEmpty()) Text(usernameError) },
            leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFFCBD5E1)) },
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

        // PIN Field
        OutlinedTextField(
            value = newPin,
            onValueChange = {
                if (it.length <= 6) {
                    newPin = it
                    pinError = ""
                }
            },
            label = { Text("Scanner 6-digit PIN | پن کوڈ") },
            placeholder = { Text("6 Digits", color = Color(0xFF94A3B8)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = pinError.isNotEmpty(),
            supportingText = { if (pinError.isNotEmpty()) Text(pinError) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFCBD5E1)) },
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

        // Add Account Button
        Button(
            onClick = {
                // Validation
                if (newUsername.isEmpty()) {
                    usernameError = "Username is required! | نام لازمی ہے"
                    return@Button
                }
                if (newPin.length != 6) {
                    pinError = "PIN must be exactly 6 digits! | پن 6 ہندسوں کا ہونا ضروری ہے"
                    return@Button
                }

                scope.launch {
                    val existing = db.userDao().getUserByUsername(newUsername)
                    if (existing != null) {
                        usernameError = "Username already exists! | نام پہلے سے موجود ہے"
                        return@launch
                    }

                    val pinHash = EncryptionService.hashSha256(newPin)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val newUser = User(
                        username = newUsername,
                        pin_hash = pinHash,
                        role = "scanner",
                        created_at = timestamp
                    )

                    try {
                        db.userDao().insertUser(newUser)
                        Toast.makeText(context, "کامیابی سے اسکینر اکاؤنٹ شامل کر دیا گیا! Account added.", Toast.LENGTH_SHORT).show()
                        newUsername = ""
                        newPin = ""
                        usernameError = ""
                        pinError = ""
                    } catch (e: Exception) {
                        Toast.makeText(context, "ثبت کرنے میں خرابی: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005129))
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("اکاؤنٹ شامل کریں | Add Profile", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFFCBD5E1))

        Text(
            text = "موجودہ اسکینرز کی معلومات | SCANNERS DEVICES (${scannersList.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp)
        )

        if (scannersList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "کوئی اضافی اسکینر اکاؤنٹ نہیں ملا | No scanner profiles created",
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(scannersList) { scanner ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFCFE5D0), RoundedCornerShape(20.dp)), // secondary-container (#cfe5d0)
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF005129), // primary
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = scanner.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "تلاشی کردار | Scanner Profile",
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        db.userDao().deleteUserById(scanner.id)
                                        Toast.makeText(context, "اسکینر اکاؤنٹ حذف ہو گیا! Profile deleted.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Account",
                                    tint = Color(0xFFBA1A1A) // M3 error red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
