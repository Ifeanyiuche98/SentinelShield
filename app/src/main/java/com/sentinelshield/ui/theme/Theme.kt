package com.sentinelshield.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Black and Gold color scheme
val Gold = Color(0xFFFFD700)
val DarkGold = Color(0xFFB8860B)
val LightGold = Color(0xFFFFE44D)
val Black = Color(0xFF000000)
val DarkSurface = Color(0xFF1A1A1A)
val DarkBackground = Color(0xFF121212)
val LightSurface = Color(0xFFFAFAFA)
val LightBackground = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    primaryContainer = DarkGold,
    onPrimaryContainer = LightGold,
    secondary = Gold,
    onSecondary = Black,
    secondaryContainer = DarkGold,
    onSecondaryContainer = LightGold,
    tertiary = LightGold,
    onTertiary = Black,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFCF6679),
    onError = Black
)

private val LightColorScheme = lightColorScheme(
    primary = DarkGold,
    onPrimary = Color.White,
    primaryContainer = LightGold,
    onPrimaryContainer = Black,
    secondary = DarkGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3C4),
    onSecondaryContainer = Black,
    tertiary = Gold,
    onTertiary = Black,
    background = LightBackground,
    onBackground = Black,
    surface = LightSurface,
    onSurface = Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242),
    error = Color(0xFFB00020),
    onError = Color.White
)

@Composable
fun SentinelShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
