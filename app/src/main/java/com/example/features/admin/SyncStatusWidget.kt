package com.example.features.admin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SyncStatusWidget(
    isOnline: Boolean,
    isSyncing: Boolean,
    lastSyncLabel: String,
    modifier: Modifier = Modifier
) {
    val color = when {
        isSyncing -> Color(0xFFF97316) // Orange (Syncing)
        isOnline -> Color(0xFF15803D) // Green (Synced)
        else -> Color(0xFFDC2626) // Red (Offline)
    }

    val icon = when {
        isSyncing -> Icons.Default.Sync
        isOnline -> Icons.Default.CloudDone
        else -> Icons.Default.CloudOff
    }

    val label = when {
        isSyncing -> "Syncing..."
        isOnline -> "Synced"
        else -> "Offline"
    }

    SuggestionChip(
        onClick = { /* Static display chip */ },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isSyncing && isOnline && lastSyncLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lastSyncLabel,
                        color = color.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.08f),
            labelColor = color
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = true,
            borderColor = color.copy(alpha = 0.2f),
            borderWidth = 1.dp
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}
