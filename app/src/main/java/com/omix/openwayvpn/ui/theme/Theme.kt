package com.omix.openwayvpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = Teal80,
    tertiary = Slate80,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceAlt,
    onBackground = androidx.compose.ui.graphics.Color(0xFFE7EDF3),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE7EDF3),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFBCC8D4)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = Teal40,
    tertiary = Slate40,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceAlt,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A2530),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A2530),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF526273)
)

@Composable
fun OpenwayvpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
