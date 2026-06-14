package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PakPassPrimary,
    primaryContainer = PakPassPrimaryContainer,
    secondaryContainer = PakPassSecondaryContainer,
    background = Color(0xFF0F172A), // Slate-900 for modern elegant dark mode
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    error = PakPassDanger
)

private val LightColorScheme = lightColorScheme(
    primary = PakPassPrimary,
    primaryContainer = PakPassPrimaryContainer,
    secondary = PakPassSecondary,
    secondaryContainer = PakPassSecondaryContainer,
    background = PakPassBackground,
    surface = PakPassSurface,
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = PakPassDanger
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Default to clean light mode for security terminals
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
