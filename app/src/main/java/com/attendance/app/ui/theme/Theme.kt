package com.attendance.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = ForestGreenLight,
    onPrimaryContainer = ForestGreenDark,
    secondary = AccentMint,
    onSecondary = Color.White,
    secondaryContainer = ForestSlate100,
    onSecondaryContainer = ForestSlate900,
    background = SoftOffWhite,
    onBackground = ForestSlate900,
    surface = CardWhite,
    onSurface = ForestSlate900,
    surfaceVariant = ForestSlate100,
    onSurfaceVariant = ForestSlate700,
    outline = ForestSlate200,
    error = RoseRed,
    onError = Color.White,
    errorContainer = RoseLight,
    onErrorContainer = RoseRed
)

@Composable
fun AttendanceAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SoftOffWhite.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
