package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ProfessionalBlue,
    onPrimary = Color.White,
    primaryContainer = Slate900,
    onPrimaryContainer = Color.White,
    secondary = Slate300,
    onSecondary = Slate900,
    background = Slate900,
    onBackground = Color.White,
    surface = Color(0xFF1E293B),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Slate200,
    outlineVariant = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = ProfessionalBlue,
    onPrimary = Color.White,
    primaryContainer = ProfessionalBlueLight,
    onPrimaryContainer = ProfessionalBlueDark,
    secondary = Slate600,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outlineVariant = Slate200
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
