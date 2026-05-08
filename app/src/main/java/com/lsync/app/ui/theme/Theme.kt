package com.lsync.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary          = TextPrimary,
    onPrimary        = BgPrimary,
    primaryContainer = AccentBlue,
    onPrimaryContainer = TextPrimary,
    secondary        = TextSecondary,
    background       = BgPrimary,
    onBackground     = TextPrimary,
    surface          = BgSecondary,
    onSurface        = TextPrimary,
    surfaceVariant   = BgCard,
    onSurfaceVariant = TextSecondary,
    outline          = Divider,
    error            = AccentRed,
)

@Composable
fun LSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
