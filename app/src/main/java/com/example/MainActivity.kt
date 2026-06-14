package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.AppDatabase
import com.example.core.storage.SecureStorageService
import com.example.core.sync.SyncWorker
import com.example.features.admin.AdminDashboard
import com.example.features.auth.FirstRunScreen
import com.example.features.auth.LoginScreen
import com.example.features.scanner.ScannerScreen
import com.example.models.User
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

sealed interface AppState {
    data object Loading : AppState
    data object FirstRun : AppState
    data object Login : AppState
    data class Error(val message: String) : AppState
    data class AdminDashboard(val user: User) : AppState
    data class ScannerDashboard(val user: User) : AppState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register an UncaughtExceptionHandler to capture and print any fatal runtime exceptions clearly in logcat
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("FATAL_APP_CRASH", "!!! UNCAUGHT EXCEPTION IN THREAD: ${thread.name} !!!", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Schedule background periodic sync task on startup
        try {
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "pakpass_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            android.util.Log.d("MainActivity", "Successfully scheduled 15-min periodic background Sync task.")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed scheduling WorkManager sync", e)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var appState by remember { mutableStateOf<AppState>(AppState.Loading) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val secureStorage = remember { SecureStorageService(applicationContext) }
                val db = remember { AppDatabase.getDatabase(applicationContext) }

                // Check first-run on start
                LaunchedEffect(Unit) {
                    try {
                        val userCount = db.userDao().getUsersCount()
                        val hasKeyStored = secureStorage.hasKeyStored()

                        if (userCount == 0 || !hasKeyStored) {
                            // If no users exist or secret key has not been derived yet, go to First-Run Setup
                            appState = AppState.FirstRun
                        } else {
                            // Go straight to Offline Login Terminal
                            appState = AppState.Login
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "Database/Security initiation failed", t)
                        appState = AppState.Error(
                            "Initialization failed:\n" +
                            "${t.localizedMessage ?: t.toString()}\n\n" +
                            android.util.Log.getStackTraceString(t)
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (val state = appState) {
                            is AppState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("آف لائن لوڈ ہو رہا ہے | Loading...", color = Color.Gray)
                                    }
                                }
                            }

                            is AppState.FirstRun -> {
                                FirstRunScreen(
                                    onSetupComplete = {
                                        appState = AppState.Login
                                    }
                                )
                            }

                            is AppState.Login -> {
                                LoginScreen(
                                    onLoginSuccess = { loggedUser ->
                                        if (loggedUser.role == "admin") {
                                            appState = AppState.AdminDashboard(loggedUser)
                                        } else {
                                            appState = AppState.ScannerDashboard(loggedUser)
                                        }
                                    }
                                )
                            }

                            is AppState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFFFF1F2))
                                        .padding(24.dp)
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Initialization Error",
                                        tint = Color(0xFFBA1A1A),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "تکنیکی خرابی | Initialization Failed",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBA1A1A)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = state.message,
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    secureStorage.clearKey()
                                                    context.deleteDatabase("pakpass_database")
                                                    appState = AppState.FirstRun
                                                } catch (e: Exception) {
                                                    // ignore
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Reset & Clear Application", color = Color.White)
                                    }
                                }
                            }

                            is AppState.AdminDashboard -> {
                                AdminDashboard(
                                    adminUser = state.user,
                                    onLogout = {
                                        appState = AppState.Login
                                    }
                                )
                            }

                            is AppState.ScannerDashboard -> {
                                ScannerScreen(
                                    scannerUser = state.user,
                                    onLogout = {
                                        appState = AppState.Login
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
