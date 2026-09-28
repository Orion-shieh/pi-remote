package com.piremote.app.ui

import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import com.piremote.app.data.LinuxEnvironmentManager
import com.piremote.app.data.LinuxEnvironmentStatus
import com.piremote.app.data.SettingsStore
import com.piremote.app.data.ToolchainInfo
import com.piremote.app.data.agent.LocalAgentEngine
import com.piremote.app.data.agent.LocalAgentSessionStore
import com.piremote.app.data.agent.SerializedAiSession
import com.piremote.app.ui.agent.LocalAgentChatScreen
import com.piremote.app.ui.agent.LocalAgentConfigDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import com.piremote.app.R
import com.piremote.terminal.KeyEventCodes
import com.piremote.terminal.view.TerminalTheme
import com.piremote.terminal.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

// ---------------------------------------------------------------- Local Shell Session

/**
 * Manages an individual native Android /system/bin/sh local process.
 */
class LocalShellSession(
    private val context: Context,
    private val customDir: File? = null,
) {
    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var sessionScope: CoroutineScope? = null
    private var readJob: Job? = null

    var onOutput: ((ByteArray) -> Unit)? = null
    var onStateChanged: ((Boolean, Int?) -> Unit)? = null

    var isAlive: Boolean = false
        private set
    var pid: Int? = null
        private set

    var currentDir: File = (customDir?.takeIf { it.exists() && it.isDirectory } ?: context.filesDir).canonicalFile
        private set

    // Line discipline & command history for pseudo-terminal emulation over pipe
    private val inputBuffer = StringBuilder()
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1
    private var tempCurrentInput = ""
    private var lastExecutedCmd = ""
    private var isInteractiveRunning: Boolean = false

    private fun isInteractiveCommand(cmd: String): Boolean {
        val trimmed = cmd.trim()
        val first = trimmed.split(Regex("\\s+")).firstOrNull() ?: ""
        return first == "pi" || first == "claude" || first == "top" ||
               first == "htop" || first == "nano" || first == "vi" ||
               first == "vim" || first == "python" || first == "python3" ||
               first == "node" || first == "npx"
    }

    companion object {
        private const val SENTINEL_PREFIX = "__PIREMOTE_DONE__:"
    }

    fun formatPrompt(): String {
        val envMgr = LinuxEnvironmentManager.getInstance(context)
        val isAlpine = envMgr.isAlpineRootfsInstalled() && envMgr.isNativeProotBundled()
        if (isAlpine) {
            val rootfsPath = envMgr.rootfsDir.canonicalPath
            val currentPath = currentDir.canonicalPath
            val relPath = if (currentPath.startsWith(rootfsPath)) {
                currentPath.removePrefix(rootfsPath).ifEmpty { "/" }
            } else {
                currentPath
            }
            return "\u001B[1;31mroot@localhost\u001B[0m:\u001B[1;34m$relPath\u001B[0m# "
        }
        val home = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val displayPath = try {
            val path = currentDir.canonicalPath
            when {
                path.startsWith(home.canonicalPath) -> path.replace(home.canonicalPath, "~/Download")
                path.startsWith(context.filesDir.canonicalPath) -> path.replace(context.filesDir.canonicalPath, "~")
                else -> path
            }
        } catch (_: Exception) {
            currentDir.path
        }
        val user = if (envMgr.isNativeBashBundled()) "bash" else "android"
        return "\u001B[1;32m$user@localhost\u001B[0m:\u001B[1;34m$displayPath\u001B[0m$ "
    }

    fun updateTerminalSize(cols: Int, rows: Int) {
        val envMgr = LinuxEnvironmentManager.getInstance(context)
        try {
            val statfixDir = File(envMgr.rootfsDir, "opt/statfix")
            statfixDir.mkdirs()
            val termSizeFile = File(statfixDir, "term_size")
            termSizeFile.writeText("$cols $rows")
        } catch (_: Exception) {}
    }

    fun printPrompt() {
        onOutput?.invoke(formatPrompt().toByteArray(Charsets.UTF_8))
    }

    fun start() {
        stop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sessionScope = scope
        try {
            val envMgr = LinuxEnvironmentManager.getInstance(context)
            val isAlpine = envMgr.isAlpineRootfsInstalled() && envMgr.isNativeProotBundled()
            val isBash = envMgr.isNativeBashBundled()

            if (customDir != null && !customDir.exists()) {
                try {
                    customDir.mkdirs()
                } catch (_: Exception) {}
            }
            currentDir = (customDir?.takeIf { it.exists() && it.isDirectory } ?: context.filesDir).canonicalFile

            val prootExec = envMgr.getProotExecutable()
            val bashExec = envMgr.getBashExecutable()

            if (isAlpine && prootExec != null) {
                envMgr.ensureRootfsStructureAndPermissions()
                currentDir = File(envMgr.rootfsDir, "root").canonicalFile
            }

            val commandList = when {
                isAlpine && prootExec != null -> {
                    val bindArgs = mutableListOf<String>()
                    if (File("/dev").exists()) { bindArgs.add("-b"); bindArgs.add("/dev") }
                    if (File("/proc").exists()) { bindArgs.add("-b"); bindArgs.add("/proc") }
                    if (File("/sys").exists()) { bindArgs.add("-b"); bindArgs.add("/sys") }
                    val storageHost = File("/storage")
                    if (storageHost.exists()) { bindArgs.add("-b"); bindArgs.add("/storage") }
                    val sdcardHost = File("/sdcard")
                    if (sdcardHost.exists()) { bindArgs.add("-b"); bindArgs.add("/sdcard") }
                    if (envMgr.nativeDir.exists()) { bindArgs.add("-b"); bindArgs.add("${envMgr.nativeDir.absolutePath}:/native") }

                    val list = mutableListOf(
                        prootExec.absolutePath,
                        "--link2symlink",
                        "-0",
                        "-r", envMgr.rootfsDir.absolutePath,
                    )
                    list.addAll(bindArgs)
                    list.add("-w")
                    list.add("/root")
                    val alpineShell = when {
                        File(envMgr.rootfsDir, "bin/bash").exists() -> "/bin/bash"
                        File(envMgr.rootfsDir, "usr/bin/bash").exists() -> "/usr/bin/bash"
                        else -> "/bin/sh"
                    }
                    list.add(alpineShell)
                    list
                }
                isBash && bashExec != null -> {
                    val bashrc = File(envMgr.homeDir, ".bashrc")
                    listOf(
                        bashExec.absolutePath,
                        "--init-file", bashrc.absolutePath,
                    )
                }
                else -> {
                    listOf("/system/bin/sh")
                }
            }

            val hostDir = if (isAlpine && prootExec != null) envMgr.rootfsDir else currentDir
            val pb = ProcessBuilder(commandList)
            pb.directory(hostDir)
            pb.redirectErrorStream(true)

            val dm = context.resources.displayMetrics
            val approxCellWidth = 12f * dm.scaledDensity * 0.605f
            val approxCols = (dm.widthPixels / approxCellWidth).toInt().coerceIn(35, 120)
            val approxRows = ((dm.heightPixels * 0.65f) / (12f * dm.scaledDensity * 1.25f)).toInt().coerceIn(15, 60)
            updateTerminalSize(approxCols, approxRows)

            val env = pb.environment()
            envMgr.configureEnvironment(env, currentDir)

            val proc = pb.start()
            process = proc
            outputStream = proc.outputStream
            isAlive = true

            pid = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pidField = proc.javaClass.getDeclaredField("pid")
                    pidField.isAccessible = true
                    pidField.getInt(proc)
                } else null
            } catch (_: Exception) {
                null
            }

            onStateChanged?.invoke(true, pid)

            val tc = envMgr.toolchain.value
            val banner = buildString {
                append("\u001B[1;36m┌────────────────────────────────────────────────────────┐\u001B[0m\r\n")
                when {
                    isAlpine -> {
                        append("\u001B[1;32m│  Alpine Linux 完整子系统 (PRoot seccomp 硬件加速)       │\u001B[0m\r\n")
                        val hasStatxFix = File(envMgr.rootfsDir, "opt/statfix/statx_shim.so").exists()
                        if (hasStatxFix) {
                            append("\u001B[90m│  环境加固: statx 垫片就绪 • POSIX shm • Git 免鉴权    │\u001B[0m\r\n")
                        } else {
                            append("\u001B[90m│  包管理器: apk (支持 apk add bash git python3 curl)      │\u001B[0m\r\n")
                        }
                    }
                    isBash -> {
                        append("\u001B[1;32m│  GNU Bash 完整 Linux 环境已就绪                       │\u001B[0m\r\n")
                        append("\u001B[90m│  指令集: Bash 5.x • 100+ BusyBox 工具 • Node.js        │\u001B[0m\r\n")
                    }
                    else -> {
                        append("\u001B[1;33m│  Android 原生精简 Shell (/system/bin/sh)              │\u001B[0m\r\n")
                        append("\u001B[90m│  提示: 可在「环境诊断」中一键部署完整 Alpine Linux 发行版 │\u001B[0m\r\n")
                    }
                }
                append("\u001B[90m│  系统架构: Linux ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"} (Android ${Build.VERSION.RELEASE})           │\u001B[0m\r\n")
                if (tc.hasNode) {
                    append("\u001B[36m│  Node.js: ${tc.nodeVersion} | npm: ${tc.npmVersion ?: "就绪"}                        │\u001B[0m\r\n")
                }
                append("\u001B[1;36m└────────────────────────────────────────────────────────┘\u001B[0m\r\n\r\n")
            }
            onOutput?.invoke(banner.toByteArray(Charsets.UTF_8))
            printPrompt()

            readJob = scope.launch {
                val stream: InputStream = proc.inputStream
                val buffer = ByteArray(4096)
                val accumulator = StringBuilder()
                try {
                    while (isActive) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        accumulator.append(text)

                        // Process accumulator looking for sentinel line
                        while (true) {
                            val markerIndex = accumulator.indexOf(SENTINEL_PREFIX)
                            if (markerIndex != -1) {
                                val newlineIndex = accumulator.indexOf('\n', markerIndex)
                                if (newlineIndex != -1) {
                                    // Complete sentinel line found
                                    val beforeMarker = accumulator.substring(0, markerIndex)
                                    val sentinelLine = accumulator.substring(markerIndex, newlineIndex)
                                    accumulator.delete(0, newlineIndex + 1)

                                    var cleaned = beforeMarker
                                    if (lastExecutedCmd.isNotBlank()) {
                                        cleaned = cleaned.removePrefix(lastExecutedCmd).trimStart('\r', '\n')
                                    }
                                    cleaned = cleaned.replace(Regex("""(?:\r?\n)?echo\s+\"?$"""), "")
                                    cleaned = cleaned.replace(Regex("""(?:\r?\n)?echo\s+\"[^\"]*?$"""), "").trimEnd()

                                    if (cleaned.isNotEmpty()) {
                                        var normalized = cleaned.replace(Regex("""(?<!\r)\n"""), "\r\n")
                                        if (!normalized.endsWith("\r\n")) {
                                            normalized += "\r\n"
                                        }
                                        withContext(Dispatchers.Main) {
                                            onOutput?.invoke(normalized.toByteArray(Charsets.UTF_8))
                                        }
                                    }

                                    val parts = sentinelLine.removePrefix(SENTINEL_PREFIX).trim().split(":", limit = 2)
                                    val newPwd = parts.getOrNull(1)?.trim()
                                    if (!newPwd.isNullOrBlank()) {
                                        try {
                                            val dirFile = File(newPwd)
                                            if (dirFile.exists() && dirFile.isDirectory) {
                                                currentDir = dirFile.canonicalFile
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    withContext(Dispatchers.Main) {
                                        isInteractiveRunning = false
                                        printPrompt()
                                    }
                                    continue
                                } else {
                                    // Marker started but line not complete yet; emit whatever is before marker
                                    if (markerIndex > 0) {
                                        val toEmit = accumulator.substring(0, markerIndex)
                                        accumulator.delete(0, markerIndex)
                                        val normalized = toEmit.replace(Regex("""(?<!\r)\n"""), "\r\n")
                                        withContext(Dispatchers.Main) {
                                            onOutput?.invoke(normalized.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    break
                                }
                            } else {
                                // Sentinel not present; check if accumulator ends with partial prefix of SENTINEL_PREFIX
                                var partialLen = 0
                                for (len in 1..minOf(accumulator.length, SENTINEL_PREFIX.length)) {
                                    if (accumulator.endsWith(SENTINEL_PREFIX.substring(0, len))) {
                                        partialLen = len
                                    }
                                }
                                val safeEmitLen = accumulator.length - partialLen
                                if (safeEmitLen > 0) {
                                    val toEmit = accumulator.substring(0, safeEmitLen)
                                    accumulator.delete(0, safeEmitLen)
                                    val normalized = toEmit.replace(Regex("""(?<!\r)\n"""), "\r\n")
                                    withContext(Dispatchers.Main) {
                                        onOutput?.invoke(normalized.toByteArray(Charsets.UTF_8))
                                    }
                                }
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    if (accumulator.isNotEmpty()) {
                        val remaining = accumulator.toString().replace(Regex("""(?<!\r)\n"""), "\r\n")
                        withContext(Dispatchers.Main) {
                            onOutput?.invoke(remaining.toByteArray(Charsets.UTF_8))
                        }
                        accumulator.clear()
                    }
                    val exitCode = try { proc.waitFor() } catch (_: Exception) { -1 }
                    withContext(Dispatchers.Main) {
                        isAlive = false
                        onStateChanged?.invoke(false, null)
                        val codeStr = if (exitCode != -1) " (代码: $exitCode)" else ""
                        val exitMsg = "\r\n\u001B[1;33m[Shell 进程已退出$codeStr]\u001B[0m\r\n"
                        onOutput?.invoke(exitMsg.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PhoneTerminal", "Failed to start local shell", e)
            val errMsg = "\r\n\u001B[1;31m[无法启动本地 Shell: ${e.localizedMessage}]\u001B[0m\r\n"
            onOutput?.invoke(errMsg.toByteArray(Charsets.UTF_8))
            isAlive = false
            onStateChanged?.invoke(false, null)
        }
    }

    /**
     * Translates raw terminal input through pseudo-line discipline:
     * - Immediate local echo of typed characters, numbers, and CJK text
     * - Enter key translation (\r -> commit line with \n to shell stdin)
     * - Backspace character and terminal column erasure
     * - Ctrl+C / Ctrl+U handling
     * - Command history navigation (Up / Down arrows)
     */
    fun send(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (isInteractiveRunning) {
            sessionScope?.launch(Dispatchers.IO) {
                try {
                    outputStream?.write(bytes)
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
            return
        }
        val text = String(bytes, Charsets.UTF_8)
        var i = 0
        while (i < text.length) {
            val ch = text[i]

            // ANSI Escape sequences (arrows, cursor keys)
            if (ch == '\u001b') {
                if (i + 2 < text.length && text[i + 1] == '[') {
                    when (text[i + 2]) {
                        'A' -> { // Up Arrow
                            handleUpArrow()
                            i += 3
                            continue
                        }
                        'B' -> { // Down Arrow
                            handleDownArrow()
                            i += 3
                            continue
                        }
                        'C', 'D' -> { // Right / Left
                            i += 3
                            continue
                        }
                    }
                }
                i++
                continue
            }

            when (ch) {
                '\r', '\n' -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                        i++
                    }
                    handleEnter()
                }
                '\u007f', '\b' -> {
                    handleBackspace()
                }
                '\u0003' -> { // Ctrl+C
                    handleCtrlC()
                }
                '\u0015' -> { // Ctrl+U (kill line)
                    handleCtrlU()
                }
                '\u000c' -> { // Ctrl+L (clear screen)
                    executeCommandLine("clear")
                }
                '\t' -> { // Tab
                    inputBuffer.append("  ")
                    onOutput?.invoke("  ".toByteArray(Charsets.UTF_8))
                }
                else -> {
                    inputBuffer.append(ch)
                    onOutput?.invoke(ch.toString().toByteArray(Charsets.UTF_8))
                }
            }
            i++
        }
    }

    private fun handleEnter() {
        val cmd = inputBuffer.toString()
        inputBuffer.clear()
        onOutput?.invoke("\r\n".toByteArray(Charsets.UTF_8))
        executeCommandLine(cmd)
    }

    private fun executeCommandLine(rawCmd: String) {
        val clean = rawCmd.trimEnd('\r', '\n')
        if (clean.isBlank()) {
            printPrompt()
            return
        }

        if (clean == "clear") {
            onOutput?.invoke("\u001B[H\u001B[2J".toByteArray(Charsets.UTF_8))
            printPrompt()
            return
        }

        if (clean == "exit") {
            stop()
            onOutput?.invoke("\r\n\u001B[1;33m[Shell 进程已退出]\u001B[0m\r\n".toByteArray(Charsets.UTF_8))
            return
        }

        if (commandHistory.isEmpty() || commandHistory.last() != clean) {
            commandHistory.add(clean)
        }
        historyIndex = -1
        tempCurrentInput = ""

        val envMgr = LinuxEnvironmentManager.getInstance(context)
        val tc = envMgr.toolchain.value
        val isAlpine = envMgr.isAlpineRootfsInstalled() && envMgr.isNativeProotBundled()
        val isNodeCommand = clean.startsWith("npm") || clean.startsWith("node") || clean.startsWith("npx") || clean == "pi" || clean == "claude"
        if (!isAlpine && !tc.hasNode && isNodeCommand) {
            val helpMsg = buildString {
                append("\r\n\u001B[1;33m[环境提示] 手机本地原生精简 Shell 未配置 Node.js CLI\u001B[0m\r\n")
                append("\u001B[90m当前运行于 Android 原生免装环境，用于基础命令与工作区文件管理。\u001B[0m\r\n\r\n")
                append("\u001B[1;36m[方案 1: 本地免装环境 AI 工作台 (推荐)]\u001B[0m\r\n")
                append("• 点击顶部的「AI 工作台」按钮，直接使用手机纯原生 AI 智能体。\r\n")
                append("• 支持自然语言生成代码、Web 实时预览、创建文档，零外部环境依赖。\r\n\r\n")
                append("\u001B[1;36m[方案 2: 切换至电脑终端 (PC Pi Agent)]\u001B[0m\r\n")
                append("• 点击屏幕底部「电脑终端」标签，无缝连接 PC 端完整环境。\r\n\r\n")
            }
            onOutput?.invoke(helpMsg.toByteArray(Charsets.UTF_8))
            printPrompt()
            return
        }

        val effectiveCmd = clean
        lastExecutedCmd = clean
        val isInteractive = isInteractiveCommand(clean)
        if (isInteractive) {
            isInteractiveRunning = true
            envMgr.ensureRootfsStructureAndPermissions()
        }

        sessionScope?.launch(Dispatchers.IO) {
            try {
                val proc = process
                if (!isAlive || proc == null || !proc.isAlive) {
                    withContext(Dispatchers.Main) {
                        onOutput?.invoke("\r\n\u001B[33m[Shell 进程已退出，正在尝试重启...]\u001B[0m\r\n".toByteArray(Charsets.UTF_8))
                        start()
                    }
                    return@launch
                }
                val os = outputStream
                if (os != null) {
                    if (isInteractive) {
                        // Interactive programs (pi, claude, etc.) manage their own input and echo __PIREMOTE_DONE__ on exit
                        os.write("$effectiveCmd\n".toByteArray(Charsets.UTF_8))
                    } else {
                        val wrapped = "$effectiveCmd\necho \"$SENTINEL_PREFIX\$?:\$(pwd)\"\n"
                        os.write(wrapped.toByteArray(Charsets.UTF_8))
                    }
                    os.flush()
                } else {
                    withContext(Dispatchers.Main) {
                        onOutput?.invoke("\r\n\u001B[31m[执行失败: Shell 未运行]\u001B[0m\r\n".toByteArray(Charsets.UTF_8))
                        printPrompt()
                    }
                }
            } catch (e: Exception) {
                Log.e("PhoneTerminal", "Failed to send command to local shell", e)
                withContext(Dispatchers.Main) {
                    onOutput?.invoke("\r\n\u001B[31m[执行失败: ${e.localizedMessage}]\u001B[0m\r\n".toByteArray(Charsets.UTF_8))
                    printPrompt()
                }
            }
        }
    }

    private fun handleBackspace() {
        if (inputBuffer.isNotEmpty()) {
            val lastChar = inputBuffer.last()
            inputBuffer.deleteCharAt(inputBuffer.length - 1)
            val isWide = lastChar.code in 0x2E80..0x9FFF || lastChar.code in 0xAC00..0xD7AF || lastChar.code in 0xF900..0xFAFF
            val erase = if (isWide) "\u0008\u0008  \u0008\u0008" else "\u0008 \u0008"
            onOutput?.invoke(erase.toByteArray(Charsets.UTF_8))
        }
    }

    private fun handleCtrlC() {
        onOutput?.invoke("^C\r\n".toByteArray(Charsets.UTF_8))
        inputBuffer.clear()
        historyIndex = -1
        tempCurrentInput = ""
        isInteractiveRunning = false
        sessionScope?.launch(Dispatchers.IO) {
            try {
                outputStream?.write("\u0003\n".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (_: Exception) {}
        }
        printPrompt()
    }

    private fun handleCtrlU() {
        if (inputBuffer.isNotEmpty()) {
            val erase = buildString {
                for (c in inputBuffer) {
                    val isWide = c.code in 0x2E80..0x9FFF || c.code in 0xAC00..0xD7AF || c.code in 0xF900..0xFAFF
                    append(if (isWide) "\u0008\u0008  \u0008\u0008" else "\u0008 \u0008")
                }
            }
            inputBuffer.clear()
            onOutput?.invoke(erase.toByteArray(Charsets.UTF_8))
        }
    }

    private fun handleUpArrow() {
        if (commandHistory.isEmpty()) return
        if (historyIndex == -1) {
            tempCurrentInput = inputBuffer.toString()
            historyIndex = commandHistory.size - 1
        } else if (historyIndex > 0) {
            historyIndex--
        } else {
            return
        }
        replaceInputWith(commandHistory[historyIndex])
    }

    private fun handleDownArrow() {
        if (historyIndex == -1) return
        if (historyIndex < commandHistory.size - 1) {
            historyIndex++
            replaceInputWith(commandHistory[historyIndex])
        } else {
            historyIndex = -1
            replaceInputWith(tempCurrentInput)
        }
    }

    private fun replaceInputWith(newText: String) {
        if (inputBuffer.isNotEmpty()) {
            val erase = buildString {
                for (c in inputBuffer) {
                    val isWide = c.code in 0x2E80..0x9FFF || c.code in 0xAC00..0xD7AF || c.code in 0xF900..0xFAFF
                    append(if (isWide) "\u0008\u0008  \u0008\u0008" else "\u0008 \u0008")
                }
            }
            onOutput?.invoke(erase.toByteArray(Charsets.UTF_8))
        }
        inputBuffer.clear()
        inputBuffer.append(newText)
        if (newText.isNotEmpty()) {
            onOutput?.invoke(newText.toByteArray(Charsets.UTF_8))
        }
    }

    fun sendCommand(cmd: String) {
        val clean = cmd.trimEnd('\r', '\n')
        if (isInteractiveRunning) {
            sessionScope?.launch(Dispatchers.IO) {
                try {
                    outputStream?.write("$clean\n".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
            return
        }
        if (inputBuffer.isNotEmpty()) {
            val erase = buildString {
                for (c in inputBuffer) {
                    val isWide = c.code in 0x2E80..0x9FFF || c.code in 0xAC00..0xD7AF || c.code in 0xF900..0xFAFF
                    append(if (isWide) "\u0008\u0008  \u0008\u0008" else "\u0008 \u0008")
                }
            }
            onOutput?.invoke(erase.toByteArray(Charsets.UTF_8))
            inputBuffer.clear()
        }
        onOutput?.invoke("$clean\r\n".toByteArray(Charsets.UTF_8))
        executeCommandLine(clean)
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        sessionScope?.cancel()
        sessionScope = null
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null
        process?.destroy()
        process = null
        isAlive = false
        isInteractiveRunning = false
        pid = null
        inputBuffer.clear()
        historyIndex = -1
        tempCurrentInput = ""
        onStateChanged?.invoke(false, null)
    }
}

// ---------------------------------------------------------------- Session State & Manager

enum class PhoneSessionKind {
    AI_AGENT, // 手机免装环境 AI 智能体工作台 (GUI)
    SHELL,    // Android 原生 Linux 精简 Shell (PTY)
}

class PhoneSessionState(
    val id: String,
    val kind: PhoneSessionKind = PhoneSessionKind.SHELL,
    initialTitle: String,
    val workingDir: String,
    val startTime: Long,
    val initialCommand: String? = null,
    val session: LocalShellSession? = null,
    val terminalView: TerminalView? = null,
    val engine: LocalAgentEngine? = null,
    initialAlive: Boolean = true,
    initialLastActive: Long = startTime,
    initialModelName: String? = null,
) {
    var title by mutableStateOf(initialTitle)
    var alive by mutableStateOf(initialAlive)
    var lastActiveTime by mutableStateOf(initialLastActive)
    var pid by mutableStateOf<Int?>(null)
    var modelName by mutableStateOf(initialModelName)
}

/**
 * Manages phone terminal & AI agent sessions, tracking running processes, AI engines, and active session.
 */
class PhoneSessionManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: PhoneSessionManager? = null

        fun getInstance(context: Context): PhoneSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhoneSessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val store = LocalAgentSessionStore(context)
    private val settings = SettingsStore(context)
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _sessions = mutableStateListOf<PhoneSessionState>()
    val sessions: List<PhoneSessionState> get() = _sessions

    val activeSessionId = MutableStateFlow<String?>(null)
    val isLocalAgentActive = MutableStateFlow(false)

    private var nextShellNumber = 1
    private var nextAiNumber = 1

    init {
        loadPersistedSessions()
    }

    private fun loadPersistedSessions() {
        val saved = store.loadAllSessions()
        for (item in saved) {
            val engine = LocalAgentEngine(context)
            engine.restoreSession(item.items, item.conversationHistory)
            val sessionState = PhoneSessionState(
                id = item.id,
                kind = PhoneSessionKind.AI_AGENT,
                initialTitle = item.title,
                workingDir = item.workingDir,
                startTime = item.startTime,
                engine = engine,
                initialAlive = item.alive,
                initialLastActive = item.lastActiveTime,
                initialModelName = item.modelName,
            )
            engine.onSessionUpdated = {
                sessionState.lastActiveTime = System.currentTimeMillis()
                saveAiSession(sessionState)
                val idx = _sessions.indexOf(sessionState)
                if (idx > 0) {
                    _sessions.removeAt(idx)
                    _sessions.add(0, sessionState)
                }
            }
            _sessions.add(sessionState)
        }
        nextAiNumber = (_sessions.count { it.kind == PhoneSessionKind.AI_AGENT } + 1)
    }

    fun saveAiSession(state: PhoneSessionState) {
        if (state.kind != PhoneSessionKind.AI_AGENT || state.engine == null) return
        val items = state.engine.getItems()
        if (items.isEmpty()) {
            store.deleteSession(state.id)
            return
        }
        store.saveSession(
            SerializedAiSession(
                id = state.id,
                title = state.title,
                workingDir = state.workingDir,
                startTime = state.startTime,
                lastActiveTime = state.lastActiveTime,
                alive = state.alive,
                modelName = state.modelName ?: settings.localAgentModel,
                items = items,
                conversationHistory = state.engine.getConversationHistory(),
            )
        )
    }

    fun cleanEmptyAiSessions(exceptId: String? = null) {
        val toRemove = _sessions.filter { session ->
            session.kind == PhoneSessionKind.AI_AGENT &&
                session.id != exceptId &&
                session.engine?.getItems()?.isEmpty() == true &&
                session.engine?.isRunning?.value != true
        }
        for (session in toRemove) {
            _sessions.remove(session)
            store.deleteSession(session.id)
        }
    }

    fun openNewAiDraftSession(workingDir: String? = null): String {
        cleanEmptyAiSessions()
        return createAiSession(workingDir = workingDir)
    }

    fun getOrCreateDefaultAiSession(workingDir: String? = null): String {
        return openNewAiDraftSession(workingDir = workingDir)
    }

    fun createAiSession(
        title: String? = null,
        workingDir: String? = null,
    ): String {
        val id = "phone_ai_sess_${System.currentTimeMillis()}_${(100..999).random()}"
        val num = nextAiNumber++
        val sessionTitle = title ?: "AI 创作 #$num"
        val dir = workingDir?.takeIf { it.isNotBlank() }
            ?: settings.localAgentWorkspace.takeIf { it.isNotBlank() }
            ?: context.filesDir.absolutePath

        val engine = LocalAgentEngine(context)
        val sessionState = PhoneSessionState(
            id = id,
            kind = PhoneSessionKind.AI_AGENT,
            initialTitle = sessionTitle,
            workingDir = dir,
            startTime = System.currentTimeMillis(),
            engine = engine,
            initialAlive = true,
            initialLastActive = System.currentTimeMillis(),
            initialModelName = settings.localAgentModel,
        )

        engine.onSessionUpdated = {
            sessionState.lastActiveTime = System.currentTimeMillis()
            saveAiSession(sessionState)
            val idx = _sessions.indexOf(sessionState)
            if (idx > 0) {
                _sessions.removeAt(idx)
                _sessions.add(0, sessionState)
            }
        }

        _sessions.add(0, sessionState)
        activeSessionId.value = id
        isLocalAgentActive.value = true
        return id
    }

    fun createSession(
        title: String? = null,
        workingDir: String? = null,
        initialCommand: String? = null,
    ): String {
        val id = "phone_sess_${System.currentTimeMillis()}_${(100..999).random()}"
        val num = nextShellNumber++
        val sessionTitle = title ?: "Shell #$num"
        val dir = workingDir?.takeIf { it.isNotBlank() } ?: context.filesDir.absolutePath

        val shellSession = LocalShellSession(context, customDir = File(dir))
        val terminalView = TerminalView(context).apply {
            (parent as? ViewGroup)?.removeView(this)
            onOutput = { bytes -> shellSession.send(bytes) }
            onClipboardCopy = { text -> copyToClipboard(context, text) }
            onTap = { showKeyboard() }
            onResize = { cols, rows ->
                shellSession.updateTerminalSize(cols, rows)
            }
        }

        val sessionState = PhoneSessionState(
            id = id,
            kind = PhoneSessionKind.SHELL,
            initialTitle = sessionTitle,
            workingDir = dir,
            startTime = System.currentTimeMillis(),
            initialCommand = initialCommand,
            session = shellSession,
            terminalView = terminalView,
            initialAlive = true,
            initialLastActive = System.currentTimeMillis(),
        )

        shellSession.onOutput = { bytes ->
            terminalView.feed(bytes)
        }
        shellSession.onStateChanged = { alive, procPid ->
            sessionState.alive = alive
            sessionState.pid = procPid
            sessionState.lastActiveTime = System.currentTimeMillis()
        }

        shellSession.start()
        if (!initialCommand.isNullOrBlank()) {
            managerScope.launch {
                delay(200)
                shellSession.sendCommand(initialCommand)
            }
        }

        _sessions.add(0, sessionState)
        activeSessionId.value = id
        isLocalAgentActive.value = false
        return id
    }

    fun openSession(id: String) {
        cleanEmptyAiSessions(exceptId = id)
        var target = _sessions.find { it.id == id }
        if (target == null) {
            val saved = store.loadAllSessions().find { it.id == id }
            if (saved != null && saved.items.isNotEmpty()) {
                val engine = LocalAgentEngine(context)
                engine.restoreSession(saved.items, saved.conversationHistory)
                val sessionState = PhoneSessionState(
                    id = saved.id,
                    kind = PhoneSessionKind.AI_AGENT,
                    initialTitle = saved.title,
                    workingDir = saved.workingDir,
                    startTime = saved.startTime,
                    engine = engine,
                    initialAlive = saved.alive,
                    initialLastActive = saved.lastActiveTime,
                    initialModelName = saved.modelName,
                )
                engine.onSessionUpdated = {
                    sessionState.lastActiveTime = System.currentTimeMillis()
                    saveAiSession(sessionState)
                    val idx = _sessions.indexOf(sessionState)
                    if (idx > 0) {
                        _sessions.removeAt(idx)
                        _sessions.add(0, sessionState)
                    }
                }
                _sessions.add(sessionState)
                target = sessionState
            }
        }
        activeSessionId.value = id
        isLocalAgentActive.value = (target?.kind == PhoneSessionKind.AI_AGENT)
    }

    fun detachActive() {
        cleanEmptyAiSessions()
        activeSessionId.value = null
        isLocalAgentActive.value = false
    }

    fun killSession(id: String) {
        val target = _sessions.find { it.id == id } ?: return
        if (target.kind == PhoneSessionKind.SHELL) {
            target.session?.stop()
        }
        target.alive = false
        target.lastActiveTime = System.currentTimeMillis()
        if (target.kind == PhoneSessionKind.AI_AGENT) {
            if (target.engine?.getItems()?.isEmpty() == true) {
                _sessions.remove(target)
                store.deleteSession(target.id)
                return
            }
            saveAiSession(target)
        }
        if (activeSessionId.value == id) {
            activeSessionId.value = null
            isLocalAgentActive.value = false
        }
    }

    fun restartSession(id: String) {
        val target = _sessions.find { it.id == id } ?: return
        target.alive = true
        target.lastActiveTime = System.currentTimeMillis()

        if (target.kind == PhoneSessionKind.SHELL) {
            target.session?.start()
            target.initialCommand?.let { cmd ->
                managerScope.launch {
                    delay(200)
                    target.session?.sendCommand(cmd)
                }
            }
        } else if (target.kind == PhoneSessionKind.AI_AGENT) {
            saveAiSession(target)
        }

        // Move to top of list
        _sessions.remove(target)
        _sessions.add(0, target)

        // Open immediately
        openSession(id)
    }

    fun forgetSession(id: String) {
        val target = _sessions.find { it.id == id } ?: return
        if (target.kind == PhoneSessionKind.SHELL) {
            target.session?.stop()
        } else if (target.kind == PhoneSessionKind.AI_AGENT) {
            store.deleteSession(id)
        }
        _sessions.remove(target)
        if (activeSessionId.value == id) {
            activeSessionId.value = null
            isLocalAgentActive.value = false
        }
    }
}

// ---------------------------------------------------------------- Preset Commands

data class PhoneQuickAction(
    val title: String,
    val command: String,
    val icon: ImageVector,
)

private val PRESET_QUICK_ACTIONS = listOf(
    PhoneQuickAction("清屏", "clear\n", Icons.Default.CleaningServices),
    PhoneQuickAction("工作目录", "pwd && ls -la\n", Icons.Default.Folder),
    PhoneQuickAction("Git 状态", "git status\n", Icons.Default.Source),
    PhoneQuickAction("Node 状态", "node -v 2>/dev/null && npm -v 2>/dev/null || echo 'Node 未安装'\n", Icons.Default.Code),
    PhoneQuickAction("npm 列表", "npm list -g --depth=0 2>/dev/null || echo 'npm 未配置'\n", Icons.Default.Inventory2),
    PhoneQuickAction("环境检查", "which node npm git bash 2>&1; echo \"PATH=\$PATH\"\n", Icons.Default.Search),
)

// ---------------------------------------------------------------- Main Entry

/**
 * Dedicated Local Android Phone Terminal Screen.
 * Mirrors the PC Session List & Fullscreen Terminal architecture.
 */
@Composable
fun PhoneTerminalScreen(
    manager: PhoneSessionManager = PhoneSessionManager.getInstance(LocalContext.current),
    onSwitchToPc: (() -> Unit)? = null,
) {
    val activeSessionId by manager.activeSessionId.collectAsState()

    HierarchicalPushPopContainer(
        targetSessionId = activeSessionId,
        onDetach = { manager.detachActive() },
        underlyingContent = {
            PhoneSessionListScreen(
                manager = manager,
                onSwitchToPc = onSwitchToPc,
                onOpenLocalAgent = { cwd ->
                    val aiId = manager.openNewAiDraftSession(workingDir = cwd)
                    manager.openSession(aiId)
                },
            )
        },
        activeContent = { sid, requestExit ->
            val activeSession = manager.sessions.find { it.id == sid }
            if (activeSession != null) {
                if (activeSession.kind == PhoneSessionKind.AI_AGENT) {
                    key(activeSession.id) {
                        LocalAgentChatScreen(
                            session = activeSession,
                            manager = manager,
                            onBack = requestExit,
                        )
                    }
                } else {
                    // Fullscreen terminal workspace with dedicated top bar & extra keys
                    PhoneTerminalFullscreenScreen(
                        manager = manager,
                        session = activeSession,
                        onBack = requestExit,
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------- Phone Session List Screen

/**
 * Session Center for Phone Terminal.
 * Standardized to PC Terminal style: Hero Header, Status Capsule, collapsible Presets, spring bounce, grouped Ended Sessions.
 */
@Composable
private fun PhoneSessionListScreen(
    manager: PhoneSessionManager,
    onSwitchToPc: (() -> Unit)? = null,
    onOpenLocalAgent: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val sessions = manager.sessions
    val running = remember(sessions.map { Triple(it.id, it.alive, it.engine?.getItems()?.size ?: 0) to it.lastActiveTime }) {
        sessions.filter { it.alive && (it.kind != PhoneSessionKind.AI_AGENT || it.engine?.getItems()?.isNotEmpty() == true) }
            .sortedByDescending { it.lastActiveTime }
    }
    val ended = remember(sessions.map { Triple(it.id, it.alive, it.engine?.getItems()?.size ?: 0) to it.lastActiveTime }) {
        sessions.filterNot { it.alive }
            .filter { it.kind != PhoneSessionKind.AI_AGENT || it.engine?.getItems()?.isNotEmpty() == true }
            .sortedByDescending { it.lastActiveTime }
    }

    val defaultPiWorkspace = remember {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val piDir = File(downloadDir, "PiAgent")
        try {
            if (!piDir.exists()) piDir.mkdirs()
        } catch (_: Exception) {}
        if (piDir.exists()) piDir.absolutePath else downloadDir.absolutePath
    }

    var selectedCwd by remember { mutableStateOf(defaultPiWorkspace) }
    var showAgentConfigDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var isCompactAtTop by remember { mutableStateOf(false) }
    var expandedPresetId by remember { mutableStateOf<String?>(null) }
    var expandedCardBounds by remember { mutableStateOf<Rect?>(null) }

    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }
    val isCompact = isListScrolled || isCompactAtTop

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val collapseThresholdPx = with(density) { 56.dp.toPx() }
    var collapseConsumedPx by remember { mutableFloatStateOf(0f) }
    var isAbsorbingCollapse by remember { mutableStateOf(false) }
    var lastCollapseTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isCompactAtTop) {
        if (!isCompactAtTop) {
            collapseConsumedPx = 0f
            isAbsorbingCollapse = false
        }
    }

    LaunchedEffect(isCompact) {
        if (isCompact && expandedPresetId != null) {
            expandedPresetId = null
        }
    }

    val maxBouncePx = with(density) { 60.dp.toPx() }
    var bounceOffsetPx by remember { mutableFloatStateOf(0f) }
    var springJob by remember { mutableStateOf<Job?>(null) }

    fun triggerSpringBack() {
        if (bounceOffsetPx < -0.5f) {
            springJob?.cancel()
            springJob = scope.launch {
                Animatable(bounceOffsetPx).animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) {
                    bounceOffsetPx = value
                }
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && bounceOffsetPx < -0.5f && springJob?.isActive != true) {
            triggerSpringBack()
        }
    }

    val nestedScrollConnection = remember(density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user touches and drags while spring animation is running, cancel it
                if (source == NestedScrollSource.UserInput && springJob?.isActive == true) {
                    springJob?.cancel()
                    springJob = null
                }

                val now = System.currentTimeMillis()
                if (isAbsorbingCollapse && now - lastCollapseTime > 400L) {
                    isAbsorbingCollapse = false
                    collapseConsumedPx = 0f
                }

                // If at the top of the list and user pulls down, immediately expand!
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    if (isAbsorbingCollapse) {
                        isAbsorbingCollapse = false
                        collapseConsumedPx = 0f
                    }
                    if (isCompactAtTop && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        isCompactAtTop = false
                        collapseConsumedPx = 0f
                        return Offset(0f, available.y)
                    }
                }

                // If at the top of the list and user swipes up while expanded:
                // Absorb the swipe to collapse presets; session list does NOT move upward!
                if (source == NestedScrollSource.UserInput && available.y < 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    if (!isCompactAtTop && !isAbsorbingCollapse) {
                        isAbsorbingCollapse = true
                        collapseConsumedPx = 0f
                        isCompactAtTop = true
                    }
                }

                if (isAbsorbingCollapse && available.y < 0f) {
                    lastCollapseTime = now
                    val remaining = (collapseThresholdPx - collapseConsumedPx).coerceAtLeast(0f)
                    if (remaining > 0f) {
                        val toConsume = available.y.coerceAtLeast(-remaining)
                        collapseConsumedPx += -toConsume
                        return Offset(0f, toConsume)
                    }
                }

                // If currently overscrolled at bottom (pulled up) and user drags back down,
                // consume downward scroll to bring bounce offset back to 0 before scrolling the list
                if (bounceOffsetPx < 0f && available.y > 0f) {
                    val consumedY = available.y.coerceAtMost(-bounceOffsetPx)
                    bounceOffsetPx += consumedY
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (abs(available.y) > abs(available.x)) {
                    // 1. Pull down at top: immediately expand presets bar
                    if (available.y > 0f && isCompactAtTop) {
                        isCompactAtTop = false
                    }
                    // 2. Collapse presets when content is short and user swipes up
                    else if (available.y < -10f && !isCompactAtTop) {
                        isCompactAtTop = true
                    }

                    // 3. iOS-style rubber-band overscroll when pulled up at the bottom
                    if (source == NestedScrollSource.UserInput && available.y < 0f) {
                        val damping = (1f - (-bounceOffsetPx / maxBouncePx).coerceIn(0f, 0.85f)) * 0.35f
                        val delta = available.y * damping
                        val newOffset = (bounceOffsetPx + delta).coerceIn(-maxBouncePx, 0f)
                        val consumedDelta = newOffset - bounceOffsetPx
                        bounceOffsetPx = newOffset
                        return Offset(0f, if (damping > 0.01f) consumedDelta / damping else available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (bounceOffsetPx < -0.5f) {
                    triggerSpringBack()
                    return available
                }
                if (isAbsorbingCollapse) {
                    isAbsorbingCollapse = false
                    collapseConsumedPx = 0f
                    // If the list is still at top (light short swipe/flick), swallow the fling completely!
                    if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        return available
                    }
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isAbsorbingCollapse = false
                collapseConsumedPx = 0f
                if (abs(available.y) > abs(available.x)) {
                    if (available.y < -100f && !isCompactAtTop) {
                        isCompactAtTop = true
                    } else if (available.y > 40f && isCompactAtTop) {
                        isCompactAtTop = false
                    }
                }
                if (bounceOffsetPx < -0.5f) {
                    triggerSpringBack()
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeekColors.DeepCanvas)
            .nestedScroll(nestedScrollConnection)
            .pointerInput(expandedPresetId) {
                if (expandedPresetId == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed }
                        if (down != null) {
                            val pos = down.position
                            val bounds = expandedCardBounds
                            if (bounds != null && !bounds.contains(pos)) {
                                expandedPresetId = null
                                down.consume()
                            }
                        }
                    }
                }
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // App Hero Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Apple App Icon squircle badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardSurface,
                    border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "\$_",
                            color = GeekColors.TerminalCyan,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "手机终端",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = GeekColors.TextPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GeekColors.TerminalCyan.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "本机会话",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                ),
                                color = GeekColors.TerminalCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Android 原生 Linux Shell · 本机免 Root 会话中心",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = GeekColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Agent Configuration & Quick Package Installer button
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GeekColors.CardSurface,
                border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pressClickEffect(),
                onClick = { showAgentConfigDialog = true },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Agent 配置与安装",
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        // Phone Status Capsule
        PhoneDeviceCapsule()

        // Presets horizontal quick launcher
        Box(
            modifier = Modifier
                .zIndex(1f)
                .pointerInput(isCompact) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.pressed) {
                                val deltaY = change.position.y - change.previousPosition.y
                                if (deltaY > 5f && isCompact) {
                                    isCompactAtTop = false
                                    collapseConsumedPx = 0f
                                    isAbsorbingCollapse = false
                                    if (isListScrolled) {
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                } else if (deltaY < -5f && !isCompact && listState.firstVisibleItemIndex == 0) {
                                    isCompactAtTop = true
                                    collapseConsumedPx = collapseThresholdPx
                                    isAbsorbingCollapse = false
                                }
                            }
                        }
                    }
                },
        ) {
            PhoneQuickPresetsRow(
                expandedPresetId = expandedPresetId,
                isCompact = isCompact,
                selectedCwd = selectedCwd,
                onExpandedPresetChange = { expandedPresetId = it },
                onCardBoundsChange = { expandedCardBounds = it },
                onCompactToggle = {
                    if (isCompact) {
                        isCompactAtTop = false
                        collapseConsumedPx = 0f
                        isAbsorbingCollapse = false
                        scope.launch { listState.animateScrollToItem(0) }
                    } else {
                        isCompactAtTop = true
                        collapseConsumedPx = collapseThresholdPx
                        isAbsorbingCollapse = false
                    }
                },
                onLaunch = { title, cmd ->
                    manager.createSession(
                        title = title,
                        workingDir = selectedCwd,
                        initialCommand = cmd,
                    )
                },
                onSwitchToPc = onSwitchToPc,
                onOpenLocalAgent = { onOpenLocalAgent?.invoke(selectedCwd) },
            )
        }

        // Main Sessions List with strict viewport boundary clipping
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = bounceOffsetPx },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                // Running Section
                item {
                    SectionHeader(title = "运行中的会话", count = running.size, isActive = true)
                }

                if (running.isEmpty()) {
                    item {
                        EmptyStateCard("暂无运行中的手机会话。点击上方「本地 AI Agent」或「Linux 终端」即可立即开始。")
                    }
                }

                items(running, key = { "run-${it.id}" }) { session ->
                    RunningPhoneSessionCard(
                        session = session,
                        onOpen = { manager.openSession(session.id) },
                        onKill = { manager.killSession(session.id) },
                    )
                }

                // Ended Section
                if (ended.isNotEmpty()) {
                    item {
                        SectionHeader(title = "历史会话", count = ended.size, isActive = false)
                    }

                    item {
                        EndedPhoneSessionsGroupCard(
                            sessions = ended,
                            onOpen = { manager.openSession(it.id) },
                            onForget = { manager.forgetSession(it.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAgentConfigDialog) {
        AgentConfigDialog(
            targetEnvironmentName = "手机终端 (Android)",
            onDismiss = { showAgentConfigDialog = false },
            onInstall = { title, cmd ->
                showAgentConfigDialog = false
                Toast.makeText(context, "正在为手机端拉起会话: $title...", Toast.LENGTH_SHORT).show()
                manager.createSession(
                    title = title,
                    workingDir = selectedCwd,
                    initialCommand = cmd,
                )
            },
        )
    }
}

// ---------------------------------------------------------------- Device Capsule & CWD

@Composable
private fun PhoneDeviceCapsule() {
    val context = LocalContext.current
    val envMgr = remember { LinuxEnvironmentManager.getInstance(context) }
    val status by envMgr.status.collectAsState()
    val toolchain by envMgr.toolchain.collectAsState()
    var showDetailDialog by remember { mutableStateOf(false) }

    val (badgeText, badgeColor, badgeGlow) = when {
        status == LinuxEnvironmentStatus.INITIALIZING -> Triple("正在部署 Linux...", GeekColors.AmberWarn, GeekColors.AmberWarnGlow)
        toolchain.hasAlpine -> Triple("Alpine Linux 就绪", GeekColors.NeonGreen, GeekColors.NeonGreenGlow)
        toolchain.hasBash -> Triple("GNU Bash 就绪", GeekColors.NeonGreen, GeekColors.NeonGreenGlow)
        status == LinuxEnvironmentStatus.READY -> Triple("本地 Linux 就绪", GeekColors.NeonGreen, GeekColors.NeonGreenGlow)
        else -> Triple("本地 Shell 就绪", GeekColors.TerminalCyan, GeekColors.TerminalCyanGlow)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(18.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(color = badgeColor, glowColor = badgeGlow)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = badgeColor,
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GeekColors.CardElevated,
                border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pressClickEffect(),
                onClick = { showDetailDialog = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "环境诊断",
                        tint = GeekColors.TerminalCyan,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "环境诊断",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = GeekColors.TerminalCyan,
                    )
                }
            }
        }
    }

    if (showDetailDialog) {
        val envMgr = remember { LinuxEnvironmentManager.getInstance(context) }
        LinuxDetailDialog(
            envMgr = envMgr,
            onDismiss = { showDetailDialog = false },
        )
    }
}

@Composable
private fun PhoneCwdSelectorRow(
    currentPath: String,
    onPathSelected: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GeekColors.BrandAccentGlow,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "工作目录",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextMuted,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentPath.ifBlank { "默认工作目录" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (currentPath.isNotBlank()) GeekColors.TerminalCyan else GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GeekColors.CardElevated,
                border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pressClickEffect(),
                onClick = { showDialog = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "选择目录",
                        tint = GeekColors.TerminalCyan,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "更改",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }
            }
        }
    }

    if (showDialog) {
        PhoneCwdPickerDialog(
            initialPath = currentPath,
            onConfirm = {
                onPathSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun PhoneCwdPickerDialog(
    initialPath: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialPath) }

    val presetDirs = remember {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val piWorkspace = File(downloadDir, "PiAgent").apply {
            try { if (!exists()) mkdirs() } catch (_: Exception) {}
        }.absolutePath
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        listOf(
            "Pi Agent 推荐工作区 (下载/PiAgent)" to piWorkspace,
            "手机下载目录 (Download)" to downloadDir.absolutePath,
            "手机文档目录 (Documents)" to docsDir,
            "手机内部存储根目录 (/sdcard)" to storageRoot,
            "应用私有沙盒目录 (内部运行环境)" to context.filesDir.absolutePath,
        )
    }

    val hasFullStorage = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择手机工作目录", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("工作目录路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeekColors.TerminalCyan,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                    ),
                )

                if (!hasFullStorage) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.AmberWarn.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, GeekColors.AmberWarn.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (_: Exception) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = GeekColors.AmberWarn,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "点击授予「所有文件访问权限」，以便读写外部存储",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeekColors.AmberWarn,
                            )
                        }
                    }
                }

                Text("常用推荐目录：", style = MaterialTheme.typography.labelSmall, color = GeekColors.TextMuted)

                presetDirs.forEach { (name, path) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (text == path) GeekColors.BrandAccentGlow else GeekColors.CardElevated,
                        border = BorderStroke(1.dp, if (text == path) GeekColors.BrandAccent else GeekColors.BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .pressClickEffect(),
                        onClick = { text = path },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = GeekColors.TextPrimary)
                            Text(path, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = GeekColors.TextMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = GeekColors.CardSurface,
    )
}

// ---------------------------------------------------------------- Quick Presets Section

private enum class PhonePresetIcon {
    Pi,
    Terminal,
    Shell,
    RemotePc,
}

@Composable
private fun PhoneQuickPresetsRow(
    expandedPresetId: String? = null,
    isCompact: Boolean = false,
    selectedCwd: String,
    onExpandedPresetChange: (String?) -> Unit = {},
    onCardBoundsChange: (Rect?) -> Unit = {},
    onCompactToggle: (() -> Unit)? = null,
    onLaunch: (title: String, cmd: String?) -> Unit,
    onSwitchToPc: (() -> Unit)? = null,
    onOpenLocalAgent: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val envMgr = remember { LinuxEnvironmentManager.getInstance(context) }
    val toolchain by envMgr.toolchain.collectAsState()

    val launchTitle = when {
        toolchain.hasAlpine -> "Alpine Linux"
        toolchain.hasBash -> "Linux (Bash)"
        else -> "Shell"
    }
    val launchBtnText = when {
        toolchain.hasAlpine -> "Alpine Linux"
        toolchain.hasBash -> "Linux 终端"
        else -> "Shell 终端"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (onCompactToggle != null) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onCompactToggle() }
                    } else Modifier
                )
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "新建会话",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    fontSize = 12.sp,
                ),
                color = GeekColors.TextMuted,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCompact) "展开" else "收起",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = GeekColors.TextMuted.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (isCompact) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isCompact) "展开" else "收起",
                    tint = GeekColors.TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Preset 1: Local AI Agent (Expandable with GUI / PTY drawer)
            PhoneQuickPresetCard(
                presetId = "phone-ai",
                name = "本地 AI Agent",
                tagline = "极简 Agent Harness",
                displayPath = selectedCwd,
                badgeText = "双视图",
                badgeBg = GeekColors.BrandPurple.copy(alpha = 0.14f),
                badgeTint = GeekColors.BrandPurple,
                iconType = PhonePresetIcon.Pi,
                isExpandable = true,
                isExpanded = expandedPresetId == "phone-ai",
                isCompact = isCompact,
                onExpandedPresetChange = onExpandedPresetChange,
                onCardBoundsChange = onCardBoundsChange,
                onClick = { onExpandedPresetChange("phone-ai") },
                expandedContent = {
                    PiModeOptionCard(
                        icon = Icons.Default.SmartToy,
                        iconTint = GeekColors.BrandPurple,
                        title = "图形视图",
                        subtitle = "结构化事件流 · 推荐",
                        tag = "GUI",
                        tagColor = GeekColors.BrandPurple,
                        onClick = {
                            onExpandedPresetChange(null)
                            if (onOpenLocalAgent != null) {
                                onOpenLocalAgent()
                            } else {
                                onLaunch("本地 AI (GUI)", "pi")
                            }
                        },
                    )
                    PiModeOptionCard(
                        icon = Icons.Default.Terminal,
                        iconTint = GeekColors.TerminalCyan,
                        title = "终端视图",
                        subtitle = "底层 PTY 原生命令行",
                        tag = "PTY",
                        tagColor = GeekColors.TerminalCyan,
                        onClick = {
                            onExpandedPresetChange(null)
                            onLaunch("本地 AI (PTY)", "pi")
                        },
                    )
                },
            )

            // Preset 2: Linux Terminal (Alpine / Bash)
            PhoneQuickPresetCard(
                presetId = "phone-linux",
                name = launchBtnText,
                tagline = if (toolchain.hasAlpine) "Alpine Linux 环境" else if (toolchain.hasBash) "GNU Bash 环境" else "Linux 环境",
                displayPath = if (toolchain.hasAlpine) "/root" else selectedCwd,
                badgeText = "Linux",
                badgeBg = GeekColors.TerminalCyan.copy(alpha = 0.14f),
                badgeTint = GeekColors.TerminalCyan,
                iconType = PhonePresetIcon.Terminal,
                isExpandable = false,
                isExpanded = false,
                isCompact = isCompact,
                onExpandedPresetChange = onExpandedPresetChange,
                onCardBoundsChange = onCardBoundsChange,
                onClick = { onLaunch(launchTitle, null) },
            )
        }
    }
}

@Composable
private fun PhoneQuickPresetCard(
    presetId: String,
    name: String,
    tagline: String,
    displayPath: String,
    badgeText: String,
    badgeBg: Color,
    badgeTint: Color,
    iconType: PhonePresetIcon,
    isExpandable: Boolean,
    isExpanded: Boolean,
    isCompact: Boolean,
    onExpandedPresetChange: (String?) -> Unit,
    onCardBoundsChange: (Rect?) -> Unit,
    onClick: () -> Unit,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val shape = RoundedCornerShape(16.dp)

    val targetWidth = when {
        isExpanded -> 235.dp
        isCompact -> 170.dp
        else -> 140.dp
    }
    val targetHeight = when {
        isExpanded -> 160.dp
        isCompact -> 52.dp
        else -> 126.dp
    }

    val cardWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "phoneCardWidth",
    )
    val cardHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "phoneCardHeight",
    )

    val cardBgColor = if (GeekColors.isDark) Color(0xFF19202D) else Color(0xFFF2F4F7)
    val rippleColor = if (GeekColors.isDark) Color(0x38D1D5DB) else Color(0x286B7280)
    val presetInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(cardBgColor, shape)
            .clip(shape)
            .onGloballyPositioned { coords ->
                if (isExpanded) {
                    onCardBoundsChange(coords.boundsInRoot())
                }
            }
            .then(
                if (!isExpanded) {
                    Modifier.clickable(
                        interactionSource = presetInteraction,
                        indication = ripple(color = rippleColor),
                    ) {
                        if (isExpandable) {
                            onExpandedPresetChange(presetId)
                        } else {
                            coroutineScope.launch {
                                delay(120)
                                onClick()
                            }
                        }
                    }
                } else Modifier
            ),
    ) {
        // 1. Left icon with squircle container
        Box(
            modifier = Modifier
                .padding(start = 11.dp, top = 10.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (iconType) {
                        PhonePresetIcon.Pi -> Color(0xFFF09082).copy(alpha = 0.14f)
                        PhonePresetIcon.Terminal -> GeekColors.TerminalCyan.copy(alpha = 0.14f)
                        PhonePresetIcon.RemotePc -> GeekColors.BrandAccent.copy(alpha = 0.14f)
                        PhonePresetIcon.Shell -> GeekColors.CardHighlight
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (iconType) {
                PhonePresetIcon.Pi -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_preset_pi),
                        contentDescription = "Pi Agent",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp),
                    )
                }
                PhonePresetIcon.Terminal -> {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = GeekColors.TerminalCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
                PhonePresetIcon.RemotePc -> {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                PhonePresetIcon.Shell -> {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = GeekColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 2. Right badge or close button
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 11.dp, top = 10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(GeekColors.CardHighlight)
                    .clickable { onExpandedPresetChange(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "收起",
                    tint = GeekColors.TextMuted,
                    modifier = Modifier.size(15.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 11.dp, top = 14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = badgeTint,
                )
            }
        }

        // 3. Title glide animation
        val titleX by animateDpAsState(
            targetValue = if (isCompact || isExpanded) 51.dp else 11.dp,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "titleX",
        )
        val titleY by animateDpAsState(
            targetValue = if (isCompact || isExpanded) 17.dp else 56.dp,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "titleY",
        )

        val maxTitleWidth = when {
            isExpanded -> 135.dp
            isCompact -> (cardWidth - 51.dp - 56.dp).coerceAtLeast(60.dp)
            else -> 118.dp
        }

        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            color = GeekColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = titleX, y = titleY)
                .widthIn(max = maxTitleWidth),
        )

        // 4. Tagline & Path
        AnimatedVisibility(
            visible = !isCompact && !isExpanded,
            enter = fadeIn(animationSpec = tween(160, delayMillis = 40)),
            exit = fadeOut(animationSpec = tween(80)),
            modifier = Modifier
                .padding(start = 11.dp, top = 78.dp, end = 11.dp)
                .fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                    ),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = GeekColors.TextMuted,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = GeekColors.TextMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                            ),
                    )
                }
            }
        }

        // 5. Expandable Options Drawer
        if (expandedContent != null) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(220, delayMillis = 60)) +
                    expandVertically(animationSpec = tween(240, delayMillis = 40)),
                exit = fadeOut(animationSpec = tween(100)) +
                    shrinkVertically(animationSpec = tween(140)),
                modifier = Modifier
                    .padding(start = 11.dp, top = 48.dp, end = 11.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = expandedContent,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Session Cards

@Composable
private fun RunningPhoneSessionCard(
    session: PhoneSessionState,
    onOpen: () -> Unit,
    onKill: () -> Unit,
) {
    val isAi = session.kind == PhoneSessionKind.AI_AGENT
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .pressClickEffect(),
        shape = shape,
        color = GeekColors.CardSurface,
        border = null,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(
                color = if (isAi) GeekColors.BrandPurple else GeekColors.TerminalCyan,
                glowColor = if (isAi) GeekColors.BrandPurpleGlow else GeekColors.TerminalCyanGlow,
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GeekColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = session.workingDir.ifBlank { "默认工作目录" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GeekSmallButton(
                    label = "打开",
                    containerColor = GeekColors.BrandAccent,
                    contentColor = Color.White,
                    borderColor = GeekColors.BrandAccent,
                    onClick = onOpen,
                )
                GeekSmallButton(
                    label = "终止",
                    containerColor = GeekColors.CardElevated,
                    contentColor = GeekColors.RoseError,
                    borderColor = GeekColors.BorderSubtle,
                    onClick = onKill,
                )
            }
        }
    }
}

@Composable
private fun EndedPhoneSessionsGroupCard(
    sessions: List<PhoneSessionState>,
    onOpen: (PhoneSessionState) -> Unit,
    onForget: (PhoneSessionState) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .alpha(0.9f),
        shape = shape,
        color = GeekColors.CardSurface,
        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            sessions.forEachIndexed { index, session ->
                if (index > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .height(1.1.dp)
                            .background(GeekColors.TextMuted.copy(alpha = 0.3f)),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                EndedPhoneSessionRow(
                    session = session,
                    onOpen = { onOpen(session) },
                    onForget = { onForget(session) },
                )
            }
        }
    }
}

@Composable
private fun EndedPhoneSessionRow(
    session: PhoneSessionState,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    val isAi = session.kind == PhoneSessionKind.AI_AGENT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressClickEffect()
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GeekColors.CardElevated,
                ) {
                    Text(
                        text = if (isAi) "AI" else "Shell",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${session.workingDir.ifBlank { "默认工作目录" }} · ${relativeTime(session.lastActiveTime)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                color = GeekColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GeekSmallButton("查看", onClick = onOpen)
            GeekSmallButton("移除", onClick = onForget)
        }
    }
}

// ---------------------------------------------------------------- Fullscreen Terminal Screen

/**
 * Dedicated Fullscreen Phone Terminal Screen.
 * Mirrors PC TerminalScreen (SessionTopNavigationBar, TerminalView, ExtraKeysBar, IME padding).
 */
@Composable
private fun PhoneTerminalFullscreenScreen(
    manager: PhoneSessionManager,
    session: PhoneSessionState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = GeekColors.isDark
    var fontSizeSp by remember { mutableStateOf(12f) }

    val terminalView = session.terminalView ?: remember {
        TerminalView(context).apply {
            onOutput = { bytes -> session.session?.send(bytes) }
            onClipboardCopy = { text -> copyToClipboard(context, text) }
            onTap = { showKeyboard() }
            onResize = { cols, rows ->
                session.session?.updateTerminalSize(cols, rows)
            }
        }
    }
    val shellSession = session.session

    DisposableEffect(terminalView, shellSession) {
        val prevResize = terminalView.onResize
        terminalView.onResize = { cols, rows ->
            prevResize?.invoke(cols, rows)
            shellSession?.updateTerminalSize(cols, rows)
        }
        onDispose {
            terminalView.onResize = prevResize
        }
    }

    LaunchedEffect(isDark, fontSizeSp) {
        val px = terminalView.resources.displayMetrics.scaledDensity * fontSizeSp
        terminalView.theme = if (isDark) {
            TerminalTheme.dark(px, Typeface.MONOSPACE)
        } else {
            TerminalTheme.light(px, Typeface.MONOSPACE)
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(260)
        terminalView.requestFocus()
        terminalView.showKeyboard()
    }

    var scrolledBack by remember { mutableStateOf(false) }

    LaunchedEffect(terminalView) {
        terminalView.onScrollChanged = { offset, _ ->
            scrolledBack = offset > 0
        }
    }

    var showAgentConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SessionTopNavigationBar(
                title = session.title.ifBlank { "本机 Linux 终端" },
                sessionId = if (session.pid != null) "PID ${session.pid}" else "Shell",
                isGuiMode = false,
                isRpcSession = false,
                onBack = onBack,
                onOpenAgentConfig = { showAgentConfigDialog = true },
                onClear = { shellSession?.sendCommand("clear\n") },
                modeSubtitle = "本机终端 (PTY)",
            )
        },
        containerColor = GeekColors.DeepCanvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AndroidView(
                        factory = {
                            (terminalView.parent as? ViewGroup)?.removeView(terminalView)
                            terminalView.rootView?.scrollTo(0, 0)
                            terminalView.apply {
                                post {
                                    requestFocus()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Floating scroll-to-bottom action button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = scrolledBack,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 14.dp),
                    ) {
                        FloatingScrollToBottomButton(
                            onClick = { terminalView.scrollToBottom() },
                        )
                    }
                }

                ExtraKeysBar(terminalView)
            }
        }
    }

    if (showAgentConfigDialog) {
        AgentConfigDialog(
            targetEnvironmentName = "手机终端 (Android)",
            onDismiss = { showAgentConfigDialog = false },
            onInstall = { title, cmd ->
                showAgentConfigDialog = false
                Toast.makeText(context, "已向终端注入命令: $title", Toast.LENGTH_SHORT).show()
                shellSession?.sendCommand(cmd.trimEnd() + "\n")
            },
        )
    }
}

// ---------------------------------------------------------------- UI Subcomponents

@Composable
private fun PhoneSectionHeader(title: String, count: Int, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = GeekColors.TextMuted,
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isActive && count > 0) GeekColors.BrandAccentGlow else GeekColors.CardElevated,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (isActive && count > 0) GeekColors.BrandAccent else GeekColors.TextMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PhoneEmptyStateCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = GeekColors.TextMuted,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = GeekColors.TextMuted,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun PhoneSmallButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color = GeekColors.CardElevated,
    contentColor: Color = GeekColors.TextPrimary,
    borderColor: Color = GeekColors.BorderSubtle,
) {
    GeekSmallButton(
        label = label,
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
    )
}

@Composable
private fun PhonePulsingDot(color: Color, glowColor: Color) {
    val transition = rememberInfiniteTransition(label = "phonePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phonePulseAlpha",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        Box(
            Modifier
                .size(16.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(glowColor),
        )
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

private fun relativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60} 分钟前"
        diff < 86400 -> "${diff / 3600} 小时前"
        else -> "${diff / 86400} 天前"
    }
}

private fun copyToClipboard(context: Context, text: String, showToast: Boolean = false) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("terminal", text)
    clipboard.setPrimaryClip(clip)
    if (showToast) {
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount > 0) {
        val text = clip.getItemAt(0).coerceToText(context)
        return text?.toString() ?: ""
    }
    return ""
}

// ---------------------------------------------------------------- Linux Environment Dialog

@Composable
private fun LinuxDetailDialog(
    envMgr: LinuxEnvironmentManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toolchain by envMgr.toolchain.collectAsState()
    val status by envMgr.status.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val isInstalling = status == LinuxEnvironmentStatus.INITIALIZING

    LaunchedEffect(Unit) {
        envMgr.detectEnvironment()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(1.dp, GeekColors.BorderHighlight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GeekColors.TerminalCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GeekColors.TerminalCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = GeekColors.TerminalCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "终端与 Linux 环境诊断",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "系统架构 · 运行环境 · 工具状态诊断",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = GeekColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Close Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = GeekColors.TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Virtualized LazyColumn with high-performance scrolling physics
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Item 1: Diagnostic Items Card
                    item(key = "diagnostic_card") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GeekColors.CardElevated,
                            border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DetailItemRow("运行模式", toolchain.environmentType)
                                DetailItemRow("GNU Bash", toolchain.bashVersion ?: if (toolchain.hasBash) "已就绪" else "精简 sh")
                                DetailItemRow("Alpine 发行版", toolchain.alpineVersion ?: if (toolchain.hasAlpine) "已部署" else "未安装")
                                DetailItemRow("PRoot 沙箱", if (toolchain.hasProot) "已就绪 (fake-root)" else "未安装")
                                DetailItemRow("BusyBox 工具箱", toolchain.busyBoxVersion ?: if (toolchain.hasBusyBox) "100+ 工具就绪" else "精简")
                                DetailItemRow("Node.js", toolchain.nodeVersion ?: "未检测到")
                                DetailItemRow("npm", toolchain.npmVersion ?: "未检测到")
                                DetailItemRow("Pi Agent", toolchain.piVersion ?: if (toolchain.hasPiAgent) "已就绪" else "待安装 (npm i -g @earendil-works/pi-coding-agent)")
                                DetailItemRow("Git", toolchain.gitVersion ?: "未检测到")
                                DetailItemRow("Python", toolchain.pythonVersion ?: if (toolchain.hasPython) "已就绪" else "未检测到")
                                DetailItemRow("用户主目录", envMgr.homeDir.absolutePath) {
                                    copyToClipboard(context, envMgr.homeDir.absolutePath, showToast = true)
                                }
                                DetailItemRow("沙箱根路径", envMgr.rootfsDir.absolutePath) {
                                    copyToClipboard(context, envMgr.rootfsDir.absolutePath, showToast = true)
                                }
                            }
                        }
                    }

                    // Item 2: Alpine Linux Deployment Section
                    item(key = "alpine_deployment") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GeekColors.CardElevated,
                            border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Linux 完整子系统 (Alpine Linux)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GeekColors.TerminalCyan,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (toolchain.hasAlpine) GeekColors.NeonGreen.copy(alpha = 0.15f) else GeekColors.AmberWarn.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = if (toolchain.hasAlpine) "已就绪" else "待部署",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = if (toolchain.hasAlpine) GeekColors.NeonGreen else GeekColors.AmberWarn,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = if (toolchain.hasAlpine) {
                                        "Alpine Linux 根文件系统已就绪。支持 apk 真实包管理器与 fake-root 环境，终端拉起可享真实 Linux 子系统体验。"
                                    } else {
                                        "一键下载部署官方 Alpine 极简系统 (~3.5MB)，即可使用 apk add 安装 bash、python、git、curl 等 Linux 软件！"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = GeekColors.TextMuted,
                                    lineHeight = 15.sp,
                                )

                                if (isInstalling) {
                                    LinuxDetailInstallProgressIndicator(envMgr)
                                }

                                if (toolchain.hasAlpine && !toolchain.hasProot) {
                                    var isDownloadingProot by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = {
                                            isDownloadingProot = true
                                            envMgr.ensureProotAvailable(scope) { ok ->
                                                isDownloadingProot = false
                                                val msg = if (ok) "PRoot 沙箱引擎已就绪！重开终端即可进入 Linux。" else "下载 PRoot 失败，请检查网络。"
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = !isDownloadingProot && !isInstalling,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = Color.White,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isDownloadingProot) "正在下载 PRoot 沙箱引擎..." else "一键补齐 PRoot 沙箱引擎 (350KB)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                        )
                                    }
                                }

                                if (!isInstalling) {
                                    Button(
                                        onClick = {
                                            envMgr.deployAlpineRootfs(scope) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (toolchain.hasAlpine) GeekColors.CardSurface else GeekColors.BrandAccent,
                                        ),
                                        border = if (toolchain.hasAlpine) BorderStroke(1.dp, GeekColors.BorderSubtle) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (toolchain.hasAlpine) Icons.Default.CleaningServices else Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (toolchain.hasAlpine) GeekColors.TextPrimary else Color.White,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (toolchain.hasAlpine) "重新部署 / 重置 Alpine Linux" else "一键部署 Alpine Linux (3.5MB)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (toolchain.hasAlpine) GeekColors.TextPrimary else Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                envMgr.detectEnvironment()
                                isRefreshing = false
                                Toast.makeText(context, "环境状态已刷新", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isRefreshing && !isInstalling,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GeekColors.TerminalCyan),
                        modifier = Modifier.height(36.dp),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.5.dp,
                                color = GeekColors.TerminalCyan,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("刷新状态", fontSize = 12.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("关闭", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinuxDetailInstallProgressIndicator(envMgr: LinuxEnvironmentManager) {
    val installProgress by envMgr.installProgress.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { installProgress.first.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = GeekColors.TerminalCyan,
            trackColor = GeekColors.CardElevated,
        )
        Text(
            text = installProgress.second,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = GeekColors.TerminalCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onCopy != null) Modifier.clickable(onClick = onCopy) else Modifier)
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GeekColors.TextMuted,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = GeekColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2000,
                ),
        )
    }
}
