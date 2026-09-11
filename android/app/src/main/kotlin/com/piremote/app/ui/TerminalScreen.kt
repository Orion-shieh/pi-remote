package com.piremote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.piremote.app.data.SessionRepository
import com.piremote.app.data.TerminalEvent
import com.piremote.terminal.KeyEventCodes
import com.piremote.terminal.view.TerminalView
import kotlinx.coroutines.flow.collectLatest

/**
 * Hosts the terminal and its on-screen key bar.
 *
 * The key bar exists because a soft keyboard has no Esc, Tab, arrows or Ctrl —
 * all of which a TUI like pi needs constantly.
 */
@Composable
fun TerminalScreen(
    repository: SessionRepository,
    sessionId: String,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Keyed on the session so switching sessions builds a fresh view instead of
    // reusing the previous session's screen. Doing it during composition rather
    // than in a LaunchedEffect matters: replay frames are buffered and may
    // already have been fed to the view by the time effects run.
    val terminalView = remember(sessionId) {
        TerminalView(context).apply {
            onOutput = { bytes -> repository.sendRaw(bytes) }
            onResize = { cols, rows ->
                // Remember the geometry so a newly created session starts at a
                // sensible size instead of a guess.
                repository.pendingResize.value = cols to rows
                repository.resize(cols, rows)
            }
            onTitleChanged = { repository.onTitleChanged(it) }
            onClipboardCopy = { text -> copyToClipboard(context, text) }
            onTap = { showKeyboard() }
        }
    }

    LaunchedEffect(sessionId) {
        terminalView.showKeyboard()
    }

    LaunchedEffect(sessionId) {
        repository.events.collectLatest { event ->
            when (event) {
                is TerminalEvent.Output -> terminalView.feed(event.data)
                TerminalEvent.Reset -> Unit
                TerminalEvent.ReplayDone -> terminalView.scrollToBottom()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        Column(
            Modifier
                .fillMaxSize()
                // targetSdk 35 forces edge-to-edge on Android 15+, where
                // adjustResize no longer shrinks the window and the IME simply
                // covers the content. Without this the key bar sits underneath
                // the keyboard and the terminal never learns its real size.
                .imePadding(),
        ) {
            TerminalTopBar(title, sessionId, terminalView, onBack)

            AndroidView(
                factory = { terminalView },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            ExtraKeysBar(terminalView)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
}

@Composable
private fun TerminalTopBar(
    title: String,
    sessionId: String,
    terminalView: TerminalView,
    onBack: () -> Unit,
) {
    var scrolledBack by remember { mutableStateOf(false) }
    LaunchedEffect(terminalView) {
        terminalView.onScrollChanged = { offset, range ->
            scrolledBack = offset > 0
            android.util.Log.d("PiRemoteScroll", "offset=${offset}px range=${range}px")
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("← 返回", onClick = onBack)
        Text(
            text = title.ifEmpty { sessionId.take(8) },
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        if (scrolledBack) {
            KeyChip("回到底部") { terminalView.scrollToBottom() }
        }
    }
}

@Composable
private fun ExtraKeysBar(terminalView: TerminalView) {
    val context = LocalContext.current
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }

    LaunchedEffect(ctrl, alt) {
        terminalView.ctrlLatched = ctrl
        terminalView.altLatched = alt
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToggleChip("Ctrl", ctrl) { ctrl = !ctrl }
            ToggleChip("Alt", alt) { alt = !alt }
            KeyChip("Esc") { terminalView.sendKeyCode(KeyEventCodes.ESCAPE) }
            KeyChip("Tab") { terminalView.sendKeyCode(KeyEventCodes.TAB) }
            // Control codes go through KeyHandler like any other key, so the
            // latched Ctrl modifier applies the same way the soft keyboard does.
            KeyChip("^C") { terminalView.sendSequence("\u0003") }
            KeyChip("^D") { terminalView.sendSequence("\u0004") }
            KeyChip("^O") { terminalView.sendSequence("\u000f") }
            KeyChip("^L") { terminalView.sendSequence("\u000c") }
            KeyChip("^Z") { terminalView.sendSequence("\u001a") }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Routed through KeyHandler so DECCKM (application cursor keys)
            // is honoured; a hardcoded ESC[A breaks TUI programs that set it.
            KeyChip("↑") { terminalView.sendKeyCode(KeyEventCodes.DPAD_UP) }
            KeyChip("↓") { terminalView.sendKeyCode(KeyEventCodes.DPAD_DOWN) }
            KeyChip("←") { terminalView.sendKeyCode(KeyEventCodes.DPAD_LEFT) }
            KeyChip("→") { terminalView.sendKeyCode(KeyEventCodes.DPAD_RIGHT) }
            KeyChip("Home") { terminalView.sendKeyCode(KeyEventCodes.MOVE_HOME) }
            KeyChip("End") { terminalView.sendKeyCode(KeyEventCodes.MOVE_END) }
            KeyChip("PgUp") { terminalView.sendKeyCode(KeyEventCodes.PAGE_UP) }
            KeyChip("PgDn") { terminalView.sendKeyCode(KeyEventCodes.PAGE_DOWN) }
            KeyChip("⏎") { terminalView.sendKeyCode(KeyEventCodes.ENTER) }
            KeyChip("粘贴") {
                val text = readClipboard(context)
                if (text.isNotEmpty()) terminalView.paste(text)
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        onClick = onClick,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        onClick = onClick,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
