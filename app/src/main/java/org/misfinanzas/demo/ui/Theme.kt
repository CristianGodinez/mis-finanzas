package org.misfinanzas.demo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepNavy = Color(0xFF07111F)
val NavySurface = Color(0xFF0E1B2A)
val NavySurfaceHigh = Color(0xFF162638)
val Mint = Color(0xFF58E0C2)
val SoftBlue = Color(0xFF8EAEFF)
val WarmAmber = Color(0xFFFFB86B)
val Danger = Color(0xFFFF7A8A)
val TextPrimary = Color(0xFFF4F7FB)
val TextMuted = Color(0xFF9EADBD)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00382F),
    secondary = SoftBlue,
    tertiary = WarmAmber,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceHigh,
    onSurfaceVariant = TextMuted,
    error = Danger
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5B),
    secondary = Color(0xFF315DA8),
    tertiary = Color(0xFF8C4D00),
    background = Color(0xFFF3F7F9),
    surface = Color.White
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else DarkColors,
        content = content
    )
}

