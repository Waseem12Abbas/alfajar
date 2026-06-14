package com.example.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.core.storage.SecureStorageService
import com.example.core.sync.SupabaseSyncService
import com.example.models.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(
    adminUser: User,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val secureStorage = remember { SecureStorageService(context) }
    var isSyncingNow by remember { mutableStateOf(false) }
    var lastSyncStamp by remember { mutableStateOf(secureStorage.getLastSyncTime()) }

    // Run autokick sync on startup if configured
    LaunchedEffect(Unit) {
        if (!secureStorage.getFirebaseProjectId().isNullOrEmpty()) {
            isSyncingNow = true
            SupabaseSyncService.sync(context)
            isSyncingNow = false
            lastSyncStamp = secureStorage.getLastSyncTime()
        }
    }

    // Monitor online state dynamically
    val isOnline = SupabaseSyncService.isOnline(context)

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onLogout) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Log out",
                                    tint = Color(0xFFBA1A1A) // error color
                                )
                            }

                            val parsedTimeLabel = if (lastSyncStamp != "2020-01-01T00:00:00Z" && lastSyncStamp.contains("T")) {
                                try {
                                    lastSyncStamp.substringAfter("T").substring(0, 5)
                                } catch (e: Exception) {
                                    ""
                                }
                            } else ""

                            SyncStatusWidget(
                                isOnline = isOnline,
                                isSyncing = isSyncingNow,
                                lastSyncLabel = parsedTimeLabel
                            )
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                "ایڈمنسٹریٹر کنٹرول پینل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005129) // primary
                            )
                            Text(
                                "ADMIN | USER: ${adminUser.username.uppercase()}",
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
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxHeight(0.09f) // polished bar size
                    .border(width = 1.dp, color = Color(0xFFEDF2F7), shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AppRegistration, contentDescription = "Register") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF005129),
                        selectedTextColor = Color(0xFF005129),
                        indicatorColor = Color(0xFFCFE5D0), // secondary-container (#cfe5d0)
                        unselectedIconColor = Color(0xFF475569),
                        unselectedTextColor = Color(0xFF475569)
                    ),
                    label = { Text("رجسٹریشن", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = "Passes") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF005129),
                        selectedTextColor = Color(0xFF005129),
                        indicatorColor = Color(0xFFCFE5D0),
                        unselectedIconColor = Color(0xFF475569),
                        unselectedTextColor = Color(0xFF475569)
                    ),
                    label = { Text("پاسز", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Users") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF005129),
                        selectedTextColor = Color(0xFF005129),
                        indicatorColor = Color(0xFFCFE5D0),
                        unselectedIconColor = Color(0xFF475569),
                        unselectedTextColor = Color(0xFF475569)
                    ),
                    label = { Text("صارفین", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Backup, contentDescription = "Backup") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF005129),
                        selectedTextColor = Color(0xFF005129),
                        indicatorColor = Color(0xFFCFE5D0),
                        unselectedIconColor = Color(0xFF475569),
                        unselectedTextColor = Color(0xFF475569)
                    ),
                    label = { Text("بیک اپ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> RegisterScreen(adminUserId = adminUser.id)
                1 -> PassesListScreen()
                2 -> UsersScreen()
                3 -> BackupScreen()
            }
        }
    }
}
