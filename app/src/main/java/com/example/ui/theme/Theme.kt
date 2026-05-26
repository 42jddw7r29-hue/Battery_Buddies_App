package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberCyan,
    secondary = CyberPurple,
    tertiary = CyberBlue,
    background = CyberDarkBg,
    surface = CyberSurface,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF04060C),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color(0xFFE0E6ED),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E6ED),
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF90A4AE)
  )

private val LightColorScheme = DarkColorScheme // Keep consistent cyber theme representation

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force sci-fi dark theme by default
  dynamicColor: Boolean = false, // Use our custom themed colors instead of system colors
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      else -> DarkColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
