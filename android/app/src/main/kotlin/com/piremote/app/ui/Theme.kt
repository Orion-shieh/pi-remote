package com.piremote.app.ui

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.piremote.app.data.SettingsStore

/**
 * Palette contract for Geek Horizon Design System.
 */
data class GeekPalette(
    val DeepCanvas: Color,
    val CardSurface: Color,
    val CardElevated: Color,
    val CardHighlight: Color,

    val BorderSubtle: Color,
    val BorderHighlight: Color,
    val BorderActive: Color,

    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,

    val BrandAccent: Color,
    val BrandAccentHover: Color,
    val BrandAccentGlow: Color,
    val BrandPurple: Color,
    val BrandPurpleGlow: Color,

    val NeonGreen: Color,
    val NeonGreenGlow: Color,
    val TerminalCyan: Color,
    val TerminalCyanGlow: Color,
    val AmberWarn: Color,
    val AmberWarnGlow: Color,
    val RoseError: Color,
    val RoseErrorGlow: Color,
    val isDark: Boolean,

    val BrandGradient: Brush,
    val AiGlowGradient: Brush,
    val TerminalGradient: Brush,
)

val DarkGeekPalette = GeekPalette(
    DeepCanvas = Color(0xFF0A0D14),      // iOS Deep Space Obsidian Canvas
    CardSurface = Color(0xFF141923),     // Crisp, luminous secondary surface (not dusty gray)
    CardElevated = Color(0xFF62749E),    // Clear, bright elevated button slate gray (~48% luminance)
    CardHighlight = Color(0xFF7E92BF),   // Clear tactile highlight state

    BorderSubtle = Color.Transparent,    // 去掉卡片边框
    BorderHighlight = Color.Transparent, // 去掉卡片边框
    BorderActive = Color(0xFF0A84FF),    // Apple System Blue

    TextPrimary = Color(0xFFFFFFFF),     // Apple 100% Primary White
    TextSecondary = Color(0xFFAAB9CF),   // Bright silver slate (high legibility, not dull gray)
    TextMuted = Color(0xFF7A8B9E),       // Clean readable muted label (55% luminance)

    BrandAccent = Color(0xFF0A84FF),     // Apple Vibrant Blue
    BrandAccentHover = Color(0xFF0071E3),
    BrandAccentGlow = Color(0x330A84FF),
    BrandPurple = Color(0xFFBF5AF2),     // Apple System Purple
    BrandPurpleGlow = Color(0x33BF5AF2),

    NeonGreen = Color(0xFF2EB84D),       // Balanced vibrant green
    NeonGreenGlow = Color(0x332EB84D),
    TerminalCyan = Color(0xFF64D2FF),    // Apple System Cyan / Teal
    TerminalCyanGlow = Color(0x3364D2FF),
    AmberWarn = Color(0xFFFF9F0A),       // Apple System Orange
    AmberWarnGlow = Color(0x33FF9F0A),
    RoseError = Color(0xFFFF453A),       // Apple System Red
    RoseErrorGlow = Color(0x33FF453A),
    isDark = true,

    BrandGradient = Brush.horizontalGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6))),
    AiGlowGradient = Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFF64D2FF))),
    TerminalGradient = Brush.horizontalGradient(listOf(Color(0xFF64D2FF), Color(0xFF0A84FF))),
)

val LightGeekPalette = GeekPalette(
    DeepCanvas = Color(0xFFFFFFFF),      // 纯白软件底部/底层背景
    CardSurface = Color(0xFFF7F8FA),     // 极浅纯净灰卡片背景 (统一与 Pi Agent 背景一致: 0xFFF7F8FA)
    CardElevated = Color(0xFFFFFFFF),    // 纯白微凸层/卡片内选项与按键背景
    CardHighlight = Color(0xFFE8EBF0),   // 按压反馈

    BorderSubtle = Color.Transparent,    // 去掉所有卡片边框
    BorderHighlight = Color.Transparent, // 去掉所有卡片边框
    BorderActive = Color(0xFF007AFF),    // Apple System Blue

    TextPrimary = Color(0xFF111827),     // 纯黑高对比清晰文字
    TextSecondary = Color(0xFF5A6577),   // 优雅次级灰文字
    TextMuted = Color(0xFF8E95A3),       // 舒适辅助说明文字

    BrandAccent = Color(0xFF007AFF),     // Apple System Blue
    BrandAccentHover = Color(0xFF0056B3),
    BrandAccentGlow = Color(0x22007AFF),
    BrandPurple = Color(0xFFAF52DE),     // Apple System Purple
    BrandPurpleGlow = Color(0x22AF52DE),

    NeonGreen = Color(0xFF2DA44E),       // Balanced vibrant green (comfortable contrast on light backgrounds)
    NeonGreenGlow = Color(0x222DA44E),
    TerminalCyan = Color(0xFF00A3D7),    // Apple System Teal
    TerminalCyanGlow = Color(0x2200A3D7),
    AmberWarn = Color(0xFFFF9500),       // Apple System Orange
    AmberWarnGlow = Color(0x22FF9500),
    RoseError = Color(0xFFFF3B30),       // Apple System Red
    RoseErrorGlow = Color(0x22FF3B30),
    isDark = false,

    BrandGradient = Brush.horizontalGradient(listOf(Color(0xFF007AFF), Color(0xFF5856D6))),
    AiGlowGradient = Brush.linearGradient(listOf(Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFF00A3D7))),
    TerminalGradient = Brush.horizontalGradient(listOf(Color(0xFF00A3D7), Color(0xFF007AFF))),
)

val LocalGeekColors = staticCompositionLocalOf { LightGeekPalette }
val LocalGuiFontScale = compositionLocalOf { 1.0f }

object GeekColors {
    val DeepCanvas: Color @Composable get() = LocalGeekColors.current.DeepCanvas
    val CardSurface: Color @Composable get() = LocalGeekColors.current.CardSurface
    val CardElevated: Color @Composable get() = LocalGeekColors.current.CardElevated
    val CardHighlight: Color @Composable get() = LocalGeekColors.current.CardHighlight

    val BorderSubtle: Color @Composable get() = LocalGeekColors.current.BorderSubtle
    val BorderHighlight: Color @Composable get() = LocalGeekColors.current.BorderHighlight
    val BorderActive: Color @Composable get() = LocalGeekColors.current.BorderActive

    val TextPrimary: Color @Composable get() = LocalGeekColors.current.TextPrimary
    val TextSecondary: Color @Composable get() = LocalGeekColors.current.TextSecondary
    val TextMuted: Color @Composable get() = LocalGeekColors.current.TextMuted

    val BrandAccent: Color @Composable get() = LocalGeekColors.current.BrandAccent
    val BrandAccentHover: Color @Composable get() = LocalGeekColors.current.BrandAccentHover
    val BrandAccentGlow: Color @Composable get() = LocalGeekColors.current.BrandAccentGlow
    val BrandPurple: Color @Composable get() = LocalGeekColors.current.BrandPurple
    val BrandPurpleGlow: Color @Composable get() = LocalGeekColors.current.BrandPurpleGlow

    val NeonGreen: Color @Composable get() = LocalGeekColors.current.NeonGreen
    val NeonGreenGlow: Color @Composable get() = LocalGeekColors.current.NeonGreenGlow
    val TerminalCyan: Color @Composable get() = LocalGeekColors.current.TerminalCyan
    val TerminalCyanGlow: Color @Composable get() = LocalGeekColors.current.TerminalCyanGlow
    val AmberWarn: Color @Composable get() = LocalGeekColors.current.AmberWarn
    val AmberWarnGlow: Color @Composable get() = LocalGeekColors.current.AmberWarnGlow
    val RoseError: Color @Composable get() = LocalGeekColors.current.RoseError
    val RoseErrorGlow: Color @Composable get() = LocalGeekColors.current.RoseErrorGlow

    val BrandGradient: Brush @Composable get() = LocalGeekColors.current.BrandGradient
    val AiGlowGradient: Brush @Composable get() = LocalGeekColors.current.AiGlowGradient
    val TerminalGradient: Brush @Composable get() = LocalGeekColors.current.TerminalGradient

    val isDark: Boolean @Composable get() = LocalGeekColors.current.isDark
}

/** Subtle tactile press effect - safe pass-through to prevent stuck gestures or sunken scaling. */
fun Modifier.pressClickEffect(): Modifier = this

@Composable
fun PiRemoteTheme(
    themeMode: String = SettingsStore.THEME_LIGHT,
    guiFontScale: Float = SettingsStore.DEFAULT_GUI_FONT_SCALE,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        SettingsStore.THEME_DARK -> true
        SettingsStore.THEME_SYSTEM -> systemDark
        else -> false
    }

    val palette = if (isDark) DarkGeekPalette else LightGeekPalette
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.BrandAccent,
            onPrimary = Color.White,
            secondary = palette.TerminalCyan,
            onSecondary = Color.Black,
            background = palette.DeepCanvas,
            onBackground = palette.TextPrimary,
            surface = palette.CardSurface,
            onSurface = palette.TextPrimary,
            surfaceVariant = palette.CardElevated,
            onSurfaceVariant = palette.TextSecondary,
            outline = palette.BorderSubtle,
            outlineVariant = palette.BorderHighlight,
            error = palette.RoseError,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = palette.BrandAccent,
            onPrimary = Color.White,
            secondary = palette.TerminalCyan,
            onSecondary = Color.White,
            background = palette.DeepCanvas,
            onBackground = palette.TextPrimary,
            surface = palette.CardSurface,
            onSurface = palette.TextPrimary,
            surfaceVariant = palette.CardElevated,
            onSurfaceVariant = palette.TextSecondary,
            outline = palette.BorderSubtle,
            outlineVariant = palette.BorderHighlight,
            error = palette.RoseError,
            onError = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val effectiveGuiFontScale = guiFontScale * 1.05f

    CompositionLocalProvider(
        LocalGeekColors provides palette,
        LocalGuiFontScale provides effectiveGuiFontScale,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
