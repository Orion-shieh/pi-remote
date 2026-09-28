package com.piremote.app.ui.gui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import com.piremote.app.ui.PiSessionPickerDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piremote.app.R
import com.piremote.app.data.SessionRepository
import com.piremote.app.ui.GeekColors
import com.piremote.app.ui.PulsingDot
import com.piremote.app.ui.pressClickEffect
import com.piremote.terminal.view.TerminalView
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import com.piremote.app.ui.LocalGuiFontScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Graphical AI agent workspace.
 *
 * Two data sources feed the same card components:
 * - RPC sessions (kind = "rpc"): pi's structured event stream via
 *   [SessionRepository.agentMessageState].
 * - Plain terminal sessions (kind = "normal"): screen-scraped via
 *   [TerminalChatParser], kept for older agents without the RPC bridge.
 */
@Composable
fun GuiChatContent(
    terminalView: TerminalView,
    repository: SessionRepository,
    sessionId: String,
    isRpcSession: Boolean,
    onSendText: (String) -> Unit,
    onSwitchSession: (() -> Unit)? = null,
    onOutlineChanged: (List<GuiChatOutlineItem>) -> Unit = {},
    jumpTargetId: String? = null,
    onJumpConsumed: () -> Unit = {},
    onShowOutline: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalGuiFontScale.current
    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity, fontScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale,
        )
    }

    val context = LocalContext.current
    val safeUriHandler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                try {
                    val parsed = Uri.parse(uri)
                    val finalUri = if (parsed.scheme.isNullOrBlank()) {
                        Uri.parse("http://$uri")
                    } else {
                        parsed
                    }
                    val intent = Intent(Intent.ACTION_VIEW, finalUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "无法打开链接: $uri", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalUriHandler provides safeUriHandler,
    ) {
        if (isRpcSession) {
            RpcGuiChat(
                repository = repository,
                sessionId = sessionId,
                onSwitchSession = onSwitchSession,
                onOutlineChanged = onOutlineChanged,
                onShowOutline = onShowOutline,
                jumpTargetId = jumpTargetId,
                onJumpConsumed = onJumpConsumed,
                modifier = modifier,
            )
        } else {
            TranscriptGuiChat(terminalView = terminalView, onSendText = onSendText, modifier = modifier)
        }
    }
}

// ------------------------------------------------------------- Slash commands

private fun isControlSlashCommand(rawText: String): Boolean {
    val trimmed = rawText.trim()
    if (!trimmed.startsWith("/")) return false
    val name = trimmed.removePrefix("/").substringBefore(" ").lowercase()
    return name in setOf(
        "model",
        "login",
        "thinking",
        "new",
        "resume",
        "status",
        "copy",
        "tree",
        "fork",
        "quit",
        "reload",
        "reload_config",
        "compact",
        "clear",
    )
}

// ------------------------------------------------------------- RPC session

@Composable
private fun RpcGuiChat(
    repository: SessionRepository,
    sessionId: String,
    onSwitchSession: (() -> Unit)?,
    onOutlineChanged: (List<GuiChatOutlineItem>) -> Unit,
    onShowOutline: (() -> Unit)? = null,
    jumpTargetId: String?,
    onJumpConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(sessionId) { repository.getOrCreateGuiAgentState(sessionId) }
    var items by remember(sessionId) { mutableStateOf(state.items) }
    val isNewlyCreated = remember(sessionId) { repository.isNewlyCreatedSession(sessionId) }
    val chatRows = remember(items) { buildChatRows(items) }

    // Pin directly to the bottom row on Frame 1 so conversation opens immediately at the bottom!
    val initialBottomIndex = remember(sessionId) {
        if (chatRows.isNotEmpty()) chatRows.size - 1 else 0
    }
    val listState = remember(sessionId) {
        LazyListState(firstVisibleItemIndex = initialBottomIndex)
    }
    var isBottomSynced by remember(sessionId) { mutableStateOf(chatRows.isNotEmpty()) }

    var agentRunning by remember(sessionId) { mutableStateOf(false) }
    var statusLine by remember(sessionId) { mutableStateOf<String?>(null) }
    var modelLabel by remember(sessionId) { mutableStateOf<String?>(state.currentModelLabel) }
    var modelKey by remember(sessionId) { mutableStateOf<String?>(state.currentModelKey) }
    var thinking by remember(sessionId) { mutableStateOf<String?>(null) }
    var models by remember(sessionId) { mutableStateOf<List<PiModelOption>>(state.availableModels) }
    val piAgentModels by repository.piAgentModels.collectAsState()
    val userAddedModels by repository.userAddedModels.collectAsState()
    val deletedModelKeys by repository.deletedModelKeys.collectAsState()

    val effectiveModels = remember(models, piAgentModels, userAddedModels, deletedModelKeys) {
        val map = LinkedHashMap<String, PiModelOption>()
        // 1. 用户手动新增的模型
        for (m in userAddedModels) {
            val k = m.key.lowercase()
            val idLower = m.id.lowercase()
            val fullKey = "${m.provider}/${m.id}".lowercase()
            if (!deletedModelKeys.contains(k) && !deletedModelKeys.contains(idLower) && !deletedModelKeys.contains(fullKey)) {
                map[k] = m
            }
        }
        // 2. PC 磁盘直读配置
        for (m in piAgentModels) {
            val k = m.key.lowercase()
            val idLower = m.id.lowercase()
            val fullKey = "${m.provider}/${m.id}".lowercase()
            if (!deletedModelKeys.contains(k) && !deletedModelKeys.contains(idLower) && !deletedModelKeys.contains(fullKey)) {
                if (!map.containsKey(k)) map[k] = m
            }
        }
        // 3. 当前会话 RPC 可用模型
        for (m in models) {
            val k = m.key.lowercase()
            val idLower = m.id.lowercase()
            val fullKey = "${m.provider}/${m.id}".lowercase()
            if (!deletedModelKeys.contains(k) && !deletedModelKeys.contains(idLower) && !deletedModelKeys.contains(fullKey)) {
                if (!map.containsKey(k)) map[k] = m
            }
        }
        map.values.toList()
    }
    var levels by remember(sessionId) { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember(sessionId) { mutableStateOf(false) }
    var showAddLoginModelDialog by remember(sessionId) { mutableStateOf(false) }
    var showSessionPicker by remember(sessionId) { mutableStateOf(false) }
    val piSessions by repository.piSessions.collectAsState()

    LaunchedEffect(showSessionPicker) {
        if (showSessionPicker) {
            repository.requestPiSessionList(sessionId, null)
        }
    }
    var pendingAutoLoginKey by remember(sessionId) { mutableStateOf<String?>(null) }
    var pendingAutoLoginProvider by remember(sessionId) { mutableStateOf<String?>(null) }
    var slashCommands by remember(sessionId) { mutableStateOf<List<PiSlashCommand>>(emptyList()) }
    var showQuitConfirm by remember(sessionId) { mutableStateOf(false) }
    var forkConfirmTarget by remember(sessionId) { mutableStateOf<UserMessage?>(null) }
    var pendingPostForkPrompt by remember(sessionId) { mutableStateOf<String?>(null) }
    var pendingForkOriginSessionId by remember(sessionId) { mutableStateOf<String?>(null) }
    var isInitialized by remember(sessionId) { mutableStateOf(!isNewlyCreated || state.items.isNotEmpty()) }
    var initElapsedSeconds by remember(sessionId) { mutableStateOf(0) }

    LaunchedEffect(sessionId) {
        if (state.currentModelProvider != null) {
            repository.setPiModelProvider(state.currentModelProvider)
        }
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
        repository.requestPiAgentModels()
    }

    LaunchedEffect(sessionId, isInitialized) {
        if (!isInitialized) {
            initElapsedSeconds = 0
            while (!isInitialized) {
                delay(1000)
                initElapsedSeconds += 1
            }
        }
    }
    // Set on every attach snapshot: the conversation opens pinned to the
    // bottom regardless of where the list was scrolled before.
    var pendingBottomJump by remember(sessionId) { mutableStateOf(false) }
    var lastSentText by remember(sessionId) { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    val density = LocalDensity.current
    val topThresholdPx = with(density) { 36.dp.toPx() }
    var lazyListCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Collapse-reposition only scrolls when the item's top edge (its first
    // row) is outside the viewport; if the header is already on screen the
    // list stays where it is.
    fun isRowTopVisible(index: Int): Boolean {
        val layoutInfo = listState.layoutInfo
        val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        return info.offset in viewportStart until viewportEnd
    }

    fun scrollToRowIfHidden(index: Int) {
        scope.launch {
            delay(50)
            if (!isRowTopVisible(index)) {
                val listCoords = lazyListCoordinates
                val targetY = if (listCoords != null && listCoords.isAttached) listCoords.size.height * 0.32f else 0f
                listState.animateScrollToItem(index)
                if (targetY > 0f) {
                    listState.animateScrollBy(-targetY)
                }
            } else {
                delay(150)
                if (!isRowTopVisible(index)) {
                    val listCoords = lazyListCoordinates
                    val targetY = if (listCoords != null && listCoords.isAttached) listCoords.size.height * 0.32f else 0f
                    listState.animateScrollToItem(index)
                    if (targetY > 0f) {
                        listState.animateScrollBy(-targetY)
                    }
                }
            }
        }
    }

    fun scrollToCoordinatesIfHidden(targetCoordinates: LayoutCoordinates?) {
        val target = targetCoordinates ?: return
        scope.launch {
            delay(50)
            val listCoords = lazyListCoordinates ?: return@launch
            if (!target.isAttached || !listCoords.isAttached) return@launch
            val cardTop = listCoords.localPositionOf(target, Offset.Zero).y
            val listHeight = listCoords.size.height.toFloat()
            val bottomThresholdPx = listHeight - topThresholdPx
            // Target position: slightly above the vertical center (approx 32% from the top)
            val targetY = listHeight * 0.32f
            if (cardTop < topThresholdPx || cardTop >= bottomThresholdPx) {
                listState.animateScrollBy(cardTop - targetY)
            } else {
                delay(150)
                if (!target.isAttached || !listCoords.isAttached) return@launch
                val cardTopAfter = listCoords.localPositionOf(target, Offset.Zero).y
                if (cardTopAfter < topThresholdPx || cardTopAfter >= bottomThresholdPx) {
                    listState.animateScrollBy(cardTopAfter - targetY)
                }
            }
        }
    }

    // Per-turn fold state: execution details (thinking/tools) collapse into "执行详情",
    // collapsed by default once finished, and auto-expanded while pi is live in it.
    val expandedTurns = remember(sessionId) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(sessionId) {
        repository.guiEvents.collect { message ->
            if (message.sid != sessionId) return@collect
            val event = AgentEventJson.parse(message.event)
            state.onEvent(event)
            items = state.items
            agentRunning = state.agentRunning
            statusLine = state.statusLine
            modelLabel = state.currentModelLabel
            modelKey = state.currentModelKey
            thinking = state.currentThinking
            models = state.availableModels
            levels = state.thinkingLevels
            slashCommands = state.slashCommands
            isInitialized = true
            if (state.currentModelProvider != null) {
                repository.setPiModelProvider(state.currentModelProvider)
            }
            // Full outline, grouped by turn: user inputs anchor their turn's
            // thinking, tools and reply, which are indented in the dialog.
            onOutlineChanged(items.map(::outlineItem))
            when (event) {
                is AgentEvent.Snapshot -> {
                    // Top-bar conversation title: prefer the agent-computed
                    // name (from the FULL session file, so it matches the home
                    // list even when the snapshot is tail-capped), then pi's
                    // session name, then the snapshot's first user message.
                    val derivedName = event.name
                        ?: event.state?.sessionName
                        ?: event.entries
                            .firstOrNull { it.message?.role == "user" && it.message.text.isNotBlank() }
                            ?.message?.text
                            ?.trim()
                            ?.let { if (it.length > 24) it.take(24) + "…" else it }
                    repository.setPiSessionName(derivedName)
                    val currentSid = event.state?.sessionId ?: sessionId
                    val originParent = pendingForkOriginSessionId
                    if (!originParent.isNullOrBlank() && originParent != currentSid) {
                        repository.lineageStore.recordFork(childKey = currentSid, parentKey = originParent)
                        pendingForkOriginSessionId = null
                        repository.requestPiSessionList(sessionId, null)
                    }
                    // A fresh snapshot means a fresh view of the conversation:
                    // land at the bottom once its items are rendered.
                    pendingBottomJump = true
                    repository.markSessionStarted(sessionId)
                    // Preload commands, models, thinking levels and state on attach
                    repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_commands"))
                    repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
                    repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_thinking_levels"))
                    repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_state"))
                    repository.requestPiAgentModels()
                }

                is AgentEvent.CommandResponse -> {
                    if (event.command == "get_tree") {
                        if (event.success && !event.treeText.isNullOrEmpty()) {
                            clipboard?.setPrimaryClip(ClipData.newPlainText("tree", event.treeText))
                            Toast.makeText(context, "已获取会话分支树并复制到剪贴板", Toast.LENGTH_SHORT).show()
                        } else if (!event.success) {
                            Toast.makeText(context, "查询会话树失败：${event.error ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (event.success && event.command in REFRESH_AFTER_SET_COMMANDS) {
                        // set/thinking responses may not echo the full state; refresh once.
                        repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_state"))
                        if (event.command == "set_model" || event.command == "cycle_model" || event.command == "reload" || event.command == "reload_config") {
                            repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
                            repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_thinking_levels"))
                        }
                    } else if (!event.success && (event.command == "set_model" || event.command == "cycle_model")) {
                        // 切换失败时，主动重新同步会话真实状态，防止 UI 显示错误模型
                        repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_state"))
                        val rawErr = event.error.orEmpty()
                        val tip = if (rawErr.contains("Model not found", ignoreCase = true)) {
                            "切换模型失败：当前运行中的 Pi 会话未载入此模型。\n(提示：若为刚添加的模型，请返回会话列表【新建会话】以生效)"
                        } else {
                            "切换模型失败：${event.error ?: "未知错误"}"
                        }
                        Toast.makeText(context, tip, Toast.LENGTH_LONG).show()
                    }
                    // A successful switch or fork lands on a different pi conversation/branch:
                    // re-attach so the agent sends a fresh snapshot of it.
                    if (event.success && (event.command == "switch_session" || event.command == "fork") && !event.cancelled) {
                        state.resetForNewSession()
                        items = state.items
                        isInitialized = false
                        isBottomSynced = false
                        repository.openSession(sessionId)
                        if (event.command == "fork") {
                            Toast.makeText(context, "分叉成功，已进入新分支", Toast.LENGTH_SHORT).show()
                            val originParent = pendingForkOriginSessionId
                            if (!originParent.isNullOrBlank()) {
                                repository.lineageStore.recordFork(childKey = sessionId, parentKey = originParent)
                            }
                            repository.requestPiSessionList(sessionId, null)
                            val postPrompt = pendingPostForkPrompt
                            pendingPostForkPrompt = null
                            if (!postPrompt.isNullOrBlank()) {
                                scope.launch {
                                    delay(500)
                                    val trimmed = postPrompt.trim()
                                    if (trimmed.isNotEmpty()) {
                                        state.addUserMessage(trimmed, model = modelLabel ?: modelKey, provider = state.currentModelProvider, sendType = "prompt")
                                        items = state.items
                                        repository.sendAgentCommand(sessionId, JSONObject().put("type", "prompt").put("message", trimmed))
                                    }
                                }
                            }
                        }
                    }
                    if (!event.success && event.command == "fork") {
                        Toast.makeText(context, "分叉失败：${event.error ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                    }
                    if (event.command == "get_last_assistant_text" && !event.lastAssistantText.isNullOrEmpty()) {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("pi", event.lastAssistantText))
                    }
                    if (!event.success && event.command in SEND_COMMANDS) {
                        // pi rejected the prompt/steer (e.g. "not streaming");
                        // give the text back instead of silently dropping it.
                        lastSentText?.let { inputText = it }
                        lastSentText = null
                    }
                }

                is AgentEvent.ExtensionUiRequest -> {
                    val autoKey = pendingAutoLoginKey
                    val autoProvider = pendingAutoLoginProvider
                    if (!autoKey.isNullOrBlank() && event.method == "input") {
                        pendingAutoLoginKey = null
                        repository.sendAgentCommand(
                            sessionId,
                            JSONObject()
                                .put("type", "extension_ui_response")
                                .put("id", event.requestId)
                                .put("value", autoKey)
                        )
                        Toast.makeText(context, "已自动提交 API Key 凭据", Toast.LENGTH_SHORT).show()
                    } else if (!autoProvider.isNullOrBlank() && (event.method == "select" || event.method == "confirm")) {
                        val matchedIndex = event.options.indexOfFirst { it.contains(autoProvider, ignoreCase = true) }
                        if (matchedIndex >= 0) {
                            pendingAutoLoginProvider = null
                            val chosenKey = (matchedIndex + 1).toString()
                            val chosenLabel = event.options[matchedIndex]
                            val sendVal = if (chosenKey.matches(Regex("""^\d+$"""))) chosenKey else chosenLabel
                            repository.sendAgentCommand(
                                sessionId,
                                JSONObject()
                                    .put("type", "extension_ui_response")
                                    .put("id", event.requestId)
                                    .put("value", sendVal)
                                    .put("key", chosenKey)
                            )
                        }
                    }
                }

                AgentEvent.AgentSettled -> {
                    // Turn finished; refresh available models in case a provider was added/logged in
                    repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
                    repository.requestPiAgentModels()
                }

                else -> Unit
            }
        }
    }

    // Refresh model catalog & thinking levels when model picker opens or model changes
    LaunchedEffect(showModelPicker) {
        if (showModelPicker) {
            repository.requestPiAgentModels()
            repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
            repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_thinking_levels"))
        }
    }

    val authUpdateResult by repository.authUpdateResult.collectAsState()
    LaunchedEffect(authUpdateResult) {
        authUpdateResult?.let { res ->
            if (res.success) {
                Toast.makeText(context, "服务商 ${res.provider} 凭据已写入电脑 auth.json，模型列表已更新", Toast.LENGTH_SHORT).show()
                repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
                repository.requestPiAgentModels()
            } else {
                Toast.makeText(context, "凭据更新失败: ${res.error ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
            repository.clearAuthUpdateResult()
        }
    }

    val modelDeletedResult by repository.modelDeletedResult.collectAsState()
    LaunchedEffect(modelDeletedResult) {
        modelDeletedResult?.let { res ->
            if (res.success) {
                Toast.makeText(context, "模型 ${res.modelId} 已从电脑端删除", Toast.LENGTH_SHORT).show()
                repository.requestPiAgentModels()
                repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
            } else {
                Toast.makeText(context, "删除模型失败: ${res.error ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
            repository.clearModelDeletedResult()
        }
    }

    val piSessionDeletedResult by repository.piSessionDeletedResult.collectAsState()
    LaunchedEffect(piSessionDeletedResult) {
        piSessionDeletedResult?.let { res ->
            if (res.success) {
                Toast.makeText(context, "已从电脑端彻底删除历史会话", Toast.LENGTH_SHORT).show()
                repository.requestPiSessionList(sessionId, null)
            } else {
                Toast.makeText(context, "删除会话失败: ${res.error ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
            repository.clearPiSessionDeletedResult()
        }
    }


    // Live-follow only while the reader is near the bottom, like pi-web.
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 2
        }
    }
    // A fresh snapshot (open / switch / change path) lands at the bottom once.
    LaunchedEffect(chatRows.size, pendingBottomJump) {
        if (chatRows.isNotEmpty()) {
            if (!isBottomSynced || pendingBottomJump) {
                listState.scrollToItem(chatRows.size - 1)
                pendingBottomJump = false
                isBottomSynced = true
            }
        }
    }
    LaunchedEffect(chatRows.size, statusLine) {
        if (chatRows.isNotEmpty() && nearBottom) {
            listState.scrollToItem(chatRows.size - 1)
        }
    }

    // Quick navigation: the top-bar ☰ dialog picks an item id; jump to it once.
    // A target inside a collapsed turn first expands that turn, then scrolls.
    LaunchedEffect(chatRows, jumpTargetId) {
        val target = jumpTargetId ?: return@LaunchedEffect
        val index = chatRows.indexOfFirst {
            when (it) {
                is ChatRow.Message -> it.item.id == target
                is ChatRow.SystemGroup -> it.items.any { m -> m.id == target }
                is ChatRow.TurnSummary -> false
            }
        }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            onJumpConsumed()
        } else {
            val itemIndex = items.indexOfFirst { it.id == target }
            if (itemIndex > 0) {
                for (i in itemIndex downTo 0) {
                    val anchor = items[i]
                    if (anchor is UserMessage) {
                        chatRows.filterIsInstance<ChatRow.TurnSummary>()
                            .filter { it.turnId == anchor.id }
                            .forEach { summary ->
                                expandedTurns["${summary.turnId}-${summary.turnIndex}"] = true
                            }
                        break
                    }
                }
            } else {
                onJumpConsumed()
            }
        }
    }

    fun doNewSession() {
        expandedTurns.clear()
        state.resetForNewSession()
        items = state.items
        isInitialized = false
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "new_session"))
    }

    fun doReload() {
        if (agentRunning) {
            Toast.makeText(context, "智能体运行中，请稍候再重新加载", Toast.LENGTH_SHORT).show()
            return
        }
        // 1. 针对纯 RPC 结构化通道，透传 reload / reload_config 控制指令
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "reload"))
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "reload_config"))

        // 3. 促使电脑端服务重新扫描读取 ~/.pi/agent/models.json 与 auth.json
        repository.requestPiAgentModels()

        // 4. 同步请求更新可用模型与会话状态
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_thinking_levels"))
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_state"))

        Toast.makeText(context, "已触发重新加载 (/reload)", Toast.LENGTH_SHORT).show()
    }

    val candidateLevels = remember(levels) {
        val nonOff = levels.filter { !it.equals("off", ignoreCase = true) && !it.equals("none", ignoreCase = true) }
        if (nonOff.isNotEmpty()) {
            (listOf("off") + nonOff).distinct()
        } else if (levels.isNotEmpty()) {
            listOf("off")
        } else {
            listOf("off", "low", "medium", "high")
        }
    }

    fun cycleThinkingLevel() {
        if (candidateLevels.size <= 1 && candidateLevels.all { it.equals("off", ignoreCase = true) }) {
            Toast.makeText(context, "当前模型不支持调节思考", Toast.LENGTH_SHORT).show()
            return
        }
        if (agentRunning) {
            Toast.makeText(context, "智能体运行中，请稍候再切换", Toast.LENGTH_SHORT).show()
            return
        }

        val cur = thinking?.trim()?.lowercase()
        val currentIndex = candidateLevels.indexOfFirst { it.equals(cur, ignoreCase = true) }
        val nextLevel = if (currentIndex < 0) {
            // If currently off or unset, activate the first active level (e.g. "low")
            candidateLevels.firstOrNull { !it.equals("off", ignoreCase = true) && !it.equals("none", ignoreCase = true) }
                ?: candidateLevels[0]
        } else if (currentIndex == candidateLevels.lastIndex) {
            candidateLevels[0]
        } else {
            candidateLevels[currentIndex + 1]
        }

        thinking = nextLevel
        state.updateThinking(nextLevel)

        repository.sendAgentCommand(
            sessionId,
            JSONObject()
                .put("type", "set_thinking_level")
                .put("level", nextLevel)
                .put("thinkingLevel", nextLevel),
        )
    }

    fun callPiForkWithSummary(userMsg: UserMessage, mode: ForkSummaryMode, customPrompt: String?) {
        if (agentRunning) {
            Toast.makeText(context, "智能体运行中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }
        pendingForkOriginSessionId = sessionId
        val payload = JSONObject().put("type", "fork")
        val eid = userMsg.entryId
        if (!eid.isNullOrBlank()) {
            payload.put("entryId", eid)
        }
        when (mode) {
            ForkSummaryMode.NO_SUMMARY -> {
                pendingPostForkPrompt = null
            }
            ForkSummaryMode.SUMMARIZE -> {
                payload.put("summary", true)
                pendingPostForkPrompt = "请对我们刚才离开的分支中所做的探索、尝试、关键发现与代码修改进行简明总结，提炼核心结论与经验教训，作为我们在当前分支继续展开工作的背景上下文。"
            }
            ForkSummaryMode.CUSTOM_PROMPT -> {
                payload.put("summary", true)
                val userRequirement = customPrompt?.trim()?.ifBlank { null }
                    ?: "提炼刚才分支的核心探索与关键结论"
                payload.put("customPrompt", userRequirement)
                pendingPostForkPrompt = "关于我们刚才离开的分支：$userRequirement\n请按此要求提炼该分支的关键经验与结论，作为我们在当前分支继续展开工作的背景上下文。"
            }
        }
        repository.sendAgentCommand(sessionId, payload)
        Toast.makeText(context, "正在从该节点分叉会话...", Toast.LENGTH_SHORT).show()
    }

    fun triggerPiLogin(
        provider: String,
        apiKey: String?,
        baseUrl: String? = null,
        models: List<String> = emptyList(),
    ) {
        val trimmedProvider = provider.trim()
        val trimmedKey = apiKey?.trim()

        if (!trimmedKey.isNullOrBlank() || trimmedProvider == "ollama" || models.isNotEmpty()) {
            // 立即在本地注入所有新勾选或输入的模型，立竿见影！
            val targetModels = if (models.isNotEmpty()) models else listOf(trimmedProvider)
            var lastOption: PiModelOption? = null
            for (m in targetModels) {
                val opt = PiModelOption(provider = trimmedProvider, id = m, name = null)
                repository.addUserModel(opt)
                state.addModel(opt)
                lastOption = opt
            }

            // 若当前运行中的会话已载入该模型，才尝试自动切换；否则不盲目触发 set_model 导致报错
            val isKnownInCurrentSession = lastOption != null && state.availableModels.any {
                it.provider.equals(lastOption.provider, ignoreCase = true) && it.id.equals(lastOption.id, ignoreCase = true)
            }
            if (isKnownInCurrentSession && lastOption != null) {
                state.updateModel(lastOption)
                modelKey = state.currentModelKey
                modelLabel = state.currentModelLabel
                repository.setPiModelProvider(lastOption.provider)
                repository.sendAgentCommand(
                    sessionId,
                    JSONObject()
                        .put("type", "set_model")
                        .put("provider", lastOption.provider)
                        .put("modelId", lastOption.id),
                )
            }

            // 通过控制信令直接向电脑端 auth.json 写入配置，并更新 models.json
            repository.updatePiAuth(
                provider = trimmedProvider,
                apiKey = trimmedKey ?: "",
                baseUrl = baseUrl?.trim()?.takeIf { it.isNotBlank() },
                models = models,
            )
            val modelCountText = if (models.isNotEmpty()) " (${models.size} 个模型)" else ""
            val tipMessage = if (isKnownInCurrentSession) {
                "已成功添加 $trimmedProvider$modelCountText，并同步至电脑"
            } else {
                "已成功添加 $trimmedProvider$modelCountText 并同步至电脑！\n由于 Pi Agent 在启动时读取配置，新模型将在【新建会话】后生效。"
            }
            Toast.makeText(context, tipMessage, Toast.LENGTH_LONG).show()
            // 主动向电脑端与 RPC 会话发起刷新可用模型命令
            repository.requestPiAgentModels()
            repository.sendAgentCommand(
                sessionId,
                JSONObject().put("type", "get_available_models")
            )
        } else {
            // 向导交互登录 / OAuth（必须在交互式 PTY 终端下执行）
            val cmd = if (trimmedProvider.isNotBlank()) "/login $trimmedProvider" else "/login"
            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(
                android.content.ClipData.newPlainText("Pi Command", cmd)
            )
            Toast.makeText(
                context,
                "交互向导/OAuth 需在终端中执行。已复制命令「$cmd」，请切换至顶栏「终端视图」运行。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val doSendPrompt: (String) -> Unit = { text ->
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            val isBtw = trimmed.startsWith("/btw", ignoreCase = true) || trimmed.startsWith("/side", ignoreCase = true)
            val question = if (isBtw) {
                trimmed.replaceFirst(Regex("""^/(btw|side)(:\w+)?\s*""", RegexOption.IGNORE_CASE), "").trim()
            } else {
                trimmed
            }

            if (isBtw && question.isEmpty()) {
                Toast.makeText(context, "请输入旁支提问内容 (例如: /btw 你的问题)", Toast.LENGTH_SHORT).show()
                inputText = "/btw "
            } else {
                inputText = ""
                lastSentText = trimmed
                state.addUserMessage(
                    text = trimmed,
                    model = modelLabel ?: modelKey,
                    provider = state.currentModelProvider,
                    sendType = if (isBtw) "btw" else "prompt",
                )
                items = state.items

                val promptToSend = if (isBtw) {
                    "【旁支提问/BTW】$question\n(说明：这是旁支提问，请直接简要回答该问题，无需调用工具修改项目代码。)"
                } else {
                    trimmed
                }
                repository.sendAgentCommand(sessionId, JSONObject().put("type", "prompt").put("message", promptToSend))
            }
        }
    }

    fun handleSlashCommand(rawText: String) {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("/")) return

        val withoutSlash = trimmed.removePrefix("/")
        val name = withoutSlash.substringBefore(" ").lowercase()
        val args = withoutSlash.substringAfter(" ", "").trim()

        when (name) {
            "btw", "side" -> {
                if (args.isEmpty()) {
                    inputText = "/$name "
                    Toast.makeText(context, "请输入旁支提问内容 (例如: /btw 你的问题)", Toast.LENGTH_SHORT).show()
                    return
                }
                doSendPrompt(rawText)
                return
            }
        }

        inputText = ""

        // 根据不同命令处理对应的客户端 UI 弹窗或结构化 RPC 指令
        when (name) {
            "model" -> showModelPicker = true
            "login" -> showAddLoginModelDialog = true
            "thinking" -> cycleThinkingLevel()
            "new" -> doNewSession()
            "resume" -> onSwitchSession?.invoke()
            "status" -> repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_session_stats"))
            "copy" -> repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_last_assistant_text"))
            "tree" -> {
                if (onShowOutline != null) {
                    onShowOutline.invoke()
                } else {
                    repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_tree"))
                    Toast.makeText(context, "已发送 /tree 命令", Toast.LENGTH_SHORT).show()
                }
            }
            "fork" -> repository.sendAgentCommand(sessionId, JSONObject().put("type", "fork"))
            "quit" -> showQuitConfirm = true
            "reload", "reload_config" -> doReload()
            "compact" -> {
                val payload = JSONObject().put("type", "compact")
                if (args.isNotEmpty()) payload.put("customInstructions", args)
                repository.sendAgentCommand(sessionId, payload)
                Toast.makeText(context, "已发送压缩指令 (/compact)", Toast.LENGTH_SHORT).show()
            }
            "clear" -> {
                doNewSession()
                Toast.makeText(context, "已重置并清屏会话", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // 针对扩展提问类指令（如 /diff, /help, /skill 等），转由 doSendPrompt 作为正常提问发送给模型并展示气泡
                doSendPrompt(rawText)
            }
        }
    }

    fun handleBuiltinCommand(name: String) {
        handleSlashCommand("/$name")
    }

    fun sendPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 纯控制类斜杠指令（如 /reload, /compact, /model 等）分发到底层控制或UI弹窗，绝不当作聊天 prompt 发给大模型
        if (isControlSlashCommand(trimmed)) {
            handleSlashCommand(trimmed)
            return
        }

        doSendPrompt(trimmed)
    }

    fun sendSteer(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        if (isControlSlashCommand(trimmed)) {
            handleSlashCommand(trimmed)
            return
        }

        val isBtw = trimmed.startsWith("/btw", ignoreCase = true) || trimmed.startsWith("/side", ignoreCase = true)
        val question = if (isBtw) {
            trimmed.replaceFirst(Regex("""^/(btw|side)(:\w+)?\s*""", RegexOption.IGNORE_CASE), "").trim()
        } else {
            trimmed
        }

        if (isBtw && question.isEmpty()) {
            Toast.makeText(context, "请输入旁支提问内容 (例如: /btw 你的问题)", Toast.LENGTH_SHORT).show()
            inputText = "/btw "
            return
        }

        inputText = ""
        lastSentText = trimmed
        state.addUserMessage(
            text = trimmed,
            model = modelLabel ?: modelKey,
            provider = state.currentModelProvider,
            sendType = if (isBtw) "btw" else "steer",
        )
        items = state.items
        val steerPrompt = if (isBtw) {
            "【旁支提问/BTW】$question\n(说明：这是旁支提问，请直接简要回答该问题，无需调用工具修改项目代码。)"
        } else {
            trimmed
        }
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "steer").put("message", steerPrompt))
        Toast.makeText(context, if (isBtw) "已发送旁支提问引导 (BTW)" else "已发送引导指令 (Steer)", Toast.LENGTH_SHORT).show()
    }

    fun sendFollowUp(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        if (isControlSlashCommand(trimmed)) {
            handleSlashCommand(trimmed)
            return
        }

        val isBtw = trimmed.startsWith("/btw", ignoreCase = true) || trimmed.startsWith("/side", ignoreCase = true)
        val question = if (isBtw) {
            trimmed.replaceFirst(Regex("""^/(btw|side)(:\w+)?\s*""", RegexOption.IGNORE_CASE), "").trim()
        } else {
            trimmed
        }

        if (isBtw && question.isEmpty()) {
            Toast.makeText(context, "请输入旁支提问内容 (例如: /btw 你的问题)", Toast.LENGTH_SHORT).show()
            inputText = "/btw "
            return
        }

        inputText = ""
        lastSentText = trimmed
        state.addUserMessage(
            text = trimmed,
            model = modelLabel ?: modelKey,
            provider = state.currentModelProvider,
            sendType = if (isBtw) "btw" else "follow_up",
        )
        items = state.items
        val followUpPrompt = if (isBtw) {
            "【旁支提问/BTW】$question\n(说明：这是排队旁支提问，待当前任务完成后请简要解答此疑问。)"
        } else {
            trimmed
        }
        repository.sendAgentCommand(sessionId, JSONObject().put("type", "follow_up").put("message", followUpPrompt))
        Toast.makeText(context, if (isBtw) "已加入排队旁支提问 (BTW)" else "已加入排队跟进 (Follow-up)", Toast.LENGTH_SHORT).show()
    }

    fun sendDialogResponse(
        item: QuestionOptionBlock,
        payload: JSONObject,
        summary: String,
        selectedKeys: Set<String> = emptySet(),
    ) {
        val requestId = item.requestId ?: return
        state.resolveDialog(requestId, summary, selectedKeys)
        items = state.items
        repository.sendAgentCommand(sessionId, payload)
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { lazyListCoordinates = it }
                .graphicsLayer {
                    alpha = if (chatRows.isEmpty() || isBottomSynced) 1f else 0f
                },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (chatRows.isEmpty()) {
                item {
                    if (isNewlyCreated) {
                        AgentWelcomeCard(
                            isInitializing = !isInitialized,
                            elapsedSeconds = initElapsedSeconds,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = GeekColors.TerminalCyan,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }

            itemsIndexed(chatRows, key = { _, row ->
                when (row) {
                    is ChatRow.Message -> row.item.id
                    is ChatRow.SystemGroup -> "sysgroup-${row.items.first().id}"
                    is ChatRow.TurnSummary -> "turn-${row.turnId}-${row.turnIndex}"
                }
            }) { rowIndex, row ->
                when (row) {
                    is ChatRow.SystemGroup -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (sysMsg in row.items) {
                                SystemStatusBadge(sysMsg)
                            }
                        }
                    }

                    is ChatRow.TurnSummary -> {
                        val turnKey = "${row.turnId}-${row.turnIndex}"
                        val isExpanded = expandedTurns[turnKey] ?: (row.live || (rowIndex == chatRows.lastIndex && row.entries.isNotEmpty()))
                        TurnSummaryCard(
                            row = row,
                            expanded = isExpanded,
                            onToggle = {
                                expandedTurns[turnKey] = !isExpanded
                            },
                            onToggleWithCoords = { coords ->
                                if (isExpanded) {
                                    scrollToCoordinatesIfHidden(coords)
                                }
                            },
                            onChildCollapsedWithCoords = { coords ->
                                scrollToCoordinatesIfHidden(coords)
                            },
                        )
                    }

                    is ChatRow.Message -> when (val item = row.item) {
                        is UserMessage -> UserMessageBubble(
                            message = item,
                            onCollapsed = {
                                scrollToRowIfHidden(rowIndex)
                            },
                            onCollapsedWithCoords = { coords ->
                                scrollToCoordinatesIfHidden(coords)
                            },
                            onFork = { userMsg -> forkConfirmTarget = userMsg },
                            onEdit = { text ->
                                inputText = text
                                Toast.makeText(context, "已填入输入框，可修改后重新发送", Toast.LENGTH_SHORT).show()
                            },
                        )
                        is ThinkingBlock -> ThinkingAccordionCard(
                            block = item,
                            onCollapsed = {
                                scrollToRowIfHidden(rowIndex)
                            },
                            onCollapsedWithCoords = { coords ->
                                scrollToCoordinatesIfHidden(coords)
                            },
                        )
                        is ToolCallBlock -> ToolExecutionCard(
                            tool = item,
                            onCollapsed = {
                                scrollToRowIfHidden(rowIndex)
                            },
                            onCollapsedWithCoords = { coords ->
                                scrollToCoordinatesIfHidden(coords)
                            },
                        )
                        is QuestionOptionBlock -> QuestionOptionCard(
                            item = item,
                            onSelectOption = { option ->
                                when {
                                    item.requestId == null -> Unit
                                    item.method == "confirm" -> {
                                        val confirmed = option.key == "yes"
                                        sendDialogResponse(
                                            item,
                                            JSONObject()
                                                .put("type", "extension_ui_response")
                                                .put("id", item.requestId)
                                                .put("confirmed", confirmed),
                                            summary = if (confirmed) "是" else "否",
                                            selectedKeys = if (confirmed) setOf("yes", "是") else setOf("no", "否"),
                                        )
                                    }

                                    else -> {
                                        val sendVal = if (option.key.matches(Regex("""^\d+$"""))) option.key else option.label
                                        sendDialogResponse(
                                            item,
                                            JSONObject()
                                                .put("type", "extension_ui_response")
                                                .put("id", item.requestId)
                                                .put("value", sendVal)
                                                .put("key", option.key),
                                            summary = option.label,
                                            selectedKeys = setOf(option.key, option.label),
                                        )
                                    }
                                }
                            },
                            onSelectMultiple = { selectedOptions, customText ->
                                val requestId = item.requestId ?: return@QuestionOptionCard
                                val labels = selectedOptions.map { it.label }
                                val keys = selectedOptions.map { it.key }
                                val keysText = keys.joinToString(",")
                                val summary = (labels + listOfNotNull(customText?.takeIf { it.isNotEmpty() }?.let { "附: $it" })).joinToString(", ")
                                val sendVal = if (keys.any { it.matches(Regex("""^\d+$""")) }) keysText else summary
                                val payload = JSONObject()
                                    .put("type", "extension_ui_response")
                                    .put("id", requestId)
                                    .put("values", JSONArray(labels))
                                    .put("keys", JSONArray(keys))
                                    .put("value", sendVal)
                                if (!customText.isNullOrBlank()) {
                                    payload.put("customText", customText)
                                }
                                sendDialogResponse(item, payload, summary, selectedKeys = (keys + labels).toSet())
                            },
                            onSubmitText = { text ->
                                val requestId = item.requestId ?: return@QuestionOptionCard
                                sendDialogResponse(
                                    item,
                                    JSONObject()
                                        .put("type", "extension_ui_response")
                                        .put("id", requestId)
                                        .put("value", text),
                                    summary = text,
                                    selectedKeys = setOf(text),
                                )
                            },
                        )

                        is ErrorMessageBlock -> AgentErrorMessageCard(item)
                        is AssistantResponse -> AssistantMessageView(item)
                        is SystemStatusMessage -> SystemStatusBadge(item)
                    }
                }
            }
        }

        // Slash command palette: "/" opens it, typing filters. Built-ins map
        // to RPC commands; everything else comes from pi's get_commands and
        // is sent through prompt for pi to resolve.
        val slashQuery = if (inputText.startsWith("/")) inputText.removePrefix("/").substringBefore(" ") else null
        if (slashQuery != null) {
            val matches = buildList {
                for ((name, description) in BUILTIN_SLASH_COMMANDS) {
                    if (name.startsWith(slashQuery, ignoreCase = true)) add(Triple(name, description, true))
                }
                for (command in slashCommands) {
                    if (command.name.startsWith(slashQuery, ignoreCase = true)) {
                        add(Triple(command.name, command.description ?: "", false))
                    }
                }
            }
            if (matches.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = GeekColors.CardSurface,
                    border = BorderStroke(1.dp, GeekColors.BorderHighlight),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        for ((name, description, builtin) in matches) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (name in setOf("btw", "side", "export") || name.startsWith("skill:")) {
                                            inputText = "/$name "
                                        } else {
                                            handleSlashCommand("/$name")
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "/$name",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = GeekColors.TerminalCyan,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeekColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (builtin) {
                                    Text(
                                        text = "内置",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = GeekColors.BrandAccent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (agentRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PulsingDot(
                        color = GeekColors.AmberWarn,
                        glowColor = GeekColors.AmberWarnGlow,
                    )
                    Text(
                        text = "Agent 执行中 · 支持中途引导与排队",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = GeekColors.TextSecondary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GeekColors.RoseError.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, GeekColors.RoseError.copy(alpha = 0.5f)),
                    onClick = {
                        state.onAbort()
                        items = state.items
                        agentRunning = false
                        repository.sendAgentCommand(sessionId, JSONObject().put("type", "abort"))
                        Toast.makeText(context, "已停止生成", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(
                        text = "■ 停止",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = GeekColors.RoseError,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (statusLine != null) {
            Text(
                text = statusLine!!,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = GeekColors.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        val currentLevel = thinking?.trim()?.takeIf { it.isNotEmpty() } ?: "off"
        val isThinkingActive = !currentLevel.equals("off", ignoreCase = true) && !currentLevel.equals("none", ignoreCase = true)

        GuiChatInputBar(
            chips = listOf(
                ComposerChip(
                    label = modelLabel ?: "选择模型",
                    sub = null,
                    icon = Icons.Default.Tune,
                    highlight = true,
                    activeColor = GeekColors.BrandAccent,
                    onClick = { showModelPicker = true },
                ),
                ComposerChip(
                    label = currentLevel,
                    sub = null,
                    icon = Icons.Default.Psychology,
                    highlight = isThinkingActive,
                    activeColor = GeekColors.BrandPurple,
                    onClick = ::cycleThinkingLevel,
                ),
                ComposerChip(
                    label = "重新加载",
                    sub = null,
                    icon = Icons.Default.Refresh,
                    highlight = false,
                    onClick = ::doReload,
                ),
                ComposerChip(
                    label = "+ 新会话",
                    sub = null,
                    highlight = false,
                    onClick = ::doNewSession,
                ),
            ),
            inputText = inputText,
            onInputTextChange = { inputText = it },
            onSendPrompt = ::sendPrompt,
            agentRunning = agentRunning,
            onSendSteer = ::sendSteer,
            onSendFollowUp = ::sendFollowUp,
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            currentModelKey = modelKey,
            currentModelLabel = modelLabel,
            currentModelProvider = state.currentModelProvider,
            models = effectiveModels,
            sessionLoadedModels = state.availableModels,
            onPickModel = { option ->
                state.updateModel(option)
                modelKey = state.currentModelKey
                modelLabel = state.currentModelLabel
                repository.setPiModelProvider(option.provider)
                repository.sendAgentCommand(
                    sessionId,
                    JSONObject()
                        .put("type", "set_model")
                        .put("provider", option.provider)
                        .put("modelId", option.id),
                )
                showModelPicker = false
            },
            onAddNewModel = {
                showModelPicker = false
                showAddLoginModelDialog = true
            },
            onDeleteModel = { option ->
                state.removeModel(option.provider, option.id)
                models = state.availableModels
                repository.deletePiModel(option.provider, option.id)
                Toast.makeText(context, "已删除模型「${option.cleanLabel}」", Toast.LENGTH_SHORT).show()
            },
            onRefreshModels = {
                repository.requestPiAgentModels()
                repository.sendAgentCommand(sessionId, JSONObject().put("type", "get_available_models"))
                Toast.makeText(context, "正在从电脑端重新读取模型...", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showModelPicker = false },
        )
    }

    if (showSessionPicker) {
        val currentSessionCwd = repository.knownSessions.collectAsState().value.firstOrNull { it.sid == sessionId }?.cwd
        PiSessionPickerDialog(
            sessions = piSessions,
            currentCwd = currentSessionCwd,
            lineageMap = repository.lineageStore.getLineageMap(),
            onPick = { session ->
                showSessionPicker = false
                repository.sendAgentCommand(
                    sessionId,
                    JSONObject()
                        .put("type", "switch_session")
                        .put("sessionPath", session.file),
                )
            },
            onRefresh = {
                repository.requestPiSessionList(sessionId, null)
            },
            onDeleteSession = { session ->
                repository.deletePiSession(session.file, session.id)
                Toast.makeText(context, "已删除该会话记录", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSessionPicker = false },
        )
    }

    if (showAddLoginModelDialog) {
        AddLoginModelDialog(
            existingProviders = effectiveModels.map { it.provider }.filter { it.isNotBlank() }.distinct(),
            allKnownPiModels = effectiveModels,
            repository = repository,
            onConfirmLogin = { provider, apiKey, baseUrl, selectedModels ->
                showAddLoginModelDialog = false
                triggerPiLogin(provider, apiKey, baseUrl, selectedModels)
            },
            onDismiss = { showAddLoginModelDialog = false },
        )
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = {
                Text("退出会话？", style = MaterialTheme.typography.titleMedium, color = GeekColors.TextPrimary)
            },
            text = {
                Text(
                    text = "将结束当前的 pi 会话进程。会话记录仍保留在电脑上，之后可以用 pi -c 继续。",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeekColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirm = false
                    repository.killSession(sessionId)
                }) {
                    Text("结束", color = GeekColors.RoseError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirm = false }) {
                    Text("取消", color = GeekColors.TextPrimary)
                }
            },
        )
    }

    forkConfirmTarget?.let { target ->
        ForkConfirmDialog(
            previewText = target.text,
            onConfirmWithSummary = { mode, customPrompt ->
                callPiForkWithSummary(target, mode, customPrompt)
            },
            onDismiss = { forkConfirmTarget = null },
        )
    }
}

// --------------------------------------------------- PTY session (legacy)

@Composable
private fun TranscriptGuiChat(
    terminalView: TerminalView,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var transcript by remember { mutableStateOf(terminalView.getTranscriptText()) }
    val pendingUserMessages = remember { mutableStateListOf<String>() }
    // Parse transcript into structured cards
    val messages = remember(transcript, pendingUserMessages.toList()) {
        TerminalChatParser.parseTranscript(transcript, pendingUserMessages)
    }
    val initialBottomIndex = remember {
        if (messages.isNotEmpty()) messages.size - 1 else 0
    }
    val listState = remember {
        LazyListState(firstVisibleItemIndex = initialBottomIndex)
    }
    var isBottomSynced by remember { mutableStateOf(messages.isNotEmpty()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var forkConfirmTarget by remember { mutableStateOf<UserMessage?>(null) }

    val transcriptDensity = LocalDensity.current
    val transcriptTopThresholdPx = with(transcriptDensity) { 36.dp.toPx() }
    var transcriptListCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun isRowTopVisible(index: Int): Boolean {
        val layoutInfo = listState.layoutInfo
        val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        return info.offset in viewportStart until viewportEnd
    }

    fun scrollToRowIfHidden(index: Int) {
        scope.launch {
            delay(50)
            if (!isRowTopVisible(index)) {
                val listCoords = transcriptListCoordinates
                val targetY = if (listCoords != null && listCoords.isAttached) listCoords.size.height * 0.32f else 0f
                listState.animateScrollToItem(index)
                if (targetY > 0f) {
                    listState.animateScrollBy(-targetY)
                }
            } else {
                delay(150)
                if (!isRowTopVisible(index)) {
                    val listCoords = transcriptListCoordinates
                    val targetY = if (listCoords != null && listCoords.isAttached) listCoords.size.height * 0.32f else 0f
                    listState.animateScrollToItem(index)
                    if (targetY > 0f) {
                        listState.animateScrollBy(-targetY)
                    }
                }
            }
        }
    }

    fun scrollToTranscriptCoordinatesIfHidden(targetCoordinates: LayoutCoordinates?) {
        val target = targetCoordinates ?: return
        scope.launch {
            delay(50)
            val listCoords = transcriptListCoordinates ?: return@launch
            if (!target.isAttached || !listCoords.isAttached) return@launch
            val cardTop = listCoords.localPositionOf(target, Offset.Zero).y
            val listHeight = listCoords.size.height.toFloat()
            val bottomThresholdPx = listHeight - transcriptTopThresholdPx
            val targetY = listHeight * 0.32f
            if (cardTop < transcriptTopThresholdPx || cardTop >= bottomThresholdPx) {
                listState.animateScrollBy(cardTop - targetY)
            } else {
                delay(150)
                if (!target.isAttached || !listCoords.isAttached) return@launch
                val cardTopAfter = listCoords.localPositionOf(target, Offset.Zero).y
                if (cardTopAfter < transcriptTopThresholdPx || cardTopAfter >= bottomThresholdPx) {
                    listState.animateScrollBy(cardTopAfter - targetY)
                }
            }
        }
    }

    // Synchronize with real-time TerminalView events
    DisposableEffect(terminalView) {
        val listener = {
            transcript = terminalView.getTranscriptText()
        }
        terminalView.onContentUpdated = listener
        // Load initial transcript
        transcript = terminalView.getTranscriptText()
        onDispose {
            if (terminalView.onContentUpdated == listener) {
                terminalView.onContentUpdated = null
            }
        }
    }

    // Periodic polling to catch emulator transcript flushing and async replay frames
    LaunchedEffect(terminalView) {
        while (true) {
            delay(150)
            val current = terminalView.getTranscriptText()
            if (current != transcript) {
                transcript = current
            }
        }
    }

    // Auto-scroll on new message or transcript growth
    LaunchedEffect(messages.size, transcript.length) {
        if (messages.isNotEmpty()) {
            if (!isBottomSynced) {
                listState.scrollToItem(messages.size - 1)
                isBottomSynced = true
            } else {
                listState.scrollToItem(messages.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Message stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { transcriptListCoordinates = it }
                .graphicsLayer {
                    alpha = if (messages.isEmpty() || isBottomSynced) 1f else 0f
                },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    AgentWelcomeCard(
                        isInitializing = transcript.isBlank(),
                        elapsedSeconds = 0,
                    )
                }
            }
            itemsIndexed(messages, key = { _, it -> it.id }) { index, item ->
                when (item) {
                    is UserMessage -> UserMessageBubble(
                        message = item,
                        onCollapsed = { scrollToRowIfHidden(index) },
                        onCollapsedWithCoords = { coords -> scrollToTranscriptCoordinatesIfHidden(coords) },
                        onFork = {
                            forkConfirmTarget = item
                        },
                        onEdit = { text ->
                            inputText = text
                            Toast.makeText(context, "已填入输入框，可修改后重新发送", Toast.LENGTH_SHORT).show()
                        },
                    )
                    is ThinkingBlock -> ThinkingAccordionCard(
                        block = item,
                        onCollapsed = { scrollToRowIfHidden(index) },
                        onCollapsedWithCoords = { coords -> scrollToTranscriptCoordinatesIfHidden(coords) },
                    )
                    is ToolCallBlock -> ToolExecutionCard(
                        tool = item,
                        onCollapsed = { scrollToRowIfHidden(index) },
                        onCollapsedWithCoords = { coords -> scrollToTranscriptCoordinatesIfHidden(coords) },
                    )
                    is QuestionOptionBlock -> QuestionOptionCard(
                        item = item,
                        onSelectOption = { option ->
                            onSendText(option.key)
                        },
                        onSelectMultiple = { options, customText ->
                            val keysText = options.joinToString(",") { it.key }
                            val fullText = if (customText.isNullOrBlank()) keysText else if (keysText.isEmpty()) customText else "$keysText,$customText"
                            onSendText(fullText.trim())
                        },
                        onSubmitText = { text ->
                            onSendText(text)
                        },
                    )
                    is ErrorMessageBlock -> AgentErrorMessageCard(item)
                    is AssistantResponse -> AssistantMessageView(item)
                    is SystemStatusMessage -> SystemStatusBadge(item)
                }
            }
        }

        // Bottom Input Section
        GuiChatInputBar(
            chips = PI_QUICK_COMMANDS.map { cmd ->
                ComposerChip(
                    label = cmd.command,
                    sub = cmd.label,
                    highlight = inputText.trim() == cmd.command || inputText.startsWith("${cmd.command} "),
                    onClick = {
                        if (cmd.command in setOf("/btw", "/side", "/diff", "/export") || cmd.command.startsWith("/skill:")) {
                            inputText = "${cmd.command} "
                        } else if (inputText.trim() == cmd.command) {
                            onSendText(cmd.command)
                        } else {
                            inputText = "${cmd.command} "
                        }
                    },
                )
            },
            inputText = inputText,
            onInputTextChange = { inputText = it },
            onSendPrompt = { text ->
                if (text.isNotBlank()) {
                    val prompt = text.trim()
                    val isBtw = prompt.startsWith("/btw", ignoreCase = true) || prompt.startsWith("/side", ignoreCase = true)
                    val question = if (isBtw) {
                        prompt.replaceFirst(Regex("""^/(btw|side)(:\w+)?\s*""", RegexOption.IGNORE_CASE), "").trim()
                    } else {
                        prompt
                    }

                    if (isBtw && question.isEmpty()) {
                        Toast.makeText(context, "请输入旁支提问内容 (例如: /btw 你的问题)", Toast.LENGTH_SHORT).show()
                        inputText = "/btw "
                    } else {
                        inputText = ""
                        if (isControlSlashCommand(prompt)) {
                            // 纯控制类斜杠指令（如 /reload, /compact, /model 等）直接通过终端输入发送，不加入 pendingUserMessages 气泡
                            onSendText(prompt)
                        } else {
                            // /btw 或普通提问等对话内容，加入 pendingUserMessages 气泡并在终端执行
                            pendingUserMessages.add(prompt)
                            onSendText(prompt)
                        }
                    }
                }
            },
        )
    }

    forkConfirmTarget?.let { target ->
        ForkConfirmDialog(
            previewText = target.text,
            onConfirm = {
                onSendText("/fork\n")
                Toast.makeText(context, "已发送 /fork 命令", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { forkConfirmTarget = null },
        )
    }
}

/** RPC commands whose successful response should be followed by a get_state refresh. */
private val REFRESH_AFTER_SET_COMMANDS = setOf(
    "set_model",
    "cycle_model",
    "reload",
    "reload_config",
)

/** Commands that carry user text; a rejected one must have its text restored to the composer. */
private val SEND_COMMANDS = setOf("prompt", "steer", "follow_up")

/** A rendered row of the RPC chat: either a card or a per-turn fold summary. */
private sealed interface ChatRow {
    data class Message(val item: GuiMessageItem) : ChatRow

    data class SystemGroup(val items: List<SystemStatusMessage>) : ChatRow

    data class TurnSummary(
        val turnId: String,
        val turnIndex: Int = 0,
        val messageCount: Int,
        val toolCount: Int,
        /** The turn's thinking and tool calls, in order. */
        val entries: List<GuiMessageItem> = emptyList(),
        /** pi is still working in this turn — the card stays expanded. */
        val live: Boolean = false,
    ) : ChatRow
}

/**
 * Groups the stream by turn: execution details (thinking blocks and tool calls) fold
 * into a collapsible TurnSummaryCard ("执行详情 · 思考过程 · N 次工具").
 * ALL assistant text responses/outputs are rendered strictly outside the fold card
 * as top-level chat messages, preserving conversational visibility and temporal order.
 * Consecutive system status notices are grouped together into [ChatRow.SystemGroup]
 * with compact 4dp spacing.
 */
private fun buildChatRows(items: List<GuiMessageItem>): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var executionEntries = mutableListOf<GuiMessageItem>()
    var systemEntries = mutableListOf<SystemStatusMessage>()
    var turnId: String? = null
    var turnIndex = 0

    fun flushExecution() {
        if (executionEntries.isEmpty()) return
        val toolCount = executionEntries.count { it is ToolCallBlock }
        val thinkingCount = executionEntries.count { it is ThinkingBlock }
        val live = executionEntries.any {
            (it is ToolCallBlock && it.status == ToolStatus.RUNNING) ||
                (it is ThinkingBlock && !it.isFinished)
        }
        rows.add(
            ChatRow.TurnSummary(
                turnId = turnId ?: "turn",
                turnIndex = turnIndex++,
                messageCount = thinkingCount,
                toolCount = toolCount,
                entries = executionEntries.toList(),
                live = live,
            ),
        )
        executionEntries = mutableListOf()
    }

    fun flushSystem() {
        if (systemEntries.isEmpty()) return
        if (systemEntries.size == 1) {
            rows.add(ChatRow.Message(systemEntries.first()))
        } else {
            rows.add(ChatRow.SystemGroup(systemEntries.toList()))
        }
        systemEntries = mutableListOf()
    }

    for (item in items) {
        when (item) {
            is SystemStatusMessage -> {
                flushExecution()
                systemEntries.add(item)
            }

            is UserMessage -> {
                flushExecution()
                flushSystem()
                turnId = item.id
                turnIndex = 0
                rows.add(ChatRow.Message(item))
            }

            is ThinkingBlock, is ToolCallBlock -> {
                flushSystem()
                executionEntries.add(item)
            }

            is AssistantResponse -> {
                // Any thinking or tool execution preceding this response folds into TurnSummary
                flushExecution()
                flushSystem()
                rows.add(ChatRow.Message(item))
            }

            else -> {
                // Interactive options and other notices stay outside the fold
                // so they are never hidden inside a collapsed card.
                flushExecution()
                flushSystem()
                rows.add(ChatRow.Message(item))
            }
        }
    }
    flushExecution()
    flushSystem()
    return rows
}

/** One row of the ☰ quick-navigation outline. User messages start a new turn. */
private fun outlineItem(item: GuiMessageItem): GuiChatOutlineItem = when (item) {
    is UserMessage -> {
        val isBtw = item.sendType == "btw" || item.text.startsWith("/btw", ignoreCase = true) || item.text.startsWith("/side", ignoreCase = true)
        GuiChatOutlineItem(
            id = item.id,
            tag = if (isBtw) "旁支" else "用户",
            label = item.text.take(40).replace('\n', ' '),
            turnStart = true,
            entryId = item.entryId,
        )
    }
    is AssistantResponse -> GuiChatOutlineItem(item.id, "回复", item.text.take(40).replace('\n', ' '))
    is ThinkingBlock -> GuiChatOutlineItem(
        item.id,
        "思考",
        if (item.isFinished) "思考过程" else "思考中…",
    )
    is ToolCallBlock -> GuiChatOutlineItem(item.id, "工具", "${item.toolName} · ${item.summary}".take(44))
    is QuestionOptionBlock -> GuiChatOutlineItem(item.id, "交互", item.question.take(40))
    is SystemStatusMessage -> GuiChatOutlineItem(item.id, "系统", item.text.take(40).replace('\n', ' '))
    is ErrorMessageBlock -> GuiChatOutlineItem(item.id, "错误", item.error.take(40).replace('\n', ' '))
}

/**
 * TUI built-in slash commands that have RPC equivalents on the phone. Everything
 * else listed by pi's get_commands (extensions / prompt templates / skills) is
 * sent through prompt for pi to resolve.
 */
private val BUILTIN_SLASH_COMMANDS = linkedMapOf(
    "model" to "切换模型",
    "login" to "新增模型与服务商登录",
    "thinking" to "切换思考强度",
    "new" to "开新会话",
    "reload" to "重新加载配置与扩展",
    "compact" to "压缩整理会话历史",
    "diff" to "查看代码变更差异",
    "btw" to "旁支快捷提问",
    "clear" to "清屏/清空终端",
    "resume" to "切换到历史会话",
    "status" to "会话统计",
    "copy" to "复制最终回复",
    "fork" to "从当前节点分叉",
    "tree" to "会话分支树",
    "export" to "导出会话记录",
    "help" to "查看帮助",
    "quit" to "结束当前会话",
)

/**
 * Adapter that connects GuiChatScreen's ChatRow.TurnSummary to the shared
 * TurnSummaryCard in GuiCards.kt with synchronized smooth tween animations.
 */
@Composable
private fun TurnSummaryCard(
    row: ChatRow.TurnSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
    onChildCollapsedWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
) {
    TurnSummaryCard(
        turnId = row.turnId,
        messageCount = row.messageCount,
        toolCount = row.toolCount,
        live = row.live,
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
        onToggleWithCoords = onToggleWithCoords,
    ) {
        row.entries.forEach { item ->
            when (item) {
                is ThinkingBlock -> ThinkingAccordionCard(
                    block = item,
                    onCollapsedWithCoords = onChildCollapsedWithCoords,
                )
                is ToolCallBlock -> ToolExecutionCard(
                    tool = item,
                    onCollapsedWithCoords = onChildCollapsedWithCoords,
                )
                else -> Unit
            }
        }
    }
}



/**
 * Apple HIG-style Model Picker Dialog with real-time search filter and clean typography.
 */
@Composable
internal fun ModelPickerDialog(
    currentModelKey: String?,
    currentModelLabel: String?,
    currentModelProvider: String?,
    models: List<PiModelOption>,
    sessionLoadedModels: List<PiModelOption> = emptyList(),
    onPickModel: (PiModelOption) -> Unit,
    onAddNewModel: () -> Unit,
    onDeleteModel: ((PiModelOption) -> Unit)? = null,
    onRefreshModels: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProviderFilter by remember { mutableStateOf<String?>(null) }
    var modelToDelete by remember { mutableStateOf<PiModelOption?>(null) }
    val providers = remember(models) {
        models.map { it.provider.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    val filteredModels = remember(models, searchQuery, selectedProviderFilter) {
        val q = searchQuery.trim()
        models.filter { option ->
            val matchProvider = selectedProviderFilter == null || option.provider.equals(selectedProviderFilter, ignoreCase = true)
            val matchQuery = q.isBlank() || (
                option.label.contains(q, ignoreCase = true) ||
                option.cleanLabel.contains(q, ignoreCase = true) ||
                option.key.contains(q, ignoreCase = true) ||
                option.provider.contains(q, ignoreCase = true) ||
                option.id.contains(q, ignoreCase = true)
            )
            matchProvider && matchQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = GeekColors.CardSurface,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = GeekColors.BrandAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onAddNewModel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("新增模型", color = GeekColors.BrandAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GeekColors.BrandAccentGlow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "切换模型",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                    val displayCurrent = currentModelLabel
                        ?: stripProviderPrefix(currentModelKey, currentModelProvider)
                    val prov = currentModelProvider?.takeIf { it.isNotBlank() }
                        ?: currentModelKey?.takeIf { it.contains("/") }?.substringBefore("/")
                    val subText = buildString {
                        if (!displayCurrent.isNullOrBlank()) {
                            append("当前：$displayCurrent")
                            if (!prov.isNullOrBlank()) {
                                append(" · $prov")
                            }
                        } else {
                            append("共 ${models.size} 个可用模型")
                        }
                    }
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = GeekColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onRefreshModels != null) {
                    IconButton(
                        onClick = onRefreshModels,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新从电脑端读取",
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (models.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "搜索模型或服务商 (${filteredModels.size}/${models.size})...",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GeekColors.TerminalCyan,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清空",
                                        tint = GeekColors.TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.CardElevated,
                            unfocusedContainerColor = GeekColors.CardElevated,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                            cursorColor = GeekColors.TerminalCyan,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )

                    Spacer(Modifier.height(10.dp))
                }

                if (providers.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isAllSelected = selectedProviderFilter == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllSelected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                            border = BorderStroke(0.6.dp, if (isAllSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedProviderFilter = null },
                        ) {
                            Text(
                                text = "全部 (${models.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isAllSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        providers.forEach { prov ->
                            val isSelected = selectedProviderFilter.equals(prov, ignoreCase = true)
                            val count = models.count { it.provider.equals(prov, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                                border = BorderStroke(0.6.dp, if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedProviderFilter = if (isSelected) null else prov
                                    },
                            ) {
                                Text(
                                    text = "$prov ($count)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (models.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    ) {
                        if (onRefreshModels != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = GeekColors.TerminalCyan,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "正在从电脑端读取最新模型...",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRefreshModels) {
                                Text("未获取到？点击重新读取", color = GeekColors.BrandAccent, fontSize = 13.sp)
                            }
                        } else {
                            Text(
                                text = "暂无已配置 API Key 的可用模型",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "请先配置服务商凭据与端点",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = onAddNewModel) {
                                Text("+ 配置服务商与 API Key", color = GeekColors.BrandAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (filteredModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到与 \"$searchQuery\" 匹配的模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextMuted,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(filteredModels, key = { _, it -> "${it.provider}:${it.id.ifEmpty { it.key }}" }) { index, option ->
                            val selected = option.key == currentModelKey || option.id == currentModelKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) GeekColors.BrandAccent.copy(alpha = 0.12f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPickModel(option) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = if (selected) "●" else "○",
                                        color = if (selected) GeekColors.BrandAccent else GeekColors.TextMuted,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.cleanLabel,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (selected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (option.provider.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = if (selected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                                                    border = BorderStroke(0.6.dp, if (selected) GeekColors.BrandAccent.copy(alpha = 0.6f) else GeekColors.BorderSubtle),
                                                ) {
                                                    Text(
                                                        text = option.provider,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = if (selected) GeekColors.BrandAccent else GeekColors.TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            val isLoadedInSession = sessionLoadedModels.isEmpty() || sessionLoadedModels.any {
                                                it.provider.equals(option.provider, ignoreCase = true) && it.id.equals(option.id, ignoreCase = true)
                                            }
                                            if (!isLoadedInSession) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = GeekColors.DeepCanvas,
                                                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                                                ) {
                                                    Text(
                                                        text = "需新建会话",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                        color = GeekColors.TextMuted,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            if (option.cleanId.isNotBlank() && !option.cleanId.equals(option.cleanLabel, ignoreCase = true)) {
                                                Text(
                                                    text = option.cleanId,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.5.sp,
                                                    ),
                                                    color = GeekColors.TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                    if (selected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = GeekColors.BrandAccent.copy(alpha = 0.18f),
                                        ) {
                                            Text(
                                                text = "使用中",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = GeekColors.BrandAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    if (onDeleteModel != null) {
                                        Spacer(Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { modelToDelete = option },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "删除模型",
                                                tint = GeekColors.TextMuted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (index < filteredModels.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp)
                                        .height(0.6.dp)
                                        .background(GeekColors.TextMuted.copy(alpha = 0.12f)),
                                )
                            }
                        }
                    }
                }


            }
        },
    )

    if (modelToDelete != null) {
        val target = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = GeekColors.CardElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = GeekColors.RoseError,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "删除模型",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "确定要从电脑端删除模型「${target.cleanLabel}」吗？\n此操作将同步在电脑端 models.json / auth.json 中移除该模型配置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GeekColors.TextSecondary,
                        lineHeight = 20.sp,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.DeepCanvas,
                        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "模型 ID: ${target.id.ifEmpty { target.key }}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                                color = GeekColors.TextPrimary,
                            )
                            if (target.provider.isNotBlank()) {
                                Text(
                                    text = "服务商: ${target.provider}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeekColors.TextMuted,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = target
                        modelToDelete = null
                        onDeleteModel?.invoke(toDel)
                    },
                ) {
                    Text("彻底删除", color = GeekColors.RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("取消", color = GeekColors.TextMuted)
                }
            },
        )
    }
}

private val modelDetectHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

internal val PRESET_PROVIDER_ENDPOINTS = mapOf(
    "deepseek" to "https://api.deepseek.com/v1",
    "siliconflow" to "https://api.siliconflow.cn/v1",
    "amd_radeon" to "https://developer.amd.com.cn/radeon/api/v1",
    "openai" to "https://api.openai.com/v1",
    "anthropic" to "https://api.anthropic.com/v1",
    "google" to "https://generativelanguage.googleapis.com/v1beta",
    "openrouter" to "https://openrouter.ai/api/v1",
    "moonshot" to "https://api.moonshot.cn/v1",
    "groq" to "https://api.groq.com/openai/v1",
    "mistral" to "https://api.mistral.ai/v1",
    "ollama" to "http://127.0.0.1:11434/v1",
)

private fun filterRecognizedModels(rawList: List<String>, provider: String): List<String> {
    val p = provider.lowercase()
    return rawList.map { it.trim() }
        .filter { it.isNotBlank() }
        .map { m ->
            if (p == "google" && m.startsWith("models/")) m.removePrefix("models/") else m
        }
        .filterNot { m ->
            val lower = m.lowercase()
            lower.contains("whisper") || lower.contains("dall-e") ||
            lower.contains("tts") || lower.contains("embedding") ||
            lower.contains("moderation") || lower.contains("babbage") ||
            lower.contains("davinci") || lower.contains("curie") ||
            lower.contains("realtime") || lower.contains("transcription") ||
            lower.contains("audio") || lower.contains("similarity") ||
            lower.contains("search") || lower.contains("text-search")
        }
        .filter { m ->
            val lower = m.lowercase()
            when (p) {
                "google" -> {
                    lower.contains("gemini") && !lower.contains("vision") && !lower.contains("aqa")
                }
                "openai" -> {
                    lower.startsWith("gpt-") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("chatgpt-")
                }
                "anthropic" -> {
                    lower.contains("claude")
                }
                "deepseek" -> {
                    lower.contains("deepseek") || lower == "deepseek-chat" || lower == "deepseek-reasoner"
                }
                else -> true
            }
        }
        .distinct()
        .sorted()
}

/**
 * Dialog for adding models and authenticating with AI providers via Pi Agent's /login command.
 */
@Composable
internal fun AddLoginModelDialog(
    existingProviders: List<String>,
    allKnownPiModels: List<PiModelOption> = emptyList(),
    repository: SessionRepository? = null,
    onConfirmLogin: (provider: String, apiKey: String, baseUrl: String?, models: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val presetProviders = listOf(
        "deepseek" to "DeepSeek",
        "siliconflow" to "硅基流动",
        "amd_radeon" to "AMD Radeon",
        "openai" to "OpenAI",
        "anthropic" to "Anthropic",
        "google" to "Gemini",
        "openrouter" to "OpenRouter",
        "groq" to "Groq",
        "mistral" to "Mistral",
        "ollama" to "Ollama",
        "custom" to "自定义",
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedProvider by remember { mutableStateOf("deepseek") }
    var customProviderText by remember { mutableStateOf("") }
    var apiKeyText by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var customBaseUrl by remember(selectedProvider) {
        mutableStateOf(PRESET_PROVIDER_ENDPOINTS[selectedProvider].orEmpty())
    }
    var showEndpointConfig by remember(selectedProvider) {
        mutableStateOf(selectedProvider == "custom" || selectedProvider == "ollama")
    }

    val effectiveProvider = if (selectedProvider == "custom") customProviderText.trim() else selectedProvider

    // 自动关联并预载电脑端已识别的该服务商模型，无需每次重复联网
    val availableModels = remember(selectedProvider, effectiveProvider, allKnownPiModels) {
        val initial = allKnownPiModels
            .filter { it.provider.equals(effectiveProvider, ignoreCase = true) }
            .map { it.id }
            .distinct()
        mutableStateListOf<String>().apply { addAll(initial) }
    }
    val selectedModels = remember(selectedProvider, effectiveProvider, allKnownPiModels) {
        val initial = allKnownPiModels
            .filter { it.provider.equals(effectiveProvider, ignoreCase = true) }
            .map { it.id }
            .distinct()
        mutableStateListOf<String>().apply { addAll(initial) }
    }

    var isDetecting by remember { mutableStateOf(false) }
    var isWaitingRemote by remember { mutableStateOf(false) }
    var detectStatusMessage by remember { mutableStateOf<String?>(null) }
    var manualModelInput by remember { mutableStateOf("") }
    var modelFilterQuery by remember { mutableStateOf("") }


    val fallbackFlow = remember { MutableStateFlow<com.piremote.app.data.ControlMessage.ModelsDetected?>(null) }
    val remoteResult by (repository?.modelsDetectedResult ?: fallbackFlow).collectAsState()
    LaunchedEffect(remoteResult) {
        remoteResult?.let { res ->
            if (res.provider.equals(effectiveProvider, ignoreCase = true)) {
                isDetecting = false
                isWaitingRemote = false
                if (res.success && res.models.isNotEmpty()) {
                    val filtered = filterRecognizedModels(res.models, effectiveProvider)
                    val resultList = if (filtered.isNotEmpty()) filtered else res.models
                    availableModels.clear()
                    availableModels.addAll(resultList)
                    selectedModels.clear()
                    selectedModels.addAll(resultList)
                    detectStatusMessage = "电脑端识别成功：发现 ${resultList.size} 个可用模型"
                } else {
                    detectStatusMessage = "电脑端未返回有效模型（${res.error ?: "未识别到模型"}），可手动输入添加"
                }
                repository?.clearModelsDetectedResult()
            }
        }
    }

    fun detectModelsFromPhone(targetProvider: String, key: String, endpoint: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val candidateUrls = mutableListOf<String>()
                val reqBuilder = Request.Builder().addHeader("Accept", "application/json")

                if (targetProvider == "google") {
                    candidateUrls.add("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                } else if (targetProvider == "anthropic") {
                    candidateUrls.add("https://api.anthropic.com/v1/models")
                    reqBuilder.addHeader("x-api-key", key)
                    reqBuilder.addHeader("anthropic-version", "2023-06-01")
                } else if (targetProvider == "deepseek") {
                    candidateUrls.add("https://api.deepseek.com/models")
                    candidateUrls.add("https://api.deepseek.com/v1/models")
                    if (key.isNotBlank()) reqBuilder.addHeader("Authorization", "Bearer $key")
                } else {
                    val raw = endpoint.trim().trimEnd('/')
                    val base = raw.removeSuffix("/chat/completions").removeSuffix("/chat")
                    if (base.endsWith("/v1")) {
                        candidateUrls.add("$base/models")
                        candidateUrls.add(base.removeSuffix("/v1") + "/models")
                    } else {
                        candidateUrls.add("$base/models")
                        candidateUrls.add("$base/v1/models")
                    }
                    if (key.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $key")
                    }
                }

                var fetchedList: List<String>? = null
                var lastErr = ""

                for (url in candidateUrls.distinct()) {
                    try {
                        modelDetectHttpClient.newCall(reqBuilder.url(url).build()).execute().use { resp ->
                            val body = resp.body?.string().orEmpty().trim()
                            if (resp.isSuccessful) {
                                val list = mutableListOf<String>()
                                if (body.startsWith("[")) {
                                    val arr = JSONArray(body)
                                    for (i in 0 until arr.length()) {
                                        val item = arr.optJSONObject(i) ?: continue
                                        val id = item.optString("id").ifEmpty { item.optString("name") }
                                        if (id.isNotBlank()) list.add(id)
                                    }
                                } else if (body.startsWith("{")) {
                                    val obj = JSONObject(body)
                                    val data = obj.optJSONArray("data")
                                        ?: obj.optJSONArray("models")
                                        ?: obj.optJSONArray("items")
                                    if (data != null) {
                                        for (i in 0 until data.length()) {
                                            val item = data.optJSONObject(i) ?: continue
                                            var id = item.optString("id").ifEmpty { item.optString("name").ifEmpty { item.optString("model") } }
                                            if (targetProvider == "google" && id.startsWith("models/")) {
                                                id = id.removePrefix("models/")
                                            }
                                            if (id.isNotBlank()) list.add(id)
                                        }
                                    }
                                }
                                val clean = filterRecognizedModels(list, targetProvider)
                                if (clean.isNotEmpty()) {
                                    fetchedList = clean
                                    return@use
                                } else if (list.isNotEmpty()) {
                                    fetchedList = list.distinct().sorted()
                                    return@use
                                }
                            } else {
                                lastErr = "HTTP ${resp.code}"
                            }
                        }
                        if (!fetchedList.isNullOrEmpty()) {
                            break
                        }
                    } catch (e: Exception) {
                        lastErr = e.message ?: "连接超时"
                    }
                }

                withContext(Dispatchers.Main) {
                    isDetecting = false
                    if (!fetchedList.isNullOrEmpty()) {
                        availableModels.clear()
                        availableModels.addAll(fetchedList!!)
                        selectedModels.clear()
                        selectedModels.addAll(fetchedList!!)
                        detectStatusMessage = "识别成功：发现 ${fetchedList!!.size} 个可用模型"
                    } else {
                        detectStatusMessage = "端点未返回模型（$lastErr），请检查 Key/网络或手动添加"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDetecting = false
                    detectStatusMessage = "识别异常: ${e.message}"
                }
            }
        }
    }

    fun detectModels() {
        val endpoint = customBaseUrl.trim().ifEmpty { PRESET_PROVIDER_ENDPOINTS[selectedProvider].orEmpty() }
        val key = apiKeyText.trim()
        if (key.isEmpty() && selectedProvider != "ollama") {
            val fromPc = allKnownPiModels.filter { it.provider.equals(effectiveProvider, ignoreCase = true) }.map { it.id }.distinct()
            if (fromPc.isNotEmpty()) {
                availableModels.clear()
                availableModels.addAll(fromPc)
                selectedModels.clear()
                selectedModels.addAll(fromPc)
                detectStatusMessage = "已直接从电脑端载入 ${fromPc.size} 个已知可用模型"
                return
            }
            detectStatusMessage = "未填入 Key，可填入凭据或直接在下方输入框手动添加模型"
            Toast.makeText(context, "未填入 Key，请填入凭据或在下方手动输入模型名称", Toast.LENGTH_SHORT).show()
            return
        }
        isDetecting = true
        detectStatusMessage = "正在请求电脑端 Agent 探测可用模型..."

        if (repository != null) {
            isWaitingRemote = true
            repository.detectRemoteModels(effectiveProvider, key, endpoint.takeIf { it.isNotBlank() })
            scope.launch {
                delay(3500)
                if (isWaitingRemote) {
                    isWaitingRemote = false
                    detectStatusMessage = "电脑端未返回，转由手机网络直接探测..."
                    detectModelsFromPhone(effectiveProvider, key, endpoint)
                }
            }
        } else {
            detectModelsFromPhone(effectiveProvider, key, endpoint)
        }
    }

    val queryTrimmed = modelFilterQuery.trim()
    val filteredDisplayModels = if (queryTrimmed.isBlank()) {
        availableModels.toList()
    } else {
        availableModels.filter { it.contains(queryTrimmed, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = GeekColors.CardSurface,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = GeekColors.TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val key = apiKeyText.trim()
                        val alreadyHasProvider = allKnownPiModels.any { it.provider.equals(effectiveProvider, ignoreCase = true) }
                        if (key.isEmpty() && selectedProvider != "ollama" && !alreadyHasProvider) {
                            Toast.makeText(context, "请先填入 API Key 凭据", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val finalModels = buildList {
                            addAll(selectedModels)
                            val manual = manualModelInput.trim()
                            if (manual.isNotBlank() && !contains(manual)) {
                                add(manual)
                            }
                        }
                        if (finalModels.isEmpty() && availableModels.isNotEmpty()) {
                            Toast.makeText(context, "请至少勾选或输入一个要添加的模型", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val finalBaseUrl = customBaseUrl.trim().takeIf { showEndpointConfig && it.isNotBlank() }
                        onConfirmLogin(effectiveProvider, key, finalBaseUrl, finalModels)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeekColors.BrandAccent,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (selectedModels.isNotEmpty() || manualModelInput.isNotBlank()) "确认添加" else "保存配置",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GeekColors.BrandAccentGlow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "新增模型与服务商",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                    Text(
                        text = "配置服务商凭据与已选模型并同步至电脑",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = GeekColors.TextMuted,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "选择服务商 (Provider)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                    ),
                    color = GeekColors.TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presetProviders.forEach { (id, label) ->
                        val isSelected = selectedProvider == id
                        val isExisting = existingProviders.any { it.equals(id, ignoreCase = true) }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GeekColors.BrandAccent.copy(alpha = 0.22f) else GeekColors.CardElevated,
                            border = BorderStroke(
                                0.8.dp,
                                if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedProvider = id
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                )
                                if (isExisting && id != "custom") {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(GeekColors.TerminalCyan),
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedProvider == "custom") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customProviderText,
                        onValueChange = { customProviderText = it },
                        placeholder = {
                            Text("例如：deepseek, kimi, minimax...", color = GeekColors.TextMuted, fontSize = 12.sp)
                        },
                        label = { Text("自定义 Provider 标识", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.CardElevated,
                            unfocusedContainerColor = GeekColors.CardElevated,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                            cursorColor = GeekColors.TerminalCyan,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                val apiKeyPlaceholder = when (selectedProvider) {
                    "google" -> "例如：AIzaSy..."
                    "anthropic" -> "例如：sk-ant-..."
                    "openai", "deepseek" -> "例如：sk-..."
                    "groq" -> "例如：gsk_..."
                    "openrouter" -> "例如：sk-or-v1-..."
                    "ollama" -> "本地服务通常无需 Key，可留空"
                    else -> "输入服务商对应 API Key 或 Token 凭据"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "API Key / 认证凭据",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                        ),
                        color = GeekColors.TextMuted,
                    )
                    if (selectedProvider == "ollama") {
                        Text(
                            text = "本地环境可选",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = GeekColors.TerminalCyan,
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    placeholder = {
                        Text(apiKeyPlaceholder, color = GeekColors.TextMuted, fontSize = 12.sp)
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GeekColors.CardElevated,
                        unfocusedContainerColor = GeekColors.CardElevated,
                        focusedBorderColor = GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                        cursorColor = GeekColors.BrandAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (apiKeyText.isNotEmpty()) {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "隐藏" else "显示",
                                        tint = GeekColors.TextMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            IconButton(onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    apiKeyText = clipText.trim()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "粘贴",
                                    tint = GeekColors.TerminalCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )

                // 端点地址配置（折叠或可调整）
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showEndpointConfig = !showEndpointConfig }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (showEndpointConfig) "▼ 收起 Base URL 端点配置" else "▶ 展开自定义 Base URL 端点 (可选)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = GeekColors.TerminalCyan,
                    )
                }

                if (showEndpointConfig) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        placeholder = {
                            Text("例如：https://api.openai.com/v1", color = GeekColors.TextMuted, fontSize = 11.5.sp)
                        },
                        label = { Text("Base URL (API 端点)", fontSize = 11.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.CardElevated,
                            unfocusedContainerColor = GeekColors.CardElevated,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                            cursorColor = GeekColors.TerminalCyan,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )
                }

                // ==================== 模型识别与多选区域 ====================
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "选择要添加的模型",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                            ),
                            color = GeekColors.TextPrimary,
                        )
                        Text(
                            text = "已选 ${selectedModels.size} / 共 ${availableModels.size} 项",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = GeekColors.TextMuted,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = GeekColors.CardElevated,
                            border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable {
                                    if (selectedModels.size == availableModels.size) {
                                        selectedModels.clear()
                                    } else {
                                        selectedModels.clear()
                                        selectedModels.addAll(availableModels)
                                    }
                                },
                        ) {
                            Text(
                                text = if (selectedModels.size == availableModels.size) "全不选" else "全选",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = GeekColors.BrandAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = GeekColors.BrandAccent.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, GeekColors.BrandAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable(enabled = !isDetecting) { detectModels() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isDetecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        strokeWidth = 1.6.dp,
                                        color = GeekColors.BrandAccent,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = GeekColors.BrandAccent,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (isDetecting) "识别中..." else "识别模型",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = GeekColors.BrandAccent,
                                )
                            }
                        }
                    }
                }

                if (!detectStatusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = detectStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = if (detectStatusMessage!!.startsWith("识别成功")) GeekColors.BrandAccent else GeekColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }

                // 手动快速添加模型输入栏
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualModelInput,
                        onValueChange = { manualModelInput = it },
                        placeholder = {
                            Text("手动输入模型ID", color = GeekColors.TextMuted, fontSize = 11.sp)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.CardElevated,
                            unfocusedContainerColor = GeekColors.CardElevated,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                            cursorColor = GeekColors.TerminalCyan,
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            val trimmed = manualModelInput.trim()
                            if (trimmed.isNotBlank()) {
                                if (!availableModels.contains(trimmed)) {
                                    availableModels.add(0, trimmed)
                                }
                                if (!selectedModels.contains(trimmed)) {
                                    selectedModels.add(trimmed)
                                }
                                manualModelInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeekColors.CardElevated,
                            contentColor = GeekColors.TerminalCyan,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, GeekColors.TerminalCyan.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 搜索过滤栏（当模型大于 5 个时显示）
                if (availableModels.size > 5) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = modelFilterQuery,
                        onValueChange = { modelFilterQuery = it },
                        placeholder = {
                            Text("快速过滤模型列表...", color = GeekColors.TextMuted, fontSize = 11.sp)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        trailingIcon = {
                            if (modelFilterQuery.isNotEmpty()) {
                                IconButton(onClick = { modelFilterQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清空",
                                        tint = GeekColors.TextMuted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.CardElevated,
                            unfocusedContainerColor = GeekColors.CardElevated,
                            focusedBorderColor = GeekColors.BorderSubtle,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }

                // 多选列表卡片
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GeekColors.CardElevated.copy(alpha = 0.5f),
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (filteredDisplayModels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (availableModels.isEmpty()) "暂无模型，请点击「识别模型」或手动输入添加" else "无匹配模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            filteredDisplayModels.forEach { modelId ->
                                val isChecked = selectedModels.contains(modelId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) {
                                                selectedModels.remove(modelId)
                                            } else {
                                                selectedModels.add(modelId)
                                            }
                                        }
                                        .background(if (isChecked) GeekColors.BrandAccent.copy(alpha = 0.12f) else Color.Transparent)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (!selectedModels.contains(modelId)) selectedModels.add(modelId)
                                            } else {
                                                selectedModels.remove(modelId)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = GeekColors.BrandAccent,
                                            checkmarkColor = Color.White,
                                            uncheckedColor = GeekColors.TextMuted,
                                        ),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = modelId,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.5.sp,
                                        ),
                                        color = if (isChecked) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GeekColors.CardElevated.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = GeekColors.TerminalCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "确认后将同步保存凭据与所选模型至电脑端 ~/.pi/agent/auth.json 与 models.json，并热刷新模型列表。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                            color = GeekColors.TextMuted,
                        )
                    }
                }
            }
        },
    )
}

private data class PiQuickCommand(
    val command: String,
    val label: String,
)

private val PI_QUICK_COMMANDS = listOf(
    PiQuickCommand("/model", "模型"),
    PiQuickCommand("/login", "新增模型"),
    PiQuickCommand("/reload", "重载"),
    PiQuickCommand("/tree", "会话树"),
    PiQuickCommand("/copy", "复制"),
    PiQuickCommand("/fork", "分叉"),
    PiQuickCommand("/new", "新会话"),
    PiQuickCommand("/resume", "恢复"),
    PiQuickCommand("/btw", "快捷提问"),
    PiQuickCommand("/diff", "差异"),
    PiQuickCommand("/status", "状态"),
    PiQuickCommand("/quit", "退出"),
)

/** One chip above the composer. RPC sessions use model/new-session actions; PTY keeps slash commands. */
private data class ComposerChip(
    val label: String,
    val sub: String? = null,
    val icon: ImageVector? = null,
    val highlight: Boolean = false,
    val activeColor: Color? = null,
    val onClick: () -> Unit,
)

@Composable
private fun AgentWelcomeCard(
    isInitializing: Boolean = false,
    elapsedSeconds: Int = 0,
) {
    val cyanColor = GeekColors.TerminalCyan
    val infiniteTransition = rememberInfiniteTransition(label = "PiBrandPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitRotation",
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(1.dp, cyanColor.copy(alpha = if (isInitializing) 0.25f else 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Central Logo badge with monochromatic cyan glow & precision orbit ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp),
            ) {
                // 1. Subtle, single-tone terminal cyan halo
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale,
                            alpha = pulseAlpha,
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    cyanColor.copy(alpha = 0.30f),
                                    cyanColor.copy(alpha = 0.08f),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                // 2. Ultra-thin single-color cyan orbital ring
                if (isInitializing) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .rotate(orbitRotation)
                            .border(
                                width = 1.2.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        cyanColor.copy(alpha = 0.85f),
                                        cyanColor.copy(alpha = 0.20f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        cyanColor.copy(alpha = 0.85f),
                                    )
                                ),
                                shape = CircleShape,
                            )
                    )
                }

                // 3. Inner glassmorphic core containing the official Pi logo
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GeekColors.CardElevated.copy(alpha = 0.95f),
                    border = BorderStroke(
                        1.dp,
                        cyanColor.copy(alpha = if (isInitializing) 0.35f else 0.18f),
                    ),
                    modifier = Modifier.size(54.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_preset_pi),
                            contentDescription = "Pi Agent",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Crossfade(
                targetState = isInitializing,
                animationSpec = tween(300),
                label = "InitTextCrossfade",
            ) { initializing ->
                if (initializing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "正在初始化 Pi 智能环境...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = (-0.2).sp,
                            ),
                            color = GeekColors.TextPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GeekColors.CardElevated,
                            border = BorderStroke(0.8.dp, cyanColor.copy(alpha = 0.25f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(cyanColor.copy(alpha = pulseAlpha))
                                )
                                Spacer(Modifier.width(8.dp))
                                val stepText = when {
                                    elapsedSeconds >= 3 -> "正在等待宿主机 Pi 响应..."
                                    elapsedSeconds >= 1 -> "正在同步工作区与会话状态..."
                                    else -> "正在建立 RPC 通信管道..."
                                }
                                Text(
                                    text = stepText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = GeekColors.TextSecondary,
                                )
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Pi Agent 智能工作台",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                letterSpacing = (-0.3).sp,
                            ),
                            color = GeekColors.TextPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "深度集成的自主编码与远程终端助手",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = GeekColors.TextMuted,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CapabilityBadge("智能编码")
                            CapabilityBadge("自主工具")
                            CapabilityBadge("终端控制")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GeekColors.CardHighlight.copy(alpha = 0.6f),
        border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = GeekColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun GuiChatInputBar(
    chips: List<ComposerChip>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    agentRunning: Boolean = false,
    onSendSteer: ((String) -> Unit)? = null,
    onSendFollowUp: ((String) -> Unit)? = null,
) {
    val isDark = GeekColors.isDark
    val canSend = inputText.isNotBlank()
    val sendButtonScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "sendScale",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GeekColors.CardSurface.copy(alpha = 0.96f),
        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (chip in chips) {
                        val chipShape = RoundedCornerShape(16.dp)
                        val activeColor = chip.activeColor ?: GeekColors.BrandAccent
                        Surface(
                            shape = chipShape,
                            color = if (chip.highlight) {
                                activeColor.copy(alpha = 0.15f)
                            } else {
                                GeekColors.CardElevated
                            },
                            border = BorderStroke(
                                0.8.dp,
                                if (chip.highlight) activeColor.copy(alpha = 0.8f) else GeekColors.BorderSubtle,
                            ),
                            modifier = Modifier
                                .clip(chipShape)
                                .pressClickEffect(),
                            onClick = chip.onClick,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (chip.icon != null) {
                                    Icon(
                                        imageVector = chip.icon,
                                        contentDescription = null,
                                        tint = if (chip.highlight) activeColor else GeekColors.TextPrimary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = chip.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    ),
                                    color = if (chip.highlight) activeColor else GeekColors.TextPrimary,
                                )
                                if (chip.sub != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = chip.sub,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = GeekColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Main text input & send / steer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = {
                        Text(
                            text = if (agentRunning) "Agent 运行中 · 可发送引导或跟进..." else "向 Pi Agent 发送指令或提问...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeekColors.TextMuted,
                            fontSize = 13.5.sp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GeekColors.CardElevated,
                        unfocusedContainerColor = GeekColors.CardElevated,
                        focusedBorderColor = if (agentRunning) GeekColors.AmberWarn else GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        cursorColor = if (agentRunning) GeekColors.AmberWarn else GeekColors.BrandAccent,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    ),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                if (agentRunning) {
                                    (onSendSteer ?: onSendPrompt).invoke(inputText)
                                } else {
                                    onSendPrompt(inputText)
                                }
                            }
                        }
                    ),
                )

                Spacer(Modifier.width(8.dp))

                if (agentRunning && canSend) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 引导: 上箭头 (同正常发送上箭头，橙色背景)
                        Surface(
                            shape = CircleShape,
                            color = GeekColors.AmberWarn,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .graphicsLayer {
                                    scaleX = sendButtonScale
                                    scaleY = sendButtonScale
                                }
                                .pressClickEffect(),
                            onClick = { onSendSteer?.invoke(inputText) ?: onSendPrompt(inputText) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "引导",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // 跟进: 先横再上箭头 (青色背景)
                        Surface(
                            shape = CircleShape,
                            color = GeekColors.BrandAccent,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .graphicsLayer {
                                    scaleX = sendButtonScale
                                    scaleY = sendButtonScale
                                }
                                .pressClickEffect(),
                            onClick = { onSendFollowUp?.invoke(inputText) ?: onSendPrompt(inputText) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_follow_up_arrow),
                                    contentDescription = "跟进",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                } else {
                    val sendButtonColor = if (canSend) {
                        if (agentRunning) GeekColors.AmberWarn else GeekColors.BrandAccent
                    } else {
                        if (isDark) GeekColors.CardElevated else Color(0xFFE8ECF2)
                    }
                    val sendContentColor = if (canSend) Color.White else (if (isDark) Color(0xFF7A8B9E) else Color(0xFF8B97A8))

                    Surface(
                        shape = CircleShape,
                        color = sendButtonColor,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .graphicsLayer {
                                scaleX = sendButtonScale
                                scaleY = sendButtonScale
                            }
                            .pressClickEffect(),
                        onClick = {
                            if (canSend) {
                                if (agentRunning) {
                                    (onSendSteer ?: onSendPrompt).invoke(inputText)
                                } else {
                                    onSendPrompt(inputText)
                                }
                            }
                        },
                        enabled = canSend,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = if (agentRunning) "引导" else "发送",
                                tint = sendContentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

