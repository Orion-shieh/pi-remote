package com.piremote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalBackground = Color(0xFF101014)
private val TerminalSurface = Color(0xFF1A1A20)
private val Accent = Color(0xFF8ABEB7)

private val PiRemoteColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201C),
    secondary = Color(0xFF66A6A0),
    background = TerminalBackground,
    onBackground = Color(0xFFE6E6E6),
    surface = TerminalSurface,
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF26262E),
    onSurfaceVariant = Color(0xFFB8B8BE),
    error = Color(0xFFFF6B68),
)

/**
 * Always dark: the terminal palette is designed against a dark grid, and a
 * light scheme would wash out the ANSI colours programs pick for it.
 */
@Composable
fun PiRemoteTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = PiRemoteColors, content = content)
}
