package com.audiora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "SYSTEM",
    colorSchemeName: String = "AUDIORA_PURPLE",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val colorScheme = when (colorSchemeName) {
        "DYNAMIC" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                baseScheme
            }
        }
        "CRIMSON_RED" -> {
            baseScheme.copy(
                primary = if (darkTheme) Color(0xFFFF8A80) else Color(0xFFFF5252),
                primaryContainer = if (darkTheme) Color(0xFFC62828) else Color(0xFFFFCDD2),
                onPrimaryContainer = if (darkTheme) Color(0xFFFFFFFF) else Color(0xFF5D0008)
            )
        }
        "OCEAN_BLUE" -> {
            baseScheme.copy(
                primary = if (darkTheme) Color(0xFF90CAF9) else Color(0xFF2196F3),
                primaryContainer = if (darkTheme) Color(0xFF1565C0) else Color(0xFFBBDEFB),
                onPrimaryContainer = if (darkTheme) Color(0xFFFFFFFF) else Color(0xFF002538)
            )
        }
        "EMERALD_GREEN" -> {
            baseScheme.copy(
                primary = if (darkTheme) Color(0xFF81C784) else Color(0xFF4CAF50),
                primaryContainer = if (darkTheme) Color(0xFF2E7D32) else Color(0xFFC8E6C9),
                onPrimaryContainer = if (darkTheme) Color(0xFFFFFFFF) else Color(0xFF002200)
            )
        }
        "SUNSET_ORANGE" -> {
            baseScheme.copy(
                primary = if (darkTheme) Color(0xFFFFCC80) else Color(0xFFF57C00),
                primaryContainer = if (darkTheme) Color(0xFFE65100) else Color(0xFFFFF3E0),
                onPrimaryContainer = if (darkTheme) Color(0xFFFFFFFF) else Color(0xFF4E2500)
            )
        }
        else -> baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val windowInsetsController = WindowCompat.getInsetsController(window, view)
                windowInsetsController.isAppearanceLightStatusBars = !darkTheme
                windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
