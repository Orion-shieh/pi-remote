package com.piremote.terminal.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import com.piremote.terminal.KeyEventCodes
import com.piremote.terminal.KeyHandler
import com.piremote.terminal.TerminalEmulator
import com.piremote.terminal.TerminalOutput
import com.piremote.terminal.TerminalRow
import com.piremote.terminal.TerminalSessionClient
import com.piremote.terminal.TextStyle
import com.piremote.terminal.WcWidth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

/**
 * Renders the ported Termux terminal emulator and forwards input to it.
 *
 * The view owns no terminal logic of its own: escape sequences, reflow, scroll
 * regions and key encoding all live in the emulator module. What is here is the
 * grid layout, painting, and the touch/IME plumbing that needs a `View`.
 *
 * Scrolling is tracked in pixels rather than rows so dragging tracks the finger,
 * with the sub-row remainder applied at draw time.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // ------------------------------------------------------------------ wiring

    /** Bytes the terminal must send back to the shell (device status reports etc.). */
    var onOutput: ((ByteArray) -> Unit)? = null

    var onTitleChanged: ((String) -> Unit)? = null

    /** Fired when the grid size changes, so the caller can tell the agent. */
    var onResize: ((cols: Int, rows: Int) -> Unit)? = null

    /** OSC 52 asked to put text on the clipboard. */
    var onClipboardCopy: ((String) -> Unit)? = null

    var onBell: (() -> Unit)? = null

    /** (scrolledBackPx, totalScrollablePx) */
    var onScrollChanged: ((Int, Int) -> Unit)? = null

    /** Fired whenever new bytes are fed or content changes, allowing GUI view to sync. */
    var onContentUpdated: (() -> Unit)? = null

    /** Extracts all text currently in the emulator screen and history buffer. */
    fun getTranscriptText(): String =
        emulator?.screen?.transcriptTextWithFullLinesJoined.orEmpty()

    var theme: TerminalTheme = TerminalTheme.dark(spToPx(13f), Typeface.MONOSPACE)
        set(value) {
            val oldW = cellWidth
            val oldH = cellHeight
            field = value
            recomputeMetrics()
            emulator?.let { applyTheme(it) }
            if (cellWidth != oldW || cellHeight != oldH) {
                applyViewSize()
            }
            invalidate()
        }

    /** Latched modifiers for the on-screen key bar; a soft keyboard cannot hold Ctrl. */
    var ctrlLatched: Boolean = false
    var altLatched: Boolean = false

    /** Invoked on tap, so the host can raise the keyboard. */
    var onTap: (() -> Unit)? = null

    var emulator: TerminalEmulator? = null
        private set

    // ----------------------------------------------------------- emulator glue

    private val terminalOutput = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            onOutput?.invoke(data.copyOfRange(offset, offset + count))
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) {
            onTitleChanged?.invoke(newTitle.orEmpty())
        }

        override fun onCopyTextToClipboard(text: String?) {
            if (!text.isNullOrEmpty()) onClipboardCopy?.invoke(text)
        }

        override fun onPasteTextFromClipboard() {
            // The host owns the clipboard; the key bar drives paste explicitly.
        }

        override fun onBell() {
            onBell?.invoke()
        }

        override fun onColorsChanged() {
            postInvalidate()
        }
    }

    private val sessionClient = object : TerminalSessionClient {
        override fun getTerminalCursorStyle(): Int? = null
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        // android.util.Log returns an Int, so these need block bodies to keep
        // the Unit return type the interface declares.
        override fun logError(tag: String, message: String) {
            Log.e(tag, message)
        }

        override fun logWarn(tag: String, message: String) {
            Log.w(tag, message)
        }

        override fun logInfo(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun logDebug(tag: String, message: String) {
            Log.d(tag, message)
        }

        override fun logVerbose(tag: String, message: String) {
            Log.v(tag, message)
        }
    }

    // ---------------------------------------------------------------- painting

    // One paint for all text. Bold uses setFakeBoldText rather than a separate
    // typeface because a real bold face has a different advance width, which
    // would make bold runs drift out of alignment with the grid.
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = theme.typeface
        textSize = theme.textSizePx
        isSubpixelText = true
    }
    private val backgroundPaint = Paint()
    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Advance width of one monospaced character.
     *
     * Deliberately NOT rounded up. Grid positions are multiples of this value,
     * and text is drawn at the font's natural advance; rounding the cell up by
     * even half a pixel makes the two drift apart, so after a few dozen columns
     * the characters no longer sit under the cursor and the line looks skewed.
     * Fonts whose advance is not exactly this are handled by scaling each run
     * in [drawRun].
     */
    private var cellWidth = 1f

    /** Line height, from the font's own spacing rather than ascent+descent. */
    private var cellHeight = 1f
    private var fontAscent = 0f
    private var rowHeightPx = 1

    /** Pre-measured advances for ASCII, which is almost all terminal output. */
    private val asciiMeasures = FloatArray(127)

    private val density = resources.displayMetrics.density
    private val scrollbarThumbWidthPx = 4f * density
    private val scrollbarTouchWidthPx = 28f * density
    private val scrollbarMarginPx = 3f * density
    private val scrollbarMinThumbPx = 32f * density

    // ------------------------------------------------------------------- state

    /** Pixels scrolled back from the live bottom; 0 means "showing the cursor". */
    private var scrollPx = 0

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    private var downY = 0f
    private var dragging = false
    private var draggingScrollbar = false
    private var pendingResize: Runnable? = null
    private val pendingFeed = mutableListOf<ByteArray>()
    private var renderScheduled = false
    private val renderRunnable = Runnable {
        renderScheduled = false
        onScrollChanged?.invoke(scrollPx, maxScrollPx())
        invalidate()
    }

    private var cursorBlinkOn = true
    private val cursorBlink = object : Runnable {
        override fun run() {
            cursorBlinkOn = !cursorBlinkOn
            invalidate()
            if (shouldBlink()) postDelayed(this, CURSOR_BLINK_MS)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        recomputeMetrics()
    }

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    private fun recomputeMetrics() {
        textPaint.typeface = theme.typeface
        textPaint.textSize = theme.textSizePx
        textPaint.isSubpixelText = true

        cellWidth = textPaint.measureText("X").coerceAtLeast(1f)
        val squareHeight = round(cellWidth * 2f)
        val fontMetrics = textPaint.fontMetrics
        val fontHeight = ceil(fontMetrics.descent - fontMetrics.ascent)
        cellHeight = maxOf(squareHeight, fontHeight).coerceAtLeast(1f)
        rowHeightPx = cellHeight.toInt().coerceAtLeast(1)

        // Center glyphs vertically within cellHeight
        val verticalPadding = (cellHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f
        fontAscent = fontMetrics.ascent - verticalPadding

        val sb = StringBuilder(" ")
        for (i in asciiMeasures.indices) {
            sb.setCharAt(0, i.toChar())
            asciiMeasures[i] = textPaint.measureText(sb, 0, 1)
        }
    }

    private fun applyTheme(e: TerminalEmulator) {
        val colors = e.mColors.mCurrentColors
        for (i in theme.palette.indices) colors[i] = theme.palette[i]
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background
        colors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
    }

    // --------------------------------------------------------------- lifecycle

    private var lastCols = 0
    private var maxRowsForCols = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyViewSize()
    }

    private fun applyViewSize() {
        if (width <= 0 || height <= 0 || cellWidth <= 0f || cellHeight <= 0f) return
        val cols = (width / cellWidth).toInt().coerceAtLeast(MIN_COLS)
        val rows = (height / cellHeight).toInt().coerceAtLeast(MIN_ROWS)

        // When columns change (device rotation portrait <-> landscape or split-screen),
        // reset the maximum unconstrained rows for the new width.
        if (cols != lastCols) {
            lastCols = cols
            maxRowsForCols = rows
        } else {
            maxRowsForCols = maxOf(maxRowsForCols, rows)
        }

        // On mobile, the soft keyboard temporarily reduces the visible view height.
        // Shrinking the underlying PTY causes destructive ConPTY repaints on Windows
        // and jarring jumps when the keyboard closes.
        // Instead, the emulator and remote PTY are pinned to the full unconstrained
        // height (maxRowsForCols), and computeScreenShift cleanly pans the visible
        // window to keep the cursor and prompt visible above the keyboard.
        val targetRows = maxRowsForCols

        if (emulator == null) {
            // Initial layout must commit immediately without 120ms debounce so that
            // buffered replay frames arriving upon attach are not dropped.
            pendingResize?.let { removeCallbacks(it) }
            pendingResize = null
            commitSize(cols, targetRows)
        } else {
            pendingResize?.let { removeCallbacks(it) }
            val action = Runnable { commitSize(cols, targetRows) }
            pendingResize = action
            postDelayed(action, RESIZE_SETTLE_MS)
        }
    }

    private fun commitSize(cols: Int, rows: Int) {
        pendingResize = null
        val existing = emulator
        if (existing != null && existing.mColumns == cols && existing.mRows == rows) return

        if (existing == null) {
            val created = TerminalEmulator(
                terminalOutput,
                cols,
                rows,
                cellWidth.toInt(),
                cellHeight.toInt(),
                TRANSCRIPT_ROWS,
                sessionClient,
            )
            applyTheme(created)
            emulator = created

            if (pendingFeed.isNotEmpty()) {
                val chunks = ArrayList(pendingFeed)
                pendingFeed.clear()
                for (chunk in chunks) {
                    feed(chunk, chunk.size)
                }
            }
        } else {
            existing.resize(cols, rows, cellWidth.toInt(), cellHeight.toInt())
        }

        scrollPx = scrollPx.coerceIn(0, maxScrollPx())
        scheduleRender()
        onResize?.invoke(cols, rows)
    }

    /** Feeds raw bytes from the shell into the emulator. */
    fun feed(data: ByteArray, length: Int = data.size) {
        val e = emulator
        if (e == null) {
            pendingFeed.add(data.copyOf(length))
            return
        }

        // Read the scroll counter before appending so the viewport can be kept
        // pinned if the reader is looking at history.
        val pushed = e.getScrollCounter()
        e.clearScrollCounter()

        e.append(data, length)

        if (pushed > 0 && scrollPx > 0) {
            // New rows moved into the transcript. The offset is measured from the
            // live bottom, so add them back or the text being read slides upwards.
            scrollPx += pushed * rowHeightPx
        }
        scrollPx = scrollPx.coerceIn(0, maxScrollPx())

        scheduleRender()
        onContentUpdated?.invoke()
    }

    private fun scheduleRender() {
        if (!renderScheduled) {
            renderScheduled = true
            if (isAttachedToWindow) {
                postOnAnimation(renderRunnable)
            } else {
                post(renderRunnable)
            }
        }
    }

    // ---------------------------------------------------------------- geometry

    private fun transcriptRows(): Int = emulator?.screen?.activeTranscriptRows ?: 0

    /**
     * When the view shrinks (e.g. soft keyboard opening) before the debounced
     * resize commits, the physical view height can hold fewer rows than [TerminalEmulator.mRows].
     *
     * In that transition, this returns how many rows down the top of the viewport
     * must shift so that the active cursor and bottom prompt remain visible rather
     * than being clipped off below the keyboard.
     *
     * This mirrors the logic in [TerminalBuffer.resize] by skipping blank rows at the bottom
     * below the cursor (so short command sessions like fresh PowerShell stay at the top,
     * while full-screen TUI apps like pi agent stay pinned at the bottom prompt).
     */
    private fun computeScreenShift(e: TerminalEmulator, visibleRows: Int): Int {
        if (visibleRows >= e.mRows) return 0
        var shift = e.mRows - visibleRows
        val cursorRow = e.cursorRow
        val screen = e.screen
        for (i in e.mRows - 1 downTo 1) {
            if (cursorRow >= i) break
            val r = screen.externalToInternalRow(i)
            val line = screen.allocateFullLineIfNecessary(r)
            if (line.isBlank) {
                if (--shift == 0) break
            }
        }
        return shift.coerceIn(0, e.mRows - visibleRows)
    }

    private fun maxScrollPx(): Int = transcriptRows() * rowHeightPx

    /** Total scrollable distance in pixels; 0 when everything fits on screen. */
    fun scrollRangePx(): Int = maxScrollPx()

    fun scrollOffsetPx(): Int = scrollPx

    val isScrolledBack: Boolean get() = scrollPx > 0

    fun scrollToBottom() {
        if (scrollPx != 0 || !scroller.isFinished) {
            scroller.forceFinished(true)
            scrollPx = 0
            onScrollChanged?.invoke(0, maxScrollPx())
            invalidate()
        }
    }

    /** Column/row count the caller should report to the agent right now. */
    fun currentSize(): Pair<Int, Int> {
        val e = emulator ?: return 0 to 0
        return e.mColumns to e.mRows
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val e = emulator ?: run {
            canvas.drawColor(theme.background)
            return
        }

        canvas.drawColor(theme.background)

        val columns = e.mColumns
        val maxScroll = maxScrollPx()
        scrollPx = scrollPx.coerceIn(0, maxScroll)

        val visibleRows = (height / rowHeightPx).coerceAtLeast(1)
        val screenShift = if (scrollPx == 0) computeScreenShift(e, visibleRows) else 0

        val scrolledRows = scrollPx / rowHeightPx
        val fractional = scrollPx % rowHeightPx
        // External row 0 is the top of the live screen; transcript rows are negative.
        // During resize debounce transitions, screenShift offsets the live viewport
        // downwards so the cursor line is kept on-screen above the keyboard.
        val topRow = screenShift - scrolledRows

        val screen = e.screen
        val palette = e.mColors.mCurrentColors

        // externalToInternalRow only accepts [-activeTranscriptRows, mRows) and
        // throws otherwise. The window needs one row above it because the
        // fractional offset shifts everything down, but that row only exists
        // once there is transcript - so clamp to what is actually addressable.
        val firstExternal = maxOf(topRow - 1, -transcriptRows())
        val lastExternal = minOf(topRow + visibleRows + 1, e.mRows - 1)

        for (externalRow in firstExternal..lastExternal) {
            val screenRow = externalRow - topRow
            val internalRow = screen.externalToInternalRow(externalRow)
            val line = screen.allocateFullLineIfNecessary(internalRow)
            val top = screenRow * cellHeight + fractional
            drawLine(canvas, line, top, columns, palette)
        }

        drawCursor(canvas, e, topRow, fractional)
        drawScrollbar(canvas)
    }

    private fun drawLine(
        canvas: Canvas,
        line: TerminalRow,
        top: Float,
        columns: Int,
        palette: IntArray,
    ) {
        if (top + cellHeight < 0 || top > height) return
        val baseline = top - fontAscent
        val text = line.mText
        val charsUsed = line.spaceUsed

        var column = 0
        var charIndex = 0

        while (column < columns && charIndex < charsUsed) {
            val runStartColumn = column
            val runStartChar = charIndex
            val runStyle = line.getStyle(column)

            val firstCh = text[charIndex]
            val firstHigh = Character.isHighSurrogate(firstCh)
            val firstCodePoint = if (firstHigh && charIndex + 1 < charsUsed) {
                Character.toCodePoint(firstCh, text[charIndex + 1])
            } else {
                firstCh.code
            }
            val firstWidth = WcWidth.width(firstCodePoint)

            if (firstWidth > 1) {
                // Wide character (CJK or wide emoji: occupies firstWidth columns, typically 2)
                val charsForCodePoint = if (firstHigh) 2 else 1
                charIndex += charsForCodePoint
                column += firstWidth

                // Consume any following combining marks
                while (charIndex < charsUsed && WcWidth.width(text, charIndex) <= 0) {
                    charIndex += if (Character.isHighSurrogate(text[charIndex])) 2 else 1
                }

                val measuredWidth = textPaint.measureText(text, runStartChar, charIndex - runStartChar)

                drawRun(
                    canvas = canvas,
                    text = text,
                    startChar = runStartChar,
                    endChar = charIndex,
                    startColumn = runStartColumn,
                    endColumn = column,
                    top = top,
                    baseline = baseline,
                    palette = palette,
                    style = runStyle,
                    measuredWidth = measuredWidth,
                    isWide = true,
                )
            } else {
                // Narrow / ASCII run (width <= 1). Keep grouping consecutive narrow characters
                // of the same style without mixing in wide CJK glyphs.
                var measuredWidth = 0f

                while (column < columns && charIndex < charsUsed) {
                    if (line.getStyle(column) != runStyle) break

                    val ch = text[charIndex]
                    val high = Character.isHighSurrogate(ch)
                    val charsForCodePoint = if (high) 2 else 1
                    if (charIndex + charsForCodePoint > charsUsed) break

                    val codePoint = if (high) {
                        Character.toCodePoint(ch, text[charIndex + 1])
                    } else {
                        ch.code
                    }
                    val width = WcWidth.width(codePoint)
                    if (width > 1) {
                        // Wide character reached: close the narrow run so it is not mixed
                        break
                    }

                    measuredWidth += if (codePoint < asciiMeasures.size) {
                        asciiMeasures[codePoint]
                    } else {
                        textPaint.measureText(text, charIndex, charsForCodePoint)
                    }

                    column += if (width <= 0) 1 else width
                    charIndex += charsForCodePoint

                    // Combining marks belong to the code point just consumed.
                    while (charIndex < charsUsed && WcWidth.width(text, charIndex) <= 0) {
                        charIndex += if (Character.isHighSurrogate(text[charIndex])) 2 else 1
                    }
                }

                drawRun(
                    canvas = canvas,
                    text = text,
                    startChar = runStartChar,
                    endChar = charIndex,
                    startColumn = runStartColumn,
                    endColumn = column,
                    top = top,
                    baseline = baseline,
                    palette = palette,
                    style = runStyle,
                    measuredWidth = measuredWidth,
                    isWide = false,
                )
            }
        }
    }

    private fun drawRun(
        canvas: Canvas,
        text: CharArray,
        startChar: Int,
        endChar: Int,
        startColumn: Int,
        endColumn: Int,
        top: Float,
        baseline: Float,
        palette: IntArray,
        style: Long,
        measuredWidth: Float,
        isWide: Boolean,
    ) {
        if (endChar <= startChar) return

        val runColumns = endColumn - startColumn
        if (runColumns <= 0) return

        val effect = TextStyle.decodeEffect(style)
        val bold = effect and (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_BLINK) != 0
        val underline = effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE != 0
        val italic = effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC != 0
        val strike = effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH != 0
        val isDim = effect and TextStyle.CHARACTER_ATTRIBUTE_DIM != 0
        val invisible = effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE != 0

        var foreColor = TextStyle.decodeForeColor(style)
        var backColor = TextStyle.decodeBackColor(style)

        if (foreColor and OPAQUE_MASK != OPAQUE_MASK) {
            // Bold brightens the first eight palette entries, as most terminals do.
            if (bold && foreColor in 0..7) foreColor += 8
            foreColor = palette[foreColor]
        }
        if (backColor and OPAQUE_MASK != OPAQUE_MASK) {
            backColor = palette[backColor]
        }

        if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0) {
            val tmp = foreColor
            foreColor = backColor
            backColor = tmp
        }

        val left = startColumn * cellWidth
        val right = endColumn * cellWidth
        val allocatedWidth = runColumns * cellWidth
        val effectiveBg = if (backColor != theme.background) backColor else theme.background

        // Draw background block if different from root theme background
        if (backColor != theme.background) {
            backgroundPaint.color = backColor
            canvas.drawRect(left, top, right, top + cellHeight, backgroundPaint)
        }

        if (!invisible) {
            var renderedFore = foreColor
            if (isDim) {
                renderedFore = dim(renderedFore, effectiveBg)
            }
            renderedFore = ensureContrast(renderedFore, effectiveBg)

            textPaint.isFakeBoldText = bold
            textPaint.isUnderlineText = underline
            textPaint.textSkewX = if (italic) -0.35f else 0f
            textPaint.isStrikeThruText = strike
            textPaint.color = renderedFore

            val count = endChar - startChar
            if (isWide) {
                // Wide character in a 2-column square box (width = 2*cellWidth, height = cellHeight).
                // Center the naturally square CJK glyph inside the box.
                // If glyph exceeds allocated box width (e.g. certain wide emojis), scale down to fit.
                if (measuredWidth > allocatedWidth + 0.5f && measuredWidth > 0f) {
                    val scale = allocatedWidth / measuredWidth
                    canvas.save()
                    canvas.scale(scale, 1f, left, baseline)
                    canvas.drawText(text, startChar, count, left, baseline, textPaint)
                    canvas.restore()
                } else {
                    val x = left + (allocatedWidth - measuredWidth) / 2f
                    canvas.drawText(text, startChar, count, x, baseline, textPaint)
                }
            } else {
                // Narrow ASCII / Latin run: perfectly unscaled 1.0x monospace advance.
                // If non-monospace fallback symbols drift, scale with local pivot so position never shifts.
                if (abs(measuredWidth - allocatedWidth) > 1.5f && measuredWidth > 0f) {
                    val scale = allocatedWidth / measuredWidth
                    canvas.save()
                    canvas.scale(scale, 1f, left, baseline)
                    canvas.drawText(text, startChar, count, left, baseline, textPaint)
                    canvas.restore()
                } else {
                    canvas.drawText(text, startChar, count, left, baseline, textPaint)
                }
            }
        }
    }

    /** Dimming: blend 40% towards effective background colour. */
    private fun dim(color: Int, bgColor: Int): Int {
        val rFg = (color shr 16) and 0xFF
        val gFg = (color shr 8) and 0xFF
        val bFg = color and 0xFF
        val rBg = (bgColor shr 16) and 0xFF
        val gBg = (bgColor shr 8) and 0xFF
        val bBg = bgColor and 0xFF
        val r = (rFg * 6 + rBg * 4) / 10
        val g = (gFg * 6 + gBg * 4) / 10
        val b = (bFg * 6 + bBg * 4) / 10
        return OPAQUE_MASK or (r shl 16) or (g shl 8) or b
    }

    /** ITU-R BT.601 perceptual luminance (0f..255f). */
    private fun luminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Guarantees that text remains sharply legible regardless of arbitrary background blocks
     * printed by CLI tools (such as Pi's badges, headers, or diff blocks).
     */
    private fun ensureContrast(foreColor: Int, backColor: Int): Int {
        val bgLum = luminance(backColor)
        val fgLum = luminance(foreColor)
        val diff = abs(fgLum - bgLum)
        val minDiff = 85f

        if (diff >= minDiff) return foreColor

        val r = (foreColor shr 16) and 0xFF
        val g = (foreColor shr 8) and 0xFF
        val b = foreColor and 0xFF

        return if (bgLum >= 128f) {
            // Light background: ensure foreground is sufficiently dark
            val targetLum = (bgLum - minDiff).coerceIn(0f, 120f)
            if (fgLum <= 1f) {
                foreColor
            } else {
                val scale = (targetLum / fgLum).coerceIn(0f, 1f)
                val nr = (r * scale).toInt().coerceIn(0, 255)
                val ng = (g * scale).toInt().coerceIn(0, 255)
                val nb = (b * scale).toInt().coerceIn(0, 255)
                OPAQUE_MASK or (nr shl 16) or (ng shl 8) or nb
            }
        } else {
            // Dark background: ensure foreground is sufficiently bright
            val targetLum = (bgLum + minDiff).coerceIn(135f, 255f)
            if (fgLum >= 254f) {
                foreColor
            } else {
                val t = ((targetLum - fgLum) / (255f - fgLum)).coerceIn(0f, 1f)
                val nr = (r + (255 - r) * t).toInt().coerceIn(0, 255)
                val ng = (g + (255 - g) * t).toInt().coerceIn(0, 255)
                val nb = (b + (255 - b) * t).toInt().coerceIn(0, 255)
                OPAQUE_MASK or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }

    /**
     * Drawn as an overlay rather than inside the text runs: the cursor regularly
     * sits past the end of a line's characters, where no run exists to carry it.
     */
    private fun drawCursor(canvas: Canvas, e: TerminalEmulator, topRow: Int, fractional: Int) {
        if (!e.shouldCursorBeVisible() || scrollPx != 0) return
        val cursorRow = e.cursorRow
        val cursorCol = e.cursorCol
        if (cursorRow < 0 || cursorRow >= e.mRows) return
        if (cursorCol < 0 || cursorCol >= e.mColumns) return
        if (!cursorBlinkOn && hasFocus()) return

        val palette = e.mColors.mCurrentColors
        val left = cursorCol * cellWidth
        // cursorRow and topRow share the same external-row coordinates.
        val top = (cursorRow - topRow) * cellHeight + fractional
        if (top + cellHeight < 0 || top > height) return

        when (e.cursorStyle) {
            TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE -> {
                backgroundPaint.color = palette[TextStyle.COLOR_INDEX_CURSOR]
                canvas.drawRect(left, top + cellHeight - 2f * density, left + cellWidth, top + cellHeight, backgroundPaint)
            }

            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR -> {
                backgroundPaint.color = palette[TextStyle.COLOR_INDEX_CURSOR]
                canvas.drawRect(left, top, left + 2f * density, top + cellHeight, backgroundPaint)
            }

            else -> {
                backgroundPaint.color = palette[TextStyle.COLOR_INDEX_CURSOR]
                backgroundPaint.alpha = if (cursorBlinkOn || !hasFocus()) 255 else 120
                canvas.drawRect(left, top, left + cellWidth, top + cellHeight, backgroundPaint)
                backgroundPaint.alpha = 255

                // Redraw the character underneath in the background colour so a
                // block cursor does not hide what it is sitting on.
                val line = e.screen.allocateFullLineIfNecessary(
                    e.screen.externalToInternalRow(cursorRow),
                )
                val charIdx = line.findStartOfColumn(cursorCol)
                if (charIdx in 0 until line.spaceUsed) {
                    val ch = line.mText[charIdx]
                    if (ch != ' ' && ch != '\u0000') {
                        val isHigh = Character.isHighSurrogate(ch)
                        val str = if (isHigh && charIdx + 1 < line.spaceUsed) {
                            String(line.mText, charIdx, 2)
                        } else {
                            ch.toString()
                        }
                        textPaint.color = palette[TextStyle.COLOR_INDEX_BACKGROUND]
                        canvas.drawText(str, left, top - fontAscent, textPaint)
                    }
                }
            }
        }
    }

    private fun scrollbarThumbRect(): RectF? {
        val max = maxScrollPx()
        if (max <= 0 || height <= 0) return null

        val e = emulator ?: return null
        val visibleLines = (height / rowHeightPx).coerceAtLeast(1)
        val totalLines = (transcriptRows() + visibleLines).coerceAtLeast(1)
        val trackHeight = height.toFloat()
        val thumbHeight = (trackHeight * visibleLines / totalLines.toFloat())
            .coerceIn(scrollbarMinThumbPx, trackHeight)
        val travel = (trackHeight - thumbHeight).coerceAtLeast(0f)

        // scrollPx 0 means "live bottom", so the thumb starts at the bottom.
        val ratio = (1f - scrollPx.toFloat() / max).coerceIn(0f, 1f)
        val thumbTop = travel * ratio
        val left = width - scrollbarThumbWidthPx - scrollbarMarginPx

        return RectF(left, thumbTop, left + scrollbarThumbWidthPx, thumbTop + thumbHeight)
    }

    private fun drawScrollbar(canvas: Canvas) {
        val rect = scrollbarThumbRect() ?: return
        scrollbarPaint.color = theme.foreground
        scrollbarPaint.alpha = if (scrollPx > 0 || draggingScrollbar) 200 else 110
        val radius = scrollbarThumbWidthPx / 2f
        canvas.drawRoundRect(rect, radius, radius, scrollbarPaint)
    }

    // ------------------------------------------------------------------- input

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Modern terminal IME configuration:
        // TYPE_CLASS_TEXT + TYPE_TEXT_VARIATION_VISIBLE_PASSWORD + TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // ensures Android soft keyboards (Gboard, Flyme, Sogou, WeChat, Baidu)
        // do not swallow keystrokes, do not attempt dictionary autocorrect/autocapitalize,
        // and immediately dispatch commits and key events.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    sendTextToTerminal(text)
                }
                super.commitText(text, newCursorPosition)
                editable?.clear()
                return true
            }

            override fun finishComposingText(): Boolean {
                val content = editable
                if (content != null && content.isNotEmpty()) {
                    val text = content.toString()
                    content.clear()
                    sendTextToTerminal(text)
                }
                return super.finishComposingText()
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (handleTerminalKey(event)) return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (event.keyCode != KeyEvent.KEYCODE_BACK) return true
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    if (!sendKeyCode(KeyEventCodes.DEL)) {
                        sendText("\u007f")
                    }
                }
                return true
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    if (!sendKeyCode(KeyEventCodes.DEL)) {
                        sendText("\u007f")
                    }
                }
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyDown(keyCode, event)
        return if (handleTerminalKey(event)) true else super.onKeyDown(keyCode, event)
    }

    /**
     * Sends text produced by the IME.
     *
     * Not the same as [sendText]: this translates for a terminal the way the
     * hardware key path does - `\n` from the Enter key becomes `\r`, and raw
     * control codes are turned back into their Ctrl+letter form.
     */
    private fun sendTextToTerminal(text: CharSequence) {
        if (text.isEmpty()) return

        val ctrl = ctrlLatched
        val alt = altLatched
        if (ctrl || alt) clearLatched()

        var i = 0
        while (i < text.length) {
            val first = text[i]
            var codePoint: Int
            if (Character.isHighSurrogate(first)) {
                if (i + 1 < text.length) {
                    i += 1
                    codePoint = Character.toCodePoint(first, text[i])
                } else {
                    codePoint = UNICODE_REPLACEMENT.toInt()
                }
            } else {
                codePoint = first.code
            }

            var control = ctrl
            if (codePoint <= 31 && codePoint != 27) {
                if (codePoint == '\n'.code) {
                    // Keyboards send \n for Enter; a terminal expects \r.
                    codePoint = '\r'.code
                }
                // Some keyboards express Ctrl combinations as raw control codes.
                control = true
                codePoint = when (codePoint) {
                    31 -> '_'.code
                    30 -> '^'.code
                    29 -> ']'.code
                    28 -> '\\'.code
                    else -> codePoint + 96
                }
            }

            emitCodePoint(codePoint, control, alt)
            i += 1
        }
    }

    /** Writes one code point, applying Ctrl and Alt the way a terminal expects. */
    private fun emitCodePoint(codePoint: Int, control: Boolean, alt: Boolean) {
        var cp = codePoint
        if (control) {
            cp = when {
                cp in 'a'.code..'z'.code -> cp - 'a'.code + 1
                cp in 'A'.code..'Z'.code -> cp - 'A'.code + 1
                cp == ' '.code || cp == '2'.code -> 0
                cp == '['.code || cp == '3'.code -> 27
                cp == '\\'.code || cp == '4'.code -> 28
                cp == ']'.code || cp == '5'.code -> 29
                cp == '^'.code || cp == '6'.code -> 30
                cp == '_'.code || cp == '7'.code || cp == '/'.code -> 31
                cp == '8'.code -> 127
                else -> cp
            }
        }
        if (cp < 0) return

        val out = StringBuilder(2)
        if (alt) out.append(ESC)
        out.appendCodePoint(cp)
        onOutput?.invoke(out.toString().toByteArray(Charsets.UTF_8))
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        onOutput?.invoke(text.toByteArray(Charsets.UTF_8))
    }

    /** Sends a raw sequence from the on-screen key bar or a pasted block. */
    fun sendSequence(sequence: String, scrollToBottom: Boolean = true) {
        sendText(sequence)
        if (scrollToBottom) scrollToBottom()
    }

    /**
     * Sends a key by its Android key code, encoded by the emulator's own
     * KeyHandler so cursor-keys / keypad application modes are respected. The
     * on-screen key bar uses this instead of hardcoding escape sequences.
     */
    fun sendKeyCode(keyCode: Int): Boolean {
        val e = emulator ?: return false
        val sequence = KeyHandler.getCode(
            keyCode,
            0,
            e.isCursorKeysApplicationMode,
            e.isKeypadApplicationMode,
        ) ?: return false
        sendText(sequence)
        scrollToBottom()
        return true
    }

    /** Wraps pasted text in bracketed-paste markers when the program asked for them. */
    fun paste(text: String) {
        val e = emulator ?: return
        e.paste(text)
        scrollToBottom()
    }

    private fun clearLatched() {
        ctrlLatched = false
        altLatched = false
    }

    private fun keyMode(event: KeyEvent): Int {
        var mode = 0
        if (event.isCtrlPressed || ctrlLatched) mode = mode or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || altLatched) mode = mode or KeyHandler.KEYMOD_ALT
        if (event.isShiftPressed) mode = mode or KeyHandler.KEYMOD_SHIFT
        return mode
    }

    private fun handleTerminalKey(event: KeyEvent): Boolean {
        val e = emulator ?: return false

        // BACK belongs to the host, not the terminal. KeyHandler maps it to ESC,
        // which would swallow the key before Compose's BackHandler could use it
        // to navigate out of the terminal.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false

        val mode = keyMode(event)

        val sequence = KeyHandler.getCode(
            event.keyCode,
            mode,
            e.isCursorKeysApplicationMode,
            e.isKeypadApplicationMode,
        )
        if (sequence != null) {
            clearLatched()
            sendText(sequence)
            scrollToBottom()
            return true
        }

        // Not a special key: use the character the keyboard produced.
        var unicode = event.unicodeChar
        if (unicode == 0) {
            unicode = event.getUnicodeChar(event.metaState)
        }
        if (unicode == 0) {
            try {
                unicode = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).get(event.keyCode, event.metaState)
            } catch (_: Exception) {}
        }
        if (unicode == 0) {
            val shift = event.isShiftPressed xor event.isCapsLockOn
            unicode = when (event.keyCode) {
                in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                    val base = if (shift) 'A'.code else 'a'.code
                    base + (event.keyCode - KeyEvent.KEYCODE_A)
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    if (event.isShiftPressed) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_1 -> '!'.code
                            KeyEvent.KEYCODE_2 -> '@'.code
                            KeyEvent.KEYCODE_3 -> '#'.code
                            KeyEvent.KEYCODE_4 -> '$'.code
                            KeyEvent.KEYCODE_5 -> '%'.code
                            KeyEvent.KEYCODE_6 -> '^'.code
                            KeyEvent.KEYCODE_7 -> '&'.code
                            KeyEvent.KEYCODE_8 -> '*'.code
                            KeyEvent.KEYCODE_9 -> '('.code
                            KeyEvent.KEYCODE_0 -> ')'.code
                            else -> '0'.code + (event.keyCode - KeyEvent.KEYCODE_0)
                        }
                    } else {
                        '0'.code + (event.keyCode - KeyEvent.KEYCODE_0)
                    }
                }
                KeyEvent.KEYCODE_SPACE -> ' '.code
                KeyEvent.KEYCODE_MINUS -> if (event.isShiftPressed) '_'.code else '-'.code
                KeyEvent.KEYCODE_EQUALS -> if (event.isShiftPressed) '+'.code else '='.code
                KeyEvent.KEYCODE_SLASH -> if (event.isShiftPressed) '?'.code else '/'.code
                KeyEvent.KEYCODE_BACKSLASH -> if (event.isShiftPressed) '|'.code else '\\'.code
                KeyEvent.KEYCODE_PERIOD -> if (event.isShiftPressed) '>'.code else '.'.code
                KeyEvent.KEYCODE_COMMA -> if (event.isShiftPressed) '<'.code else ','.code
                KeyEvent.KEYCODE_SEMICOLON -> if (event.isShiftPressed) ':'.code else ';'.code
                KeyEvent.KEYCODE_APOSTROPHE -> if (event.isShiftPressed) '"'.code else '\''.code
                KeyEvent.KEYCODE_GRAVE -> if (event.isShiftPressed) '~'.code else '`'.code
                KeyEvent.KEYCODE_LEFT_BRACKET -> if (event.isShiftPressed) '{'.code else '['.code
                KeyEvent.KEYCODE_RIGHT_BRACKET -> if (event.isShiftPressed) '}'.code else ']'.code
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> '\r'.code
                KeyEvent.KEYCODE_TAB -> '\t'.code
                else -> 0
            }
        }
        if (unicode != 0) {
            emitCodePoint(
                codePoint = unicode,
                control = mode and KeyHandler.KEYMOD_CTRL != 0,
                alt = mode and KeyHandler.KEYMOD_ALT != 0,
            )
            clearLatched()
            scrollToBottom()
            return true
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x >= width - scrollbarTouchWidthPx && maxScrollPx() > 0) {
                    draggingScrollbar = true
                    dragging = false
                    scroller.forceFinished(true)
                    dragScrollbarTo(event.y)
                } else {
                    scroller.forceFinished(true)
                    draggingScrollbar = false
                    downY = event.y
                    dragging = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingScrollbar) {
                    dragScrollbarTo(event.y)
                } else {
                    val delta = event.y - downY
                    if (!dragging && abs(delta) > touchSlop) dragging = true
                    if (dragging) {
                        scrollByPixels(delta.toInt())
                        downY = event.y
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when {
                    draggingScrollbar -> {
                        draggingScrollbar = false
                        invalidate()
                    }

                    dragging && event.actionMasked == MotionEvent.ACTION_UP -> startFling()

                    !dragging && event.actionMasked == MotionEvent.ACTION_UP -> {
                        requestFocus()
                        showKeyboard()
                        onTap?.invoke()
                        scrollToBottom()
                        performClick()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun startFling() {
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(VELOCITY_UNITS_PER_SECOND)
        val velocityY = tracker.yVelocity
        if (abs(velocityY) < minFlingVelocity) return

        // fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY):
        // the offset lives on the Y axis, so the bounds belong in the Y slots.
        // scrollPx counts backwards from the newest line, so a downward flick
        // (positive yVelocity) increases it and the sign needs no adjustment.
        scroller.fling(0, scrollPx, 0, velocityY.toInt(), 0, 0, 0, maxScrollPx())
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPx = scroller.currY
            onScrollChanged?.invoke(scrollPx, maxScrollPx())
            postInvalidateOnAnimation()
        }
    }

    private fun scrollByPixels(deltaPx: Int) {
        val next = (scrollPx + deltaPx).coerceIn(0, maxScrollPx())
        if (next != scrollPx) {
            scrollPx = next
            onScrollChanged?.invoke(scrollPx, maxScrollPx())
            invalidate()
        }
    }

    private fun dragScrollbarTo(touchY: Float) {
        val rect = scrollbarThumbRect() ?: return
        val travel = (height - rect.height()).coerceAtLeast(1f)
        val thumbTop = (touchY - rect.height() / 2f).coerceIn(0f, travel)
        val ratio = thumbTop / travel
        val max = maxScrollPx()
        val next = ((1f - ratio) * max).toInt().coerceIn(0, max)
        if (next != scrollPx) {
            scrollPx = next
            onScrollChanged?.invoke(scrollPx, max)
            invalidate()
        }
    }

    // -------------------------------------------------------------- IME / focus

    /**
     * Shows the soft keyboard.
     *
     * Focusing alone is not enough: once the view already holds focus (which it
     * does after the first tap) `requestFocus()` is a no-op, so the keyboard never
     * comes back after being dismissed. The call is posted so it runs after the
     * touch event finishes dispatching, because some IMEs ignore `showSoftInput`
     * invoked from inside `onTouchEvent`.
     */
    override fun requestRectangleOnScreen(rectangle: android.graphics.Rect?, immediate: Boolean): Boolean {
        // Prevent Android ViewRootImpl from scrolling/panning the Activity window to bring this native view to (0,0),
        // which pushes the top navigation bar off-screen.
        return false
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
        val result = super.requestFocus(direction, previouslyFocusedRect)
        rootView?.scrollTo(0, 0)
        return result
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rootView?.scrollTo(0, 0)
    }

    fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        if (!hasFocus()) requestFocus()
        post {
            if (!imm.showSoftInput(this, 0)) {
                imm.restartInput(this)
                imm.showSoftInput(this, 0)
            }
        }
    }

    private fun shouldBlink(): Boolean = hasFocus() && scrollPx == 0

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && shouldBlink()) {
            removeCallbacks(cursorBlink)
            postDelayed(cursorBlink, CURSOR_BLINK_MS)
        } else {
            removeCallbacks(cursorBlink)
            cursorBlinkOn = true
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(cursorBlink)
        removeCallbacks(renderRunnable)
        renderScheduled = false
        pendingResize?.let { removeCallbacks(it) }
        pendingResize = null
    }

    private companion object {
        const val TRANSCRIPT_ROWS = 5000
        const val MIN_COLS = 2
        const val MIN_ROWS = 2
        const val CURSOR_BLINK_MS = 500L
        const val VELOCITY_UNITS_PER_SECOND = 1000
        const val RESIZE_SETTLE_MS = 120L
        const val ESC = '\u001b'
        const val UNICODE_REPLACEMENT = 0xFFFD
        const val OPAQUE_MASK = 0xFF000000.toInt()
    }
}
