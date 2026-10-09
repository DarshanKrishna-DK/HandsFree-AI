package com.example.handsfree.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CreamPink = lightColorScheme(
    primary = Color(0xFF94465F), onPrimary = Color.White,
    primaryContainer = Color(0xFFF7DCE3), onPrimaryContainer = Color(0xFF482631),
    secondary = Color(0xFF765B64), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E5E8), onSecondaryContainer = Color(0xFF482631),
    tertiary = Color(0xFF626C50),
    background = Color(0xFFFFF8F3), onBackground = Color(0xFF38262C),
    surface = Color(0xFFFFF8F3), onSurface = Color(0xFF38262C),
    surfaceVariant = Color(0xFFF8E9E9), onSurfaceVariant = Color(0xFF725C65),
    surfaceContainer = Color(0xFFFFEFED), surfaceContainerHigh = Color(0xFFF7E4E7),
    surfaceContainerLow = Color(0xFFFFF3EF), surfaceContainerHighest = Color(0xFFF2DDE2),
    outline = Color(0xFFAD8893), outlineVariant = Color(0xFFE6CDD2),
    error = Color(0xFFAD354E), onError = Color.White
)

@Composable
fun HandsFreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CreamPink, typography = Typography, content = content)
}
