package com.piremote.terminal.view

import android.graphics.Typeface
import com.piremote.terminal.TerminalColors
import com.piremote.terminal.TextStyle

/**
 * Colours and metrics used to paint the terminal grid.
 *
 * The palette is 259 entries: 0..255 are the ANSI colours and 256/257/258 are
 * the special foreground, background and cursor slots, matching
 * [TextStyle]'s indices. It starts from the ported Termux colour scheme and only
 * the first 16 entries are overridden, since those are the ones programs treat
 * as "the terminal's own colours".
 */
class TerminalTheme(
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val palette: IntArray,
    val typeface: Typeface,
    val textSizePx: Float,
) {
    companion object {

        private val DARK_BASE = intArrayOf(
            0xFF1C1C1E.toInt(), 0xFFCC3E3E.toInt(), 0xFF4E9A4E.toInt(), 0xFFC7A252.toInt(),
            0xFF4A7FB5.toInt(), 0xFF9A6BB5.toInt(), 0xFF3F9A9A.toInt(), 0xFFD0D0D0.toInt(),
            0xFF7F7F7F.toInt(), 0xFFFF6B6B.toInt(), 0xFF7FD67F.toInt(), 0xFFE8D07A.toInt(),
            0xFF6FA8DC.toInt(), 0xFFC79BE8.toInt(), 0xFF6FD3D3.toInt(), 0xFFFFFFFF.toInt(),
        )

        private val LIGHT_BASE = intArrayOf(
            0xFF24292E.toInt(), // 0: Black
            0xFFD73A49.toInt(), // 1: Red
            0xFF22863A.toInt(), // 2: Green
            0xFFB08800.toInt(), // 3: Yellow
            0xFF005CC5.toInt(), // 4: Blue
            0xFF6F42C1.toInt(), // 5: Magenta
            0xFF0086B3.toInt(), // 6: Cyan
            0xFFE1E4E8.toInt(), // 7: Light Gray / White
            0xFF959DA5.toInt(), // 8: Bright Black / Gray
            0xFFCB2431.toInt(), // 9: Bright Red
            0xFF28A745.toInt(), // 10: Bright Green
            0xFFB08800.toInt(), // 11: Bright Yellow
            0xFF2188FF.toInt(), // 12: Bright Blue
            0xFF8A63D2.toInt(), // 13: Bright Magenta
            0xFF3192AA.toInt(), // 14: Bright Cyan
            0xFFFFFFFF.toInt(), // 15: Bright White
        )

        fun dark(textSizePx: Float, typeface: Typeface): TerminalTheme =
            TerminalTheme(
                foreground = 0xFFE6E6E6.toInt(),
                background = 0xFF101014.toInt(),
                cursor = 0xFFE6E6E6.toInt(),
                palette = build(DARK_BASE, 0xFFE6E6E6.toInt(), 0xFF101014.toInt()),
                typeface = typeface,
                textSizePx = textSizePx,
            )

        fun light(textSizePx: Float, typeface: Typeface): TerminalTheme =
            TerminalTheme(
                foreground = 0xFF24292E.toInt(),
                background = 0xFFFAFAFA.toInt(),
                cursor = 0xFF24292E.toInt(),
                palette = build(LIGHT_BASE, 0xFF24292E.toInt(), 0xFFFAFAFA.toInt()),
                typeface = typeface,
                textSizePx = textSizePx,
            )

        private fun build(base16: IntArray, foreground: Int, background: Int): IntArray {
            val colors = TerminalColors.COLOR_SCHEME.mDefaultColors.copyOf()
            System.arraycopy(base16, 0, colors, 0, 16)
            colors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
            colors[TextStyle.COLOR_INDEX_BACKGROUND] = background
            colors[TextStyle.COLOR_INDEX_CURSOR] = foreground
            return colors
        }
    }
}
