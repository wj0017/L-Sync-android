package com.lsync.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary            = FgPrimary,
    onPrimary          = BgPrimary,
    primaryContainer   = AccentBlue,
    onPrimaryContainer = FgPrimary,
    secondary          = FgSecondary,
    background         = BgPrimary,
    onBackground       = FgPrimary,
    surface            = BgSecondary,
    onSurface          = FgPrimary,
    surfaceVariant     = BgCard,
    onSurfaceVariant   = FgSecondary,
    outline            = Divider,
    error              = AccentRed,
)

@Composable
fun LSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = Typography,
        content     = content,
    )
}
