package com.piremote.app.ui.agent

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONObject
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.piremote.app.data.SettingsStore
import com.piremote.app.data.PiSessionBrief
import com.piremote.app.data.SessionRelationResolver
import com.piremote.app.data.agent.ExtraAction
import com.piremote.app.data.agent.LocalAgentEngine
import com.piremote.app.data.agent.LocalAgentItem
import com.piremote.app.data.agent.LocalAgentSessionStore
import com.piremote.app.data.agent.SerializedAiSession
import com.piremote.app.ui.GeekColors
import com.piremote.app.ui.GeekSmallButton
import com.piremote.app.ui.PhoneSessionKind
import com.piremote.app.ui.PhoneSessionState
import com.piremote.app.ui.PhoneSessionManager
import com.piremote.app.ui.pressClickEffect
import com.piremote.app.ui.SessionTopNavigationBar
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.piremote.app.data.SessionLineageStore
import com.piremote.app.ui.BranchFoldGuide
import com.piremote.app.ui.ChatOutlineDialog
import com.piremote.app.ui.gui.AssistantMessageView
import com.piremote.app.ui.gui.CopyResponseButton
import com.piremote.app.ui.gui.ForkConfirmDialog
import com.piremote.app.ui.gui.ForkSummaryMode
import com.piremote.app.ui.gui.GuiChatOutlineItem
import com.piremote.app.ui.gui.InteractiveOption
import com.piremote.app.ui.gui.MarkdownContentView
import com.piremote.app.ui.gui.PRESET_PROVIDER_ENDPOINTS
import com.piremote.app.ui.gui.PiModelOption
import com.piremote.app.ui.gui.QuestionOptionBlock
import com.piremote.app.ui.gui.QuestionOptionCard
import com.piremote.app.ui.gui.SystemStatusBadge
import com.piremote.app.ui.gui.SystemStatusMessage
import com.piremote.app.ui.gui.ThinkingAccordionCard
import com.piremote.app.ui.gui.ThinkingBlock
import com.piremote.app.ui.gui.TurnSummaryCard
import com.piremote.app.ui.gui.UserMessage
import com.piremote.app.ui.gui.UserMessageBubble
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import com.piremote.app.ui.LocalGuiFontScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LocalChatRow {
    data class User(val message: LocalAgentItem.User) : LocalChatRow()
    data class Question(val item: LocalAgentItem.Question) : LocalChatRow()
    data class TurnSummary(
        val turnId: String,
        val turnIndex: Int = 0,
        val messageCount: Int,
        val toolCount: Int,
        val entries: List<LocalTurnEntry>,
        val live: Boolean,
    ) : LocalChatRow()
    data class Action(
        val id: String,
        val action: ExtraAction,
        val toolName: String,
    ) : LocalChatRow()
    data class Assistant(val message: LocalAgentItem.Assistant) : LocalChatRow()
    data class SystemNotice(val message: LocalAgentItem.SystemNotice) : LocalChatRow()
}

sealed class LocalTurnEntry {
    data class Thinking(val id: String, val content: String, val isFinished: Boolean) : LocalTurnEntry()
    data class Tool(val toolCall: LocalAgentItem.ToolCall) : LocalTurnEntry()
}

/**
 * Groups items chronologically: execution details (thinking and tools/shell) fold
 * into stage-wise TurnSummaryCard ("执行详情"), while each assistant output is rendered
 * strictly outside the card in the natural message stream, in exact temporal sequence.
 */
private fun buildLocalChatRows(items: List<LocalAgentItem>, isRunning: Boolean): List<LocalChatRow> {
    val rows = mutableListOf<LocalChatRow>()
    var currentTurnId: String? = null
    var turnIndex = 0
    val currentRunEntries = mutableListOf<LocalTurnEntry>()

    val lastUserId = items.filterIsInstance<LocalAgentItem.User>().lastOrNull()?.id

    fun flushExecution() {
        if (currentRunEntries.isEmpty()) return
        val toolCount = currentRunEntries.count { it is LocalTurnEntry.Tool }
        val thinkingCount = currentRunEntries.count { it is LocalTurnEntry.Thinking }
        val isLastTurn = currentTurnId != null && currentTurnId == lastUserId
        val live = (isRunning && isLastTurn) || currentRunEntries.any {
            it is LocalTurnEntry.Tool && !it.toolCall.isFinished
        } || currentRunEntries.any {
            it is LocalTurnEntry.Thinking && !it.isFinished
        }

        val actionsToPromote = currentRunEntries
            .filterIsInstance<LocalTurnEntry.Tool>()
            .mapNotNull { toolEntry ->
                toolEntry.toolCall.extraAction?.let { action ->
                    LocalChatRow.Action(
                        id = "action_${toolEntry.toolCall.id}",
                        action = action,
                        toolName = toolEntry.toolCall.toolName,
                    )
                }
            }

        rows.add(
            LocalChatRow.TurnSummary(
                turnId = currentTurnId ?: "turn",
                turnIndex = turnIndex++,
                messageCount = thinkingCount,
                toolCount = toolCount,
                entries = currentRunEntries.toList(),
                live = live,
            )
        )
        // Promote action buttons outside the fold so users can directly tap them without expanding details
        rows.addAll(actionsToPromote)
        currentRunEntries.clear()
    }

    for (item in items) {
        when (item) {
            is LocalAgentItem.User -> {
                flushExecution()
                currentTurnId = item.id
                turnIndex = 0
                rows.add(LocalChatRow.User(item))
            }

            is LocalAgentItem.Question -> {
                flushExecution()
                rows.add(LocalChatRow.Question(item))
            }

            is LocalAgentItem.ToolCall -> {
                currentRunEntries.add(LocalTurnEntry.Tool(item))
            }

            is LocalAgentItem.SystemNotice -> {
                flushExecution()
                rows.add(LocalChatRow.SystemNotice(item))
            }

            is LocalAgentItem.Assistant -> {
                if (!item.thinking.isNullOrBlank()) {
                    currentRunEntries.add(
                        LocalTurnEntry.Thinking(
                            id = "${item.id}_thinking",
                            content = item.thinking ?: "",
                            isFinished = !item.isStreaming,
                        )
                    )
                }
                if (item.text.isNotBlank()) {
                    flushExecution()
                    rows.add(LocalChatRow.Assistant(item))
                }
            }
        }
    }
    flushExecution()
    return rows
}

internal val RECOMMENDED_PROVIDER_MODELS = listOf(
    PiModelOption("deepseek", "deepseek-chat", "DeepSeek-V3"),
    PiModelOption("deepseek", "deepseek-reasoner", "DeepSeek-R1"),
    PiModelOption("siliconflow", "deepseek-ai/DeepSeek-V3", "DeepSeek-V3 (硅基)"),
    PiModelOption("siliconflow", "deepseek-ai/DeepSeek-R1", "DeepSeek-R1 (硅基)"),
    PiModelOption("siliconflow", "Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B"),
    PiModelOption("amd_radeon", "DeepSeek-V4-Flash-0731", "DeepSeek V4 Flash"),
    PiModelOption("amd_radeon", "deepseek-ai/DeepSeek-V3", "DeepSeek V3 (AMD)"),
    PiModelOption("openai", "gpt-4o", "GPT-4o"),
    PiModelOption("openai", "gpt-4o-mini", "GPT-4o Mini"),
    PiModelOption("openai", "o1", "o1"),
    PiModelOption("openai", "o3-mini", "o3-mini"),
    PiModelOption("anthropic", "claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet"),
    PiModelOption("anthropic", "claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
    PiModelOption("google", "gemini-2.5-flash", "Gemini 2.5 Flash"),
    PiModelOption("google", "gemini-2.0-flash", "Gemini 2.0 Flash"),
    PiModelOption("openrouter", "anthropic/claude-3.7-sonnet", "Claude 3.7 (OpenRouter)"),
    PiModelOption("openrouter", "deepseek/deepseek-r1", "DeepSeek R1 (OpenRouter)"),
    PiModelOption("ollama", "qwen2.5-coder", "Qwen 2.5 Coder"),
)

/**
 * Fullscreen Interactive Workspace for the 100% Native Local AI Agent.
 * Runs completely on the phone without Termux, external binaries, or PC connection.
 */
@Composable
fun LocalAgentChatScreen(
    session: PhoneSessionState? = null,
    manager: PhoneSessionManager? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val engine = session?.engine ?: remember { LocalAgentEngine(context) }

    val items by engine.items.collectAsState()
    val isRunning by engine.isRunning.collectAsState()
    val statusMessage by engine.statusMessage.collectAsState()
    val activeWebPreview by engine.activeWebPreview.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showAddLoginModelDialog by remember { mutableStateOf(false) }
    var outlineForkTarget by remember { mutableStateOf<GuiChatOutlineItem?>(null) }
    var forkConfirmTarget by remember { mutableStateOf<UserMessage?>(null) }
    var modelPickerRefreshTrigger by remember { mutableIntStateOf(0) }
    var currentModel by remember {
        mutableStateOf(session?.modelName?.takeIf { it.isNotBlank() } ?: settings.localAgentModel.ifBlank { "deepseek-chat" })
    }
    var showSessionPicker by remember { mutableStateOf(false) }
    var showChatOutline by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var previewUrlToOpen by remember { mutableStateOf<Pair<String, String>?>(null) }

    val allKnownModels = remember(modelPickerRefreshTrigger, settings.localAgentProviderId) {
        val list = mutableListOf<PiModelOption>()
        val deletedKeys = settings.getLocalAgentDeletedModels()

        // 仅当用户为服务商配置了有效的 API Key（或 Ollama 本地端点）时，才载入对应推荐模型
        for (m in RECOMMENDED_PROVIDER_MODELS) {
            val key = settings.getProviderApiKey(m.provider)
            val isConfigured = key.isNotBlank() || (m.provider == "ollama" && settings.localAgentProviderId == "ollama" && settings.getProviderEndpoint("ollama").isNotBlank())
            val isDeleted = deletedKeys.contains(m.id.lowercase()) || deletedKeys.contains("${m.provider}/${m.id}".lowercase())
            if (isConfigured && !isDeleted) {
                list.add(m)
            }
        }

        // 用户自定义添加并保存的模型（同样仅保留已配置凭据且未被删除的模型）
        for ((prov, id) in settings.getLocalAgentCustomModels()) {
            val key = settings.getProviderApiKey(prov)
            val isConfigured = key.isNotBlank() || prov.equals("ollama", ignoreCase = true)
            val isDeleted = deletedKeys.contains(id.lowercase()) || deletedKeys.contains("${prov}/$id".lowercase())
            if (isConfigured && !isDeleted && list.none { it.provider.equals(prov, ignoreCase = true) && it.id == id }) {
                list.add(PiModelOption(prov, id, id))
            }
        }

        // 若当前模型被删除或不在列表中，不要强制将其塞入 list
        if (currentModel.isNotBlank()) {
            val prov = settings.localAgentProviderId.ifBlank { "deepseek" }
            val hasKey = settings.getProviderApiKey(prov).isNotBlank()
            val isDeleted = deletedKeys.contains(currentModel.lowercase()) || deletedKeys.contains("${prov}/$currentModel".lowercase())
            if (hasKey && !isDeleted && list.none { it.id == currentModel }) {
                list.add(0, PiModelOption(prov, currentModel, currentModel))
            }
        }
        list
    }

    fun forkAiSessionFromNode(targetId: String, mode: ForkSummaryMode, customPrompt: String?) {
        val curSession = session ?: return
        val curItems = engine.getItems()
        val targetIndex = curItems.indexOfFirst { it.id == targetId }
        val effectiveIndex = if (targetIndex >= 0) targetIndex else curItems.lastIndex
        if (effectiveIndex < 0) {
            Toast.makeText(context, "当前会话无节点可分叉", Toast.LENGTH_SHORT).show()
            return
        }

        val forkedItems = curItems.take(effectiveIndex + 1)
        val history = engine.getConversationHistory()
        val forkedHistory = mutableListOf<JSONObject>()
        val systemMsg = history.firstOrNull { it.optString("role") == "system" }
        if (systemMsg != null) forkedHistory.add(systemMsg)

        val userCount = forkedItems.count { it is LocalAgentItem.User }
        var currentUserIndex = 0
        for (msg in history) {
            if (msg.optString("role") == "system") continue
            if (msg.optString("role") == "user") {
                currentUserIndex++
                if (currentUserIndex > userCount) break
            }
            forkedHistory.add(msg)
        }

        val baseTitle = curSession.title.removeSuffix(" (分叉)").take(16)
        val newTitle = "$baseTitle (分叉)"

        if (manager != null) {
            val newId = manager.createAiSession(
                title = newTitle,
                workingDir = curSession.workingDir,
            )
            val newSessionState = manager.sessions.firstOrNull { it.id == newId }
            newSessionState?.modelName = curSession.modelName
            newSessionState?.engine?.restoreSession(forkedItems, forkedHistory)
            if (newSessionState != null) {
                manager.saveAiSession(newSessionState)
            }

            SessionLineageStore(context).recordFork(childKey = newId, parentKey = curSession.id)
            manager.openSession(newId)

            if (mode == ForkSummaryMode.SUMMARIZE || mode == ForkSummaryMode.CUSTOM_PROMPT) {
                val promptText = if (mode == ForkSummaryMode.SUMMARIZE) {
                    "请对我们刚才离开的分支中所做的探索、尝试、关键发现与代码修改进行简明总结，提炼核心结论与经验教训，作为我们在当前分支继续展开工作的背景上下文。"
                } else {
                    "关于我们刚才离开的分支：${customPrompt?.trim().orEmpty()}\n请按此要求提炼该分支的关键经验与结论，作为我们在当前分支继续展开工作的背景上下文。"
                }
                newSessionState?.engine?.sendPrompt(promptText, scope)
            }
            Toast.makeText(context, "分叉成功，已进入新分支", Toast.LENGTH_SHORT).show()
        }
    }

    val initialChatRows = remember { buildLocalChatRows(items, isRunning) }
    val initialBottomIndex = remember(session?.id) {
        if (initialChatRows.isNotEmpty()) initialChatRows.size - 1 else 0
    }
    val listState = remember(session?.id) {
        LazyListState(firstVisibleItemIndex = initialBottomIndex)
    }
    var isBottomSynced by remember(session?.id) {
        mutableStateOf(initialChatRows.isNotEmpty())
    }

    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 2
        }
    }

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

    val density = LocalDensity.current
    val topThresholdPx = with(density) { 36.dp.toPx() }
    var lazyListCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

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

    val chatOutline = remember(items) {
        val outline = mutableListOf<GuiChatOutlineItem>()
        for (item in items) {
            when (item) {
                is LocalAgentItem.User -> {
                    val preview = item.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "用户输入"
                    outline.add(
                        GuiChatOutlineItem(
                            id = item.id,
                            tag = "用户",
                            label = preview.take(50),
                            turnStart = true,
                        )
                    )
                }
                is LocalAgentItem.Assistant -> {
                    val thinking = item.thinking
                    if (!thinking.isNullOrBlank()) {
                        val preview = thinking.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "思考过程"
                        outline.add(
                            GuiChatOutlineItem(
                                id = item.id + "_thinking",
                                tag = "思考",
                                label = preview.take(50),
                                turnStart = false,
                            )
                        )
                    }
                    if (item.text.isNotBlank()) {
                        val preview = item.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Agent 回复"
                        outline.add(
                            GuiChatOutlineItem(
                                id = item.id,
                                tag = "Agent",
                                label = preview.take(50),
                                turnStart = false,
                            )
                        )
                    }
                }
                is LocalAgentItem.ToolCall -> {
                    val preview = "${item.toolName} ${item.inputArgs.replace('\n', ' ').trim()}".trim()
                    outline.add(
                        GuiChatOutlineItem(
                            id = item.id,
                            tag = "工具",
                            label = preview.take(50),
                            turnStart = false,
                        )
                    )
                }
                is LocalAgentItem.SystemNotice -> {
                    val preview = item.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "系统提示"
                    outline.add(
                        GuiChatOutlineItem(
                            id = item.id,
                            tag = "系统",
                            label = preview.take(50),
                            turnStart = false,
                        )
                    )
                }
                is LocalAgentItem.Question -> {
                    val preview = item.question.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "交互提问"
                    outline.add(
                        GuiChatOutlineItem(
                            id = item.id,
                            tag = "提问",
                            label = preview.take(50),
                            turnStart = false,
                        )
                    )
                }
            }
        }
        outline
    }

    val expandedTurns = remember { mutableStateMapOf<String, Boolean>() }
    val chatRows = remember(items, isRunning) { buildLocalChatRows(items, isRunning) }

    // Auto-scroll to bottom when new messages or tool cards arrive
    LaunchedEffect(chatRows.size, isRunning) {
        if (chatRows.isNotEmpty()) {
            if (!isBottomSynced) {
                listState.scrollToItem(chatRows.size - 1)
                isBottomSynced = true
            } else if (nearBottom) {
                listState.scrollToItem(chatRows.size - 1)
            }
        }
    }

    // Auto-prompt to configure if key is missing on first launch
    LaunchedEffect(Unit) {
        if (settings.localAgentApiKey.isBlank()) {
            showAddLoginModelDialog = true
        }
    }

    Scaffold(
        topBar = {
            val currentProviderId = allKnownModels.find { it.id == currentModel }?.provider ?: settings.localAgentProviderId
            val currentProviderLabel = PHONE_PROVIDER_PRESETS.find { it.id.equals(currentProviderId, ignoreCase = true) }?.name ?: currentProviderId
            SessionTopNavigationBar(
                title = session?.title ?: "本地 AI 工作台",
                sessionId = session?.id ?: "",
                isGuiMode = true,
                isRpcSession = true,
                modelProvider = if (settings.isLocalAgentConfigured && allKnownModels.isNotEmpty()) currentProviderLabel else null,
                onBack = onBack,
                onShowOutline = { showChatOutline = true },
                onSwitchSession = { showSessionPicker = true },
                modeSubtitle = if (settings.isLocalAgentConfigured && allKnownModels.isNotEmpty()) null else "待配置服务商",
                onOpenAgentConfig = null,
                onRenameTitle = {
                    val sid = session?.id ?: ""
                    val currTitle = session?.title ?: "本地 AI 工作台"
                    sessionToRename = sid to currTitle
                },
            )
        },
        containerColor = GeekColors.DeepCanvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding(),
        ) {
            val fontScale = LocalGuiFontScale.current
            val currentDensity = LocalDensity.current
            val scaledDensity = remember(currentDensity, fontScale) {
                Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * fontScale,
                )
            }

            val safeUriHandler = remember(context) {
                object : UriHandler {
                    override fun openUri(uri: String) {
                        val isLocalPreview = uri.contains("127.0.0.1:8765") || uri.contains("localhost:8765")
                        if (isLocalPreview) {
                            previewUrlToOpen = Pair(uri, "")
                            return
                        }
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
                // Chat Messages List
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (items.isEmpty()) {
                        LocalAgentWelcomeView(
                            workspacePath = engine.tools.getWorkspace().absolutePath,
                            onQuickPrompt = { prompt ->
                                inputText = prompt
                                if (session != null) {
                                    session.lastActiveTime = System.currentTimeMillis()
                                    if (session.title.startsWith("AI 创作") || session.title == "本地 AI 工作台") {
                                        val cleanPrompt = prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: prompt
                                        val candidate = cleanPrompt.take(16)
                                        if (candidate.isNotBlank()) {
                                            session.title = candidate
                                        }
                                    }
                                }
                                engine.sendPrompt(prompt, scope)
                                if (session != null) {
                                    manager?.saveAiSession(session)
                                }
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { lazyListCoordinates = it }
                                .graphicsLayer {
                                    alpha = if (chatRows.isEmpty() || isBottomSynced) 1f else 0f
                                },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(
                                items = chatRows,
                                key = { _, row ->
                                    when (row) {
                                        is LocalChatRow.User -> row.message.id
                                        is LocalChatRow.Question -> row.item.id
                                        is LocalChatRow.TurnSummary -> "turn_${row.turnId}_${row.turnIndex}"
                                        is LocalChatRow.Action -> row.id
                                        is LocalChatRow.Assistant -> row.message.id
                                        is LocalChatRow.SystemNotice -> row.message.id
                                    }
                                }
                            ) { rowIndex, row ->
                                when (row) {
                                    is LocalChatRow.User -> {
                                        UserMessageBubble(
                                            message = UserMessage(
                                                id = row.message.id,
                                                text = row.message.text,
                                                timestamp = row.message.timestamp,
                                                model = row.message.model ?: currentModel,
                                                provider = row.message.provider ?: settings.localAgentProviderId,
                                                sendType = row.message.sendType,
                                            ),
                                            onCollapsed = {
                                                scrollToRowIfHidden(rowIndex)
                                            },
                                            onCollapsedWithCoords = { coords ->
                                                scrollToCoordinatesIfHidden(coords)
                                            },
                                            onEdit = { text ->
                                                inputText = text
                                                Toast.makeText(context, "已填入输入框，可修改后重新发送", Toast.LENGTH_SHORT).show()
                                            },
                                            onFork = { userMsg ->
                                                forkConfirmTarget = userMsg
                                            },
                                            onNewSession = { userMsg ->
                                                if (manager != null) {
                                                    val newId = manager.openNewAiDraftSession()
                                                    manager.openSession(newId)
                                                } else {
                                                    engine.clearSession()
                                                }
                                                inputText = userMsg.text
                                                Toast.makeText(context, "已开启新会话并填入内容", Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }

                                    is LocalChatRow.Question -> {
                                        val block = remember(row.item.id, row.item.selectedAnswer, row.item.isAnswered) {
                                            QuestionOptionBlock(
                                                id = row.item.id,
                                                timestamp = row.item.timestamp,
                                                question = row.item.question,
                                                options = row.item.options.mapIndexed { idx, opt ->
                                                    InteractiveOption(key = (idx + 1).toString(), label = opt)
                                                },
                                                selectedKey = row.item.selectedAnswer,
                                                selectedKeys = row.item.selectedAnswer?.split(", ", ",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                                                isAnswered = row.item.isAnswered,
                                                requestId = row.item.requestId,
                                                method = if (row.item.isMultiSelect) "multi_select" else "select",
                                                isMultiSelect = row.item.isMultiSelect,
                                                allowCustomInput = row.item.allowCustomInput,
                                            )
                                        }
                                        QuestionOptionCard(
                                            item = block,
                                            onSelectOption = { opt ->
                                                engine.answerQuestion(row.item.requestId, opt.label)
                                            },
                                            onSelectMultiple = { selectedOpts, customText ->
                                                val answer = buildList {
                                                    addAll(selectedOpts.map { it.label })
                                                    if (!customText.isNullOrBlank()) add(customText)
                                                }.joinToString(", ")
                                                engine.answerQuestion(row.item.requestId, answer)
                                            },
                                            onSubmitText = { text ->
                                                engine.answerQuestion(row.item.requestId, text)
                                            },
                                            onConfirmAnswer = { confirmed ->
                                                engine.answerQuestion(row.item.requestId, if (confirmed) "是" else "否")
                                            },
                                        )
                                    }

                                    is LocalChatRow.TurnSummary -> {
                                        val turnKey = "${row.turnId}_${row.turnIndex}"
                                        val isTurnExpanded = expandedTurns[turnKey] ?: (row.live || (rowIndex == chatRows.lastIndex && row.entries.isNotEmpty()))
                                        TurnSummaryCard(
                                            turnId = turnKey,
                                            messageCount = row.messageCount,
                                            toolCount = row.toolCount,
                                            live = row.live,
                                            expanded = isTurnExpanded,
                                            onToggle = {
                                                expandedTurns[turnKey] = !isTurnExpanded
                                            },
                                            onToggleWithCoords = { coords ->
                                                if (isTurnExpanded) {
                                                    scrollToCoordinatesIfHidden(coords)
                                                }
                                            },
                                        ) {
                                            row.entries.forEach { entry ->
                                                when (entry) {
                                                    is LocalTurnEntry.Thinking -> {
                                                        ThinkingAccordionCard(
                                                            block = ThinkingBlock(
                                                                id = entry.id,
                                                                content = entry.content,
                                                                isFinished = entry.isFinished,
                                                            ),
                                                            onCollapsedWithCoords = { coords ->
                                                                scrollToCoordinatesIfHidden(coords)
                                                            },
                                                        )
                                                    }

                                                    is LocalTurnEntry.Tool -> {
                                                        LocalAgentToolExecutionCard(
                                                            toolCall = entry.toolCall,
                                                            onOpenWebPreview = { url, path ->
                                                                previewUrlToOpen = Pair(url, path)
                                                            },
                                                            onOpenOfficeDocument = { path, mimeType ->
                                                                val opened = engine.tools.openFileInSystem(path, mimeType)
                                                                if (!opened) {
                                                                    Toast.makeText(context, "无法打开文档，请安装 WPS Office 或其他查看器", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            onCollapsedWithCoords = { coords ->
                                                                scrollToCoordinatesIfHidden(coords)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is LocalChatRow.Action -> {
                                        LocalAgentArtifactActionCard(
                                            item = row,
                                            onOpenWebPreview = { url, path ->
                                                previewUrlToOpen = Pair(url, path)
                                            },
                                            onOpenOfficeDocument = { path, mimeType ->
                                                val opened = engine.tools.openFileInSystem(path, mimeType)
                                                if (!opened) {
                                                    Toast.makeText(context, "无法打开文档，请安装 WPS Office 或其他查看器", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        )
                                    }

                                    is LocalChatRow.Assistant -> {
                                        AssistantMessageView(text = row.message.text)
                                    }

                                    is LocalChatRow.SystemNotice -> {
                                        SystemStatusBadge(
                                            message = SystemStatusMessage(
                                                id = row.message.id,
                                                text = row.message.text,
                                                timestamp = row.message.timestamp,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Running status capsule
                    RunningStatusCapsule(
                        visible = isRunning,
                        statusMessage = statusMessage,
                    )
                }
            }

            // Bottom Input Bar (with integrated current model chip & full-duplex steering)
            LocalAgentInputBar(
                inputText = inputText,
                isRunning = isRunning,
                currentModel = if (settings.isLocalAgentConfigured && allKnownModels.isNotEmpty()) currentModel else "配置模型与服务商",
                onModelClick = {
                    if (allKnownModels.isEmpty() || !settings.isLocalAgentConfigured) {
                        showAddLoginModelDialog = true
                    } else {
                        showModelPicker = true
                    }
                },
                onNewSessionClick = {
                    if (manager != null) {
                        val currentItems = engine.getItems()
                        if (currentItems.isEmpty() && !engine.isRunning.value) {
                            Toast.makeText(context, "当前已是新会话", Toast.LENGTH_SHORT).show()
                        } else {
                            val newId = manager.openNewAiDraftSession()
                            manager.openSession(newId)
                        }
                    } else {
                        engine.clearSession()
                    }
                },
                onTextChange = { inputText = it },
                onSend = {
                    val prompt = inputText
                    inputText = ""
                    if (session != null) {
                        session.lastActiveTime = System.currentTimeMillis()
                        // Auto-name session if title is default
                        if (session.title.startsWith("AI 创作") || session.title == "本地 AI 工作台") {
                            val cleanPrompt = prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: prompt
                            val candidate = cleanPrompt.take(16)
                            if (candidate.isNotBlank()) {
                                session.title = candidate
                            }
                        }
                    }
                    engine.sendPrompt(prompt, scope)
                    if (session != null) {
                        manager?.saveAiSession(session)
                    }
                },
                onSendSteer = {
                    val steer = inputText
                    inputText = ""
                    engine.sendSteer(steer, scope)
                },
                onSendFollowUp = {
                    val followUp = inputText
                    inputText = ""
                    engine.sendFollowUp(followUp, scope)
                },
                onStop = {
                    engine.stop()
                    Toast.makeText(context, "已停止生成", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // Modal Web Preview Sheet
    previewUrlToOpen?.let { (url, path) ->
        WebPreviewSheet(
            url = url,
            filePath = path,
            onDismiss = { previewUrlToOpen = null },
        )
    }

    // Auto-pop web preview when triggered by agent
    activeWebPreview?.let { (url, path) ->
        WebPreviewSheet(
            url = url,
            filePath = path,
            onDismiss = { engine.closeWebPreview() },
        )
    }

    // Native Mobile Model Picker Dialog
    if (showModelPicker) {
        LocalAgentModelPickerDialog(
            currentModelKey = currentModel,
            currentModelLabel = allKnownModels.find { it.id == currentModel }?.cleanLabel ?: currentModel,
            currentModelProvider = settings.localAgentProviderId,
            models = allKnownModels,
            onPickModel = { option ->
                currentModel = option.id
                val endpoint = settings.getProviderEndpoint(option.provider).ifBlank {
                    PRESET_PROVIDER_ENDPOINTS[option.provider] ?: settings.localAgentEndpoint
                }
                settings.switchToProvider(option.provider, endpoint, option.id)
                if (session != null) {
                    session.modelName = option.id
                    manager?.saveAiSession(session)
                }
                showModelPicker = false
                Toast.makeText(context, "已切换模型: ${option.cleanLabel}", Toast.LENGTH_SHORT).show()
            },
            onAddNewModel = {
                showModelPicker = false
                showAddLoginModelDialog = true
            },
            onDeleteModel = { option ->
                settings.addLocalAgentDeletedModel(option.provider, option.id)
                if (currentModel == option.id) {
                    val remaining = allKnownModels.filterNot { it.id == option.id }
                    if (remaining.isNotEmpty()) {
                        val fallback = remaining.first()
                        currentModel = fallback.id
                        val fallbackEndpoint = settings.getProviderEndpoint(fallback.provider).ifBlank {
                            PRESET_PROVIDER_ENDPOINTS[fallback.provider] ?: settings.localAgentEndpoint
                        }
                        settings.switchToProvider(fallback.provider, fallbackEndpoint, fallback.id)
                        if (session != null) {
                            session.modelName = fallback.id
                            manager?.saveAiSession(session)
                        }
                    } else {
                        currentModel = ""
                        settings.localAgentModel = ""
                    }
                }
                modelPickerRefreshTrigger++
                Toast.makeText(context, "已从本地移除模型「${option.cleanLabel}」", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showModelPicker = false },
        )
    }

    // Native Mobile Configure Provider and Endpoint Dialog
    if (showAddLoginModelDialog) {
        LocalAgentProviderConfigDialog(
            settings = settings,
            onConfirm = { provider, apiKey, baseUrl, selectedModels ->
                showAddLoginModelDialog = false
                settings.setProviderApiKey(provider, apiKey)
                val endpoint = baseUrl?.takeIf { it.isNotBlank() } ?: PRESET_PROVIDER_ENDPOINTS[provider].orEmpty()
                if (endpoint.isNotBlank()) {
                    settings.setProviderEndpoint(provider, endpoint)
                }
                // 恢复已选模型的已删除标记
                for (m in selectedModels) {
                    settings.restoreLocalAgentDeletedModel(provider, m)
                }
                if (selectedModels.isNotEmpty()) {
                    settings.addLocalAgentCustomModels(provider, selectedModels)
                    val firstModel = selectedModels.first()
                    currentModel = firstModel
                    settings.switchToProvider(provider, endpoint, firstModel)
                    if (session != null) {
                        session.modelName = firstModel
                        manager?.saveAiSession(session)
                    }
                } else {
                    settings.switchToProvider(provider, endpoint)
                }
                modelPickerRefreshTrigger++
                Toast.makeText(context, "已成功配置服务商「$provider」", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAddLoginModelDialog = false },
        )
    }

    // Historical Sessions Picker Dialog
    if (showSessionPicker) {
        LocalAgentSessionPickerDialog(
            currentSessionId = session?.id,
            currentWorkingDir = session?.workingDir ?: settings.localAgentWorkspace.ifBlank { context.filesDir.absolutePath },
            manager = manager,
            onPickSession = { pickedId ->
                showSessionPicker = false
                if (manager != null) {
                    manager.openSession(pickedId)
                } else {
                    val store = LocalAgentSessionStore(context)
                    val target = store.loadAllSessions().find { it.id == pickedId }
                    if (target != null) {
                        engine.restoreSession(target.items, target.conversationHistory)
                    }
                }
            },
            onRenameSession = { sid, currentTitle ->
                sessionToRename = sid to currentTitle
            },
            onNewSession = {
                showSessionPicker = false
                if (manager != null) {
                    val currentItems = engine.getItems()
                    if (currentItems.isEmpty() && !engine.isRunning.value) {
                        Toast.makeText(context, "当前已是新会话", Toast.LENGTH_SHORT).show()
                    } else {
                        val newId = manager.openNewAiDraftSession()
                        manager.openSession(newId)
                    }
                } else {
                    engine.clearSession()
                }
            },
            onDismiss = { showSessionPicker = false },
        )
    }

    // Chat Turn Outline & Session Tree Roaming Dialog (styled exactly like PC Terminal)
    if (showChatOutline) {
        ChatOutlineDialog(
            items = chatOutline,
            onPick = { pickedItem ->
                showChatOutline = false
                val targetIndex = chatRows.indexOfFirst { row ->
                    when (row) {
                        is LocalChatRow.User -> row.message.id == pickedItem.id
                        is LocalChatRow.Question -> row.item.id == pickedItem.id
                        is LocalChatRow.TurnSummary -> row.turnId == pickedItem.id || row.entries.any {
                            when (it) {
                                is LocalTurnEntry.Thinking -> pickedItem.id.startsWith(it.id) || it.id.startsWith(pickedItem.id)
                                is LocalTurnEntry.Tool -> it.toolCall.id == pickedItem.id
                            }
                        }
                        is LocalChatRow.Action -> row.id.contains(pickedItem.id)
                        is LocalChatRow.Assistant -> row.message.id == pickedItem.id || pickedItem.id.startsWith(row.message.id)
                        is LocalChatRow.SystemNotice -> row.message.id == pickedItem.id
                    }
                }
                if (targetIndex >= 0) {
                    val targetRow = chatRows[targetIndex]
                    if (targetRow is LocalChatRow.TurnSummary) {
                        expandedTurns["${targetRow.turnId}_${targetRow.turnIndex}"] = true
                    }
                    scope.launch {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            },
            onFork = { item ->
                showChatOutline = false
                outlineForkTarget = item
            },
            onDismiss = { showChatOutline = false },
        )
    }

    // Fork confirmation dialog from outline node
    outlineForkTarget?.let { item ->
        ForkConfirmDialog(
            previewText = item.label,
            onConfirmWithSummary = { mode, customPrompt ->
                forkAiSessionFromNode(item.id, mode, customPrompt)
                outlineForkTarget = null
            },
            onDismiss = { outlineForkTarget = null },
        )
    }

    // Fork confirmation dialog from user bubble
    forkConfirmTarget?.let { target ->
        ForkConfirmDialog(
            previewText = target.text,
            onConfirmWithSummary = { mode, customPrompt ->
                forkAiSessionFromNode(target.id, mode, customPrompt)
                forkConfirmTarget = null
            },
            onDismiss = { forkConfirmTarget = null },
        )
    }

    // Session Rename Dialog
    sessionToRename?.let { (targetId, initialName) ->
        var newName by remember { mutableStateOf(initialName) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Text(
                    text = "重命名会话",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextPrimary,
                )
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("输入会话名称", color = GeekColors.TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank()) {
                            if (manager != null) {
                                val s = manager.sessions.find { it.id == targetId }
                                if (s != null) {
                                    s.title = trimmed
                                    manager.saveAiSession(s)
                                }
                            } else {
                                val store = LocalAgentSessionStore(context)
                                val list = store.loadAllSessions()
                                val target = list.find { it.id == targetId }
                                if (target != null) {
                                    store.saveSession(target.copy(title = trimmed))
                                }
                            }
                            if (session?.id == targetId) {
                                session.title = trimmed
                            }
                        }
                        sessionToRename = null
                    }
                ) {
                    Text("保存", color = GeekColors.BrandAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("取消", color = GeekColors.TextMuted)
                }
            },
        )
    }
}



/**
 * Historical AI conversations dialog with Segmented Switcher, tree group branches, and new session action.
 * Faithful clone of PC Terminal's PiSessionPickerDialog and SessionPickerRow.
 */
@Composable
private fun LocalAgentSessionPickerDialog(
    currentSessionId: String?,
    currentWorkingDir: String,
    manager: PhoneSessionManager?,
    onPickSession: (String) -> Unit,
    onRenameSession: ((String, String) -> Unit)? = null,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var diskRefreshTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val isDark = GeekColors.isDark

    val animatedOffset = remember { Animatable(0f) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    val currentFraction = if (isDragging) dragProgress else animatedOffset.value
    var sessionToDelete by remember { mutableStateOf<PiSessionBrief?>(null) }

    val draggableState = rememberDraggableState { deltaPx ->
        if (containerWidthPx > 0f) {
            val deltaFraction = -deltaPx / containerWidthPx
            val raw = dragProgress + deltaFraction
            dragProgress = when {
                raw < 0f -> raw * 0.35f
                raw > 1f -> 1f + (raw - 1f) * 0.35f
                else -> raw
            }
        }
    }

    fun normPath(p: String?): String =
        p?.trim()?.replace('\\', '/')?.trimEnd('/')?.lowercase() ?: ""

    val lineageMap = remember(diskRefreshTrigger) {
        SessionLineageStore(context).getLineageMap()
    }

    val sessions = remember(manager?.sessions?.toList(), diskRefreshTrigger, lineageMap) {
        val list = mutableListOf<PiSessionBrief>()
        val seenIds = mutableSetOf<String>()

        if (manager != null) {
            val aiList = manager.sessions.filter { it.kind == PhoneSessionKind.AI_AGENT }
            for (s in aiList) {
                val items = s.engine?.getItems() ?: emptyList()
                if (items.isEmpty() && s.id != currentSessionId) continue
                seenIds.add(s.id)
                val userPrompt = items.firstOrNull { it is LocalAgentItem.User }?.let { (it as LocalAgentItem.User).text.replace('\n', ' ').trim() }
                list.add(
                    PiSessionBrief(
                        file = s.id,
                        id = s.id,
                        cwd = s.workingDir,
                        name = s.title.ifBlank { null },
                        preview = userPrompt,
                        timestamp = formatIsoTimestamp(s.lastActiveTime),
                        messageCount = items.size,
                        parentSession = lineageMap[s.id],
                    )
                )
            }
        }

        val store = LocalAgentSessionStore(context)
        val fromDisk = store.loadAllSessions()
        for (saved in fromDisk) {
            if (!seenIds.contains(saved.id)) {
                seenIds.add(saved.id)
                val userPrompt = saved.items.firstOrNull { it is LocalAgentItem.User }?.let { (it as LocalAgentItem.User).text.replace('\n', ' ').trim() }
                list.add(
                    PiSessionBrief(
                        file = saved.id,
                        id = saved.id,
                        cwd = saved.workingDir,
                        name = saved.title.ifBlank { null },
                        preview = userPrompt,
                        timestamp = formatIsoTimestamp(saved.lastActiveTime),
                        messageCount = saved.items.size,
                        parentSession = lineageMap[saved.id],
                    )
                )
            }
        }

        list.sortedByDescending { it.timestamp.orEmpty() }
    }

    val normCwd = normPath(currentWorkingDir)
    val scoped = remember(sessions, normCwd) {
        sessions.filter {
            val sCwd = normPath(it.cwd)
            sCwd.isNotEmpty() && (sCwd == normCwd || (normCwd.isNotEmpty() && sCwd.endsWith(normCwd)))
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
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "切换会话",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = GeekColors.TextPrimary,
                        )
                        val totalDirs = sessions.mapNotNull { it.cwd?.trim()?.replace('\\', '/')?.trimEnd('/')?.lowercase() }.distinct().size
                        if (sessions.isNotEmpty()) {
                            Text(
                                text = "共扫描到 $totalDirs 个目录 · ${sessions.size} 个会话",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = GeekColors.TextMuted,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressClickEffect(),
                        onClick = onNewSession,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建会话",
                                tint = GeekColors.BrandAccent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "新建",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.BrandAccent,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Authentic iOS Segmented Switcher: [当前目录] vs [全部会话] (synchronized with swipe fraction)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) GeekColors.CardElevated else Color(0xFFE9ECF2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.5.dp),
                    ) {
                        SegmentedTabItem(
                            label = "当前目录 (${scoped.size})",
                            selected = currentFraction < 0.5f,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    animatedOffset.animateTo(
                                        0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                                    )
                                }
                            },
                        )
                        SegmentedTabItem(
                            label = "全部会话 (${sessions.size})",
                            selected = currentFraction >= 0.5f,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    animatedOffset.animateTo(
                                        1f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .clipToBounds()
                    .onSizeChanged { containerWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            isDragging = true
                            dragProgress = animatedOffset.value
                        },
                        onDragStopped = { velocity ->
                            val currentVal = dragProgress
                            val targetPage = when {
                                velocity < -400f -> 1
                                velocity > 400f -> 0
                                else -> currentVal.roundToInt().coerceIn(0, 1)
                            }
                            scope.launch {
                                animatedOffset.snapTo(dragProgress)
                                isDragging = false
                                animatedOffset.animateTo(
                                    targetValue = targetPage.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                                )
                            }
                        },
                    ),
            ) {
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无历史会话，请点击右上角「新建」开启全新对话",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    // Page 0: 当前目录
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = 0 - currentFraction
                                translationX = offset * size.width
                                alpha = (1f - kotlin.math.abs(offset) * 0.45f).coerceIn(0f, 1f)
                            },
                    ) {
                        val scopedTreeGroups = remember(scoped, lineageMap) {
                            SessionRelationResolver.buildTreeGroups(scoped, lineageMap)
                        }
                        if (scopedTreeGroups.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "当前工作路径下暂无会话\n← 左滑查看全部会话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GeekColors.TextMuted,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(scopedTreeGroups) { gIdx, group ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        group.items.forEach { treeItem ->
                                            SessionPickerRow(
                                                session = treeItem.session,
                                                depth = treeItem.depth,
                                                onPick = { onPickSession(it.id) },
                                                onDelete = { sessionToDelete = it },
                                            )
                                        }
                                    }
                                    if (gIdx < scopedTreeGroups.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                                .height(0.6.dp)
                                                .background(GeekColors.TextMuted.copy(alpha = 0.14f)),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Page 1: 全部会话
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = 1 - currentFraction
                                translationX = offset * size.width
                                alpha = (1f - kotlin.math.abs(offset) * 0.45f).coerceIn(0f, 1f)
                            },
                    ) {
                        val groups = remember(sessions) {
                            sessions.groupBy {
                                it.cwd?.trim()?.replace('\\', '/')?.trimEnd('/')?.ifBlank { "未指定目录" } ?: "未指定目录"
                            }.entries.sortedByDescending { (_, gList) ->
                                gList.maxOfOrNull { it.timestamp.orEmpty() }.orEmpty()
                            }.toList()
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            groups.forEachIndexed { groupIndex, (cwd, groupSessions) ->
                                val cwdTreeGroups = SessionRelationResolver.buildTreeGroups(groupSessions, lineageMap)
                                item(key = "hdr-$cwd") {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = GeekColors.CardElevated,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top = if (groupIndex == 0) 4.dp else 22.dp,
                                                bottom = 6.dp
                                            ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = GeekColors.TerminalCyan,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = cwd,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                color = GeekColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = GeekColors.CardHighlight.copy(alpha = 0.5f),
                                            ) {
                                                Text(
                                                    text = "${groupSessions.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = GeekColors.TextSecondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                itemsIndexed(cwdTreeGroups) { gIdx, group ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        group.items.forEach { treeItem ->
                                            SessionPickerRow(
                                                session = treeItem.session,
                                                depth = treeItem.depth,
                                                onPick = { onPickSession(it.id) },
                                                onDelete = { sessionToDelete = it },
                                            )
                                        }
                                    }
                                    if (gIdx < cwdTreeGroups.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                                .height(0.6.dp)
                                                .background(GeekColors.TextMuted.copy(alpha = 0.14f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )

    if (sessionToDelete != null) {
        val target = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
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
                        text = "彻底删除会话",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "确定要从本地彻底删除以下会话吗？此操作将物理删除会话记录，无法撤销。",
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
                                text = target.name ?: target.preview ?: "会话 ${target.id.take(8)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = GeekColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!target.cwd.isNullOrBlank()) {
                                Text(
                                    text = target.cwd,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    color = GeekColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                        sessionToDelete = null
                        if (manager != null) {
                            manager.forgetSession(toDel.id)
                        } else {
                            LocalAgentSessionStore(context).deleteSession(toDel.id)
                        }
                        diskRefreshTrigger++
                        Toast.makeText(context, "已删除会话", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("彻底删除", color = GeekColors.RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("取消", color = GeekColors.TextMuted)
                }
            },
        )
    }
}

@Composable
private fun SegmentedTabItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isDark = GeekColors.isDark
    val shape = RoundedCornerShape(9.dp)
    val bgColor = if (selected) {
        if (isDark) GeekColors.CardHighlight else Color.White
    } else {
        Color.Transparent
    }
    Surface(
        shape = shape,
        color = bgColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                ),
                color = if (selected) GeekColors.TextPrimary else GeekColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SessionPickerRow(
    session: PiSessionBrief,
    depth: Int = 0,
    onPick: (PiSessionBrief) -> Unit,
    onDelete: ((PiSessionBrief) -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPick(session) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (depth > 0) {
                BranchFoldGuide(
                    depth = depth,
                    modifier = Modifier.padding(top = 1.5.dp, end = 5.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = session.name
                            ?: session.preview
                            ?: "会话 ${session.id.take(8)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                        ),
                        color = if (depth == 0) GeekColors.TextPrimary else GeekColors.TextPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GeekColors.CardElevated,
                        ) {
                            Text(
                                text = "${session.messageCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = GeekColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                            )
                        }
                        if (onDelete != null) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onDelete(session) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "删除会话",
                                    tint = GeekColors.TextMuted,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.5.dp))

                Text(
                    text = "${formatPiSessionTime(session.timestamp)} · ID: ${session.id.take(8)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.5.sp),
                    color = GeekColors.TextMuted,
                )
            }
        }
    }
}

private fun formatIsoTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(millis))
}

private fun formatPiSessionTime(timestamp: String?): String {
    if (timestamp.isNullOrEmpty() || timestamp.length < 16) return "时间未知"
    return timestamp.take(10) + " " + timestamp.substring(11, 16)
}

// ---------------------------------------------------------------- Running Status Capsule

@Composable
private fun BoxScope.RunningStatusCapsule(
    visible: Boolean,
    statusMessage: String?,
) {
    if (visible) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GeekColors.CardElevated,
            border = BorderStroke(1.dp, GeekColors.BrandAccentGlow),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                    color = GeekColors.BrandAccent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusMessage ?: "Agent 正在执行...",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = GeekColors.BrandAccent,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Welcome View

@Composable
private fun LocalAgentWelcomeView(
    workspacePath: String,
    onQuickPrompt: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = GeekColors.BrandPurple.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = GeekColors.BrandPurple,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "手机纯原生 AI 创作引擎",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = GeekColors.TextPrimary,
        )
        Text(
            text = "纯原生免装环境 • 零外部依赖 • 自由读写文件 • 实时网页交互 • 生成 Word/PPT",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = GeekColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(20.dp))

        // Quick feature cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeatureWelcomeCard(
                title = "网页开发与实时交互",
                desc = "帮我做一个带音效与动态效果的极客计算器网页",
                icon = Icons.Default.Language,
                onClick = { onQuickPrompt("请帮我开发一个现代化的高颜值极客计算器网页（包含优雅深色拟物设计、按键动效与清晰按键逻辑），并自动启动本地预览。") },
            )

            FeatureWelcomeCard(
                title = "标准 Word 文档生成",
                desc = "撰写一份「2026 移动端自主 Agent 架构白皮书」",
                icon = Icons.Default.Description,
                onClick = { onQuickPrompt("请帮我在手机本地生成一份标准排版的 Word 文档（白皮书.docx），包含大标题、执行摘要、技术架构章节和对比表格，完成后提供直接打开入口。") },
            )

            FeatureWelcomeCard(
                title = "PPT 演示文稿制作",
                desc = "制作一套 5 页的「产品季度汇报演示文稿」",
                icon = Icons.Default.Bolt,
                onClick = { onQuickPrompt("请帮我制作一份 5 页的标准 16:9 PowerPoint 演示文稿（汇报.pptx），包含封面、业务现状、核心突破、指标对比和未来规划。") },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "文件存放目录: $workspacePath",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = GeekColors.TextMuted,
        )
    }
}

@Composable
private fun FeatureWelcomeCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GeekColors.TerminalCyan,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextPrimary,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = GeekColors.TextMuted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- High-Value Action / Artifact Card (Outside Fold)

@Composable
private fun LocalAgentArtifactActionCard(
    item: LocalChatRow.Action,
    onOpenWebPreview: (String, String) -> Unit,
    onOpenOfficeDocument: (String, String) -> Unit,
) {
    val cardShape = RoundedCornerShape(18.dp)

    when (val action = item.action) {
        is ExtraAction.WebPreview -> {
            val projectName = action.filePath.trimEnd('/').substringAfterLast('/')
            Surface(
                shape = cardShape,
                color = GeekColors.CardSurface,
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(cardShape)
                    .pressClickEffect(),
                onClick = { onOpenWebPreview(action.serverUrl, action.filePath) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeekColors.CardElevated,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = GeekColors.TerminalCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "网页工程已就绪",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GeekColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$projectName · ${action.serverUrl}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = GeekColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                            ),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    GeekSmallButton(
                        label = "立即预览",
                        containerColor = GeekColors.BrandAccent,
                        contentColor = Color.White,
                        borderColor = GeekColors.BrandAccent,
                        onClick = { onOpenWebPreview(action.serverUrl, action.filePath) },
                    )
                }
            }
        }

        is ExtraAction.OfficeDocument -> {
            val fileName = action.filePath.substringAfterLast('/')
            val isDocx = fileName.endsWith(".docx", ignoreCase = true)
            val isPptx = fileName.endsWith(".pptx", ignoreCase = true)
            val typeTitle = when {
                isDocx -> "Word 文档已生成"
                isPptx -> "PPT 演示文稿已生成"
                else -> "Office 文档已生成"
            }
            val accentColor = if (isPptx) GeekColors.AmberWarn else GeekColors.BrandAccent

            Surface(
                shape = cardShape,
                color = GeekColors.CardSurface,
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(cardShape)
                    .pressClickEffect(),
                onClick = { onOpenOfficeDocument(action.filePath, action.mimeType) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeekColors.CardElevated,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = typeTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GeekColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = GeekColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                            ),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    GeekSmallButton(
                        label = "打开文档",
                        containerColor = accentColor,
                        contentColor = Color.White,
                        borderColor = accentColor,
                        onClick = { onOpenOfficeDocument(action.filePath, action.mimeType) },
                    )
                }
            }
        }

        is ExtraAction.FileCreated -> {
            val fileName = action.filePath.substringAfterLast('/')
            Surface(
                shape = cardShape,
                color = GeekColors.CardSurface,
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(cardShape)
                    .pressClickEffect(),
                onClick = { onOpenOfficeDocument(action.filePath, "*/*") },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeekColors.CardElevated,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = GeekColors.TerminalCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "文件已生成: $fileName",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GeekColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = action.filePath,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = GeekColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                            ),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    GeekSmallButton(
                        label = "打开",
                        containerColor = GeekColors.CardElevated,
                        contentColor = GeekColors.TextPrimary,
                        borderColor = GeekColors.BorderSubtle,
                        onClick = { onOpenOfficeDocument(action.filePath, "*/*") },
                    )
                }
            }
        }

        is ExtraAction.QuestionAnswered -> {
            // Interactive question answer already rendered as QuestionOptionCard in chat timeline
        }
    }
}

// ---------------------------------------------------------------- Tool Execution Card

@Composable
private fun LocalAgentToolExecutionCard(
    toolCall: LocalAgentItem.ToolCall,
    onOpenWebPreview: (String, String) -> Unit,
    onOpenOfficeDocument: (String, String) -> Unit,
    onCollapsed: (() -> Unit)? = null,
    onCollapsedWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardBgColor by animateColorAsState(
        targetValue = if (isPressed) GeekColors.CardHighlight else if (GeekColors.isDark) Color(0xFF1B2230) else Color(0xFFFFFFFF),
        animationSpec = tween(120),
        label = "toolCardBg",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "toolChevron",
    )

    val commandSummary = remember(toolCall.inputArgs) {
        try {
            val obj = org.json.JSONObject(toolCall.inputArgs)
            obj.optString("command", "").ifEmpty {
                obj.optString("path", "").ifEmpty {
                    obj.optString("project_name", "").ifEmpty {
                        obj.optString("file_name", "")
                    }
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBgColor,
        border = null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onGloballyPositioned { cardCoordinates = it }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val wasExpanded = isExpanded
                    isExpanded = !isExpanded
                    if (wasExpanded) {
                        if (onCollapsedWithCoords != null) {
                            onCollapsedWithCoords.invoke(cardCoordinates)
                        } else {
                            onCollapsed?.invoke()
                        }
                    }
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Header Row (naked icon without background)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    val iconTint = if (toolCall.isError) GeekColors.AmberWarn else GeekColors.TerminalCyan
                    Icon(
                        imageVector = when {
                            toolCall.toolName.contains("shell") || toolCall.toolName.contains("command") -> Icons.Default.Terminal
                            toolCall.toolName.contains("web") -> Icons.Default.Language
                            toolCall.toolName.contains("word") || toolCall.toolName.contains("presentation") -> Icons.Default.Description
                            toolCall.toolName.contains("file") -> Icons.Default.Folder
                            else -> Icons.Default.Code
                        },
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = toolCall.toolName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = if (toolCall.toolName.contains("shell") || toolCall.toolName.contains("command")) FontFamily.Monospace else FontFamily.Default,
                        ),
                        color = GeekColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (commandSummary.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = commandSummary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = GeekColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = if (toolCall.isFinished) "${toolCall.elapsedMs}ms" else "运行中..."
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = if (toolCall.isError) GeekColors.AmberWarn else GeekColors.TextMuted,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GeekColors.TextMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            // Collapsible Output Snippet & Action Buttons (original style)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(140)) + expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Command / Input display
                    val displayInput = if (commandSummary.isNotBlank()) commandSummary else toolCall.inputArgs
                    if (displayInput.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GeekColors.CardElevated,
                            border = null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = if (toolCall.toolName.contains("shell")) "$" else "❯",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = GeekColors.TerminalCyan,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                                Text(
                                    text = displayInput,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = GeekColors.TextPrimary,
                                )
                            }
                        }
                    }

                    // Output Snippet
                    if (toolCall.output.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GeekColors.DeepCanvas,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = toolCall.output,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = if (toolCall.isError) GeekColors.AmberWarn else GeekColors.TextSecondary,
                                softWrap = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(8.dp),
                            )
                        }
                    } else if (toolCall.isFinished && toolCall.output.isBlank()) {
                        Text(
                            text = "（无输出内容）",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                color = GeekColors.TextMuted,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Input Bar & Integrated Prompt Chips

@Composable
private fun LocalAgentInputBar(
    inputText: String,
    isRunning: Boolean,
    currentModel: String,
    onModelClick: () -> Unit,
    onNewSessionClick: (() -> Unit)? = null,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendSteer: (() -> Unit)? = null,
    onSendFollowUp: (() -> Unit)? = null,
    onStop: () -> Unit,
) {
    val isDark = GeekColors.isDark
    val canSend = inputText.isNotBlank()
    val sendButtonScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "localSendScale",
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Current Model Chip (Apple pill)
                val modelChipShape = RoundedCornerShape(16.dp)
                Surface(
                    shape = modelChipShape,
                    color = GeekColors.BrandAccent.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, GeekColors.BrandAccent.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .clip(modelChipShape)
                        .pressClickEffect(),
                    onClick = onModelClick,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = currentModel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = GeekColors.BrandAccent,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "更改",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = GeekColors.TextMuted,
                        )
                    }
                }

                if (onNewSessionClick != null) {
                    val newSessionShape = RoundedCornerShape(16.dp)
                    Surface(
                        shape = newSessionShape,
                        color = GeekColors.CardElevated,
                        border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .clip(newSessionShape)
                            .pressClickEffect(),
                        onClick = onNewSessionClick,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "+ 新会话",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = GeekColors.TextPrimary,
                            )
                        }
                    }
                }
            }

            if (isRunning && canSend) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "⚡ 引导 (Steer) · 📋 跟进 (Follow-up) · 支持 /btw 旁支提问",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = GeekColors.AmberWarn,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Main text input & send/stop button (Apple Messages style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = if (isRunning) "输入调整引导或跟进任务 (支持 /btw)..." else "向本地 AI Agent 发送指令或问题...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeekColors.TextMuted,
                            fontSize = 14.sp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GeekColors.CardElevated,
                        unfocusedContainerColor = GeekColors.CardElevated,
                        focusedBorderColor = if (isRunning) GeekColors.AmberWarn else GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        cursorColor = if (isRunning) GeekColors.AmberWarn else GeekColors.BrandAccent,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    ),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                if (isRunning) {
                                    onSendSteer?.invoke() ?: onSend()
                                } else {
                                    onSend()
                                }
                            }
                        }
                    ),
                )

                Spacer(Modifier.width(8.dp))

                if (isRunning) {
                    if (canSend) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Instant Steer Button
                            Surface(
                                shape = CircleShape,
                                color = GeekColors.AmberWarn,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .pressClickEffect(),
                                onClick = { onSendSteer?.invoke() ?: onSend() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "引导 (Steer)",
                                        tint = Color.Black,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }

                            // Follow-up Queue Button
                            if (onSendFollowUp != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = GeekColors.TerminalCyan,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .pressClickEffect(),
                                    onClick = { onSendFollowUp.invoke() },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.FormatListBulleted,
                                            contentDescription = "跟进 (Follow-up)",
                                            tint = Color.Black,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }
                            }

                            // Stop Button
                            Surface(
                                shape = CircleShape,
                                color = GeekColors.CardElevated,
                                border = BorderStroke(0.8.dp, GeekColors.AmberWarn.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .pressClickEffect(),
                                onClick = onStop,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "停止",
                                        tint = GeekColors.AmberWarn,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty input while running: prominent stop button
                        Surface(
                            shape = CircleShape,
                            color = GeekColors.AmberWarn,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .pressClickEffect(),
                            onClick = onStop,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "停止",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                } else {
                    val sendButtonColor = if (canSend) GeekColors.BrandAccent else (if (isDark) GeekColors.CardElevated else Color(0xFFE8ECF2))
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
                        onClick = { if (canSend) onSend() },
                        enabled = canSend,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "发送",
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




