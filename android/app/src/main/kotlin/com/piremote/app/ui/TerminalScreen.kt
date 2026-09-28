package com.piremote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import com.piremote.app.data.PiSessionBrief
import com.piremote.app.data.SessionRelationInfo
import com.piremote.app.data.SessionRelationKind
import com.piremote.app.data.SessionRelationResolver
import com.piremote.app.data.SessionRepository
import com.piremote.app.data.TerminalEvent
import com.piremote.app.ui.gui.GuiChatContent
import com.piremote.terminal.KeyEventCodes
import com.piremote.terminal.view.TerminalTheme
import com.piremote.terminal.view.TerminalView
import kotlinx.coroutines.launch

/**
 * Modern geek terminal & AI agent workspace.
 * Features a unified, persistent top navigation bar that remains permanently fixed across
 * both Graphical (AI chat cards) and Terminal (raw PTY emulation) modes.
 */
@Composable
fun TerminalScreen(
    repository: SessionRepository,
    sessionId: String,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = GeekColors.isDark

    val terminalView = remember(sessionId) {
        repository.getOrCreateTerminalView(sessionId, context).apply {
            (parent as? ViewGroup)?.removeView(this)
            onOutput = { bytes -> repository.sendRaw(bytes) }
            onResize = { cols, rows ->
                repository.pendingResize.value = cols to rows
                repository.resize(cols, rows)
            }
            onTitleChanged = { repository.onTitleChanged(it) }
            onClipboardCopy = { text -> copyToClipboard(context, text) }
            onTap = { showKeyboard() }
        }
    }

    var isGuiMode by remember(sessionId) { mutableStateOf(repository.viewModeFor(sessionId)) }
    val isRpcSession = repository.sessionKind(sessionId) == "rpc"
    var scrolledBack by remember { mutableStateOf(false) }

    val piSessionName by repository.piSessionName.collectAsState()
    val piModelProvider by repository.piModelProvider.collectAsState()
    val piSessions by repository.piSessions.collectAsState()
    var filterCwd by remember(sessionId) { mutableStateOf(repository.sessionCwd(sessionId)) }
    var showSessionPicker by remember { mutableStateOf(false) }
    LaunchedEffect(showSessionPicker) {
        if (showSessionPicker) repository.requestPiSessionList(sessionId, null)
    }

    val chatOutline by repository.chatOutline.collectAsState()
    var showChatOutline by remember { mutableStateOf(false) }
    var jumpTargetId by remember { mutableStateOf<String?>(null) }
    var outlineForkTarget by remember { mutableStateOf<com.piremote.app.ui.gui.GuiChatOutlineItem?>(null) }
    var showAgentConfigDialog by remember { mutableStateOf(false) }

    LaunchedEffect(terminalView) {
        terminalView.onScrollChanged = { offset, _ ->
            scrolledBack = offset > 0
        }
    }

    LaunchedEffect(isDark) {
        val px = terminalView.resources.displayMetrics.scaledDensity * 13f
        terminalView.theme = if (isDark) {
            TerminalTheme.dark(px, Typeface.MONOSPACE)
        } else {
            TerminalTheme.light(px, Typeface.MONOSPACE)
        }
    }

    LaunchedEffect(sessionId, isGuiMode) {
        if (!isGuiMode) {
            kotlinx.coroutines.delay(260)
            terminalView.showKeyboard()
        }
    }

    LaunchedEffect(sessionId) {
        repository.events.collect { event ->
            when (event) {
                is TerminalEvent.Output -> terminalView.feed(event.data)
                TerminalEvent.Reset -> Unit
                TerminalEvent.ReplayDone -> terminalView.scrollToBottom()
            }
        }
    }

    Scaffold(
        topBar = {
            SessionTopNavigationBar(
                title = piSessionName ?: title.ifBlank { "Pi Agent" },
                sessionId = sessionId,
                isGuiMode = isGuiMode,
                isRpcSession = isRpcSession,
                modelProvider = piModelProvider,
                onBack = onBack,
                onToggleGuiMode = {
                    val next = !isGuiMode
                    isGuiMode = next
                    repository.setGuiMode(sessionId, next)
                },
                onShowOutline = if (isRpcSession) {
                    { showChatOutline = true }
                } else {
                    null
                },
                onSwitchSession = if (isRpcSession) {
                    { showSessionPicker = true }
                } else {
                    null
                },
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
            if (isGuiMode) {
                key(sessionId) {
                    GuiChatContent(
                        terminalView = terminalView,
                        repository = repository,
                        sessionId = sessionId,
                        isRpcSession = isRpcSession,
                        onSendText = { text ->
                            repository.sendText(sessionId, text + "\r")
                        },
                        onSwitchSession = { showSessionPicker = true },
                        onOutlineChanged = { repository.setChatOutline(it) },
                        jumpTargetId = jumpTargetId,
                        onJumpConsumed = { jumpTargetId = null },
                        onShowOutline = { showChatOutline = true },
                    )
                }
            } else {
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
                                terminalView
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
    }

    if (showAgentConfigDialog) {
        AgentConfigDialog(
            targetEnvironmentName = "电脑端 (PC)",
            onDismiss = { showAgentConfigDialog = false },
            onInstall = { title, cmd ->
                showAgentConfigDialog = false
                android.widget.Toast.makeText(context, "已向终端注入命令: $title", android.widget.Toast.LENGTH_SHORT).show()
                val clean = cmd.trimEnd()
                if (isGuiMode) {
                    repository.sendText(sessionId, clean + "\r")
                } else {
                    repository.sendRaw((clean + "\r").toByteArray(Charsets.UTF_8))
                }
            },
        )
    }

    val piSessionDeletedResult by repository.piSessionDeletedResult.collectAsState()
    LaunchedEffect(piSessionDeletedResult) {
        piSessionDeletedResult?.let { res ->
            if (res.success) {
                android.widget.Toast.makeText(context, "已从电脑端彻底删除历史会话", android.widget.Toast.LENGTH_SHORT).show()
                repository.requestPiSessionList(sessionId, null)
            } else {
                android.widget.Toast.makeText(context, "删除会话失败: ${res.error ?: "未知错误"}", android.widget.Toast.LENGTH_LONG).show()
            }
            repository.clearPiSessionDeletedResult()
        }
    }

    if (showSessionPicker) {
        PiSessionPickerDialog(
            sessions = piSessions,
            currentCwd = filterCwd,
            lineageMap = repository.lineageStore.getLineageMap(),
            onPick = { session ->
                showSessionPicker = false
                session.cwd?.let { filterCwd = it }
                repository.sendAgentCommand(
                    sessionId,
                    org.json.JSONObject()
                        .put("type", "switch_session")
                        .put("sessionPath", session.file),
                )
            },
            onRefresh = {
                repository.requestPiSessionList(sessionId, null)
            },
            onDeleteSession = { session ->
                repository.deletePiSession(session.file, session.id)
            },
            onDismiss = { showSessionPicker = false },
        )
    }

    if (showChatOutline) {
        ChatOutlineDialog(
            items = chatOutline,
            onPick = { item ->
                showChatOutline = false
                jumpTargetId = item.id
            },
            onFork = { item ->
                showChatOutline = false
                outlineForkTarget = item
            },
            onDismiss = { showChatOutline = false },
        )
    }

    outlineForkTarget?.let { item ->
        com.piremote.app.ui.gui.ForkConfirmDialog(
            previewText = item.label,
            onConfirmWithSummary = { mode, customPrompt ->
                val payload = org.json.JSONObject().put("type", "fork")
                val eid = item.entryId
                if (!eid.isNullOrBlank()) {
                    payload.put("entryId", eid)
                }
                if (mode == com.piremote.app.ui.gui.ForkSummaryMode.SUMMARIZE) {
                    payload.put("summary", true)
                } else if (mode == com.piremote.app.ui.gui.ForkSummaryMode.CUSTOM_PROMPT) {
                    payload.put("summary", true)
                    customPrompt?.takeIf { it.isNotBlank() }?.let { payload.put("customPrompt", it) }
                }
                repository.sendAgentCommand(sessionId, payload)
                android.widget.Toast.makeText(context, "正在从该节点分叉会话...", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { outlineForkTarget = null },
        )
    }
}

/**
 * Historical pi conversations dialog with Segmented Switcher.
 */
@Composable
fun PiSessionPickerDialog(
    sessions: List<PiSessionBrief>,
    currentCwd: String?,
    lineageMap: Map<String, String> = emptyMap(),
    onPick: (PiSessionBrief) -> Unit,
    onRefresh: () -> Unit,
    onDeleteSession: ((PiSessionBrief) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var sessionToDelete by remember { mutableStateOf<PiSessionBrief?>(null) }
    val animatedOffset = remember { Animatable(0f) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val currentFraction = if (isDragging) dragProgress else animatedOffset.value

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

    val sortedSessions = remember(sessions) {
        sessions.sortedByDescending { it.timestamp.orEmpty() }
    }

    val normCwd = normPath(currentCwd)
    val scoped = remember(sortedSessions, normCwd) {
        sortedSessions.filter {
            val sCwd = normPath(it.cwd)
            sCwd.isNotEmpty() && (sCwd == normCwd || (normCwd.isNotEmpty() && sCwd.endsWith(normCwd)))
        }
    }

    val isDark = GeekColors.isDark

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
                                text = "共扫描到 $totalDirs 个工程 · ${sessions.size} 个会话",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = GeekColors.TextMuted,
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = GeekColors.CardElevated,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onRefresh),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新扫描",
                                tint = GeekColors.BrandAccent,
                                modifier = Modifier.size(17.dp),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 28.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = GeekColors.BrandAccent,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "正在扫描电脑上的全部会话文件...",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextMuted,
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
                                    text = "当前工程路径下暂无会话\n← 左滑查看全部会话",
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
                                                onPick = onPick,
                                                onDelete = if (onDeleteSession != null) { { sessionToDelete = it } } else null,
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
                        val groups = remember(sortedSessions) {
                            sortedSessions.groupBy {
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
                                                onPick = onPick,
                                                onDelete = if (onDeleteSession != null) { { sessionToDelete = it } } else null,
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
                        text = "确定要从电脑端删除以下会话吗？此操作将物理删除会话持久化文件，无法撤销。",
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
                        onDeleteSession?.invoke(toDel)
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
internal fun BranchFoldGuide(
    depth: Int,
    modifier: Modifier = Modifier,
    color: Color = GeekColors.TextMuted.copy(alpha = 0.50f),
) {
    val stepDp = 12.dp
    val totalWidth = (depth * 12).dp
    val height = 20.dp

    Canvas(
        modifier = modifier.size(width = totalWidth, height = height),
    ) {
        val strokeWidthPx = 1.35.dp.toPx()
        val stepPx = stepDp.toPx()
        val yTargetPx = 10.dp.toPx()

        // Guide lines for preceding depths
        for (i in 0 until (depth - 1)) {
            val guideX = (i * stepPx) + (stepPx * 0.35f)
            drawLine(
                color = color.copy(alpha = color.alpha * 0.45f),
                start = Offset(guideX, 0f),
                end = Offset(guideX, size.height),
                strokeWidth = strokeWidthPx * 0.85f,
                cap = StrokeCap.Round,
            )
        }

        // L-shaped branch fold line for current depth
        val startX = ((depth - 1) * stepPx) + (stepPx * 0.35f)
        val endX = size.width

        val path = Path().apply {
            moveTo(startX, 0f)
            lineTo(startX, yTargetPx)
            lineTo(endX, yTargetPx)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
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

private fun formatPiSessionTime(timestamp: String?): String {
    if (timestamp.isNullOrEmpty() || timestamp.length < 16) return "时间未知"
    return timestamp.take(10) + " " + timestamp.substring(11, 16)
}

/**
 * Modern Tree-style Outline Dialog with vertical timeline and role iconography.
 */
@Composable
internal fun ChatOutlineDialog(
    items: List<com.piremote.app.ui.gui.GuiChatOutlineItem>,
    onPick: (com.piremote.app.ui.gui.GuiChatOutlineItem) -> Unit,
    onFork: ((com.piremote.app.ui.gui.GuiChatOutlineItem) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expandedDrawers by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    val groups = remember(items) {
        val list = mutableListOf<Pair<com.piremote.app.ui.gui.GuiChatOutlineItem?, List<com.piremote.app.ui.gui.GuiChatOutlineItem>>>()
        var header: com.piremote.app.ui.gui.GuiChatOutlineItem? = null
        var children = mutableListOf<com.piremote.app.ui.gui.GuiChatOutlineItem>()
        fun flush() {
            list.add(header to children.toList())
            header = null
            children = mutableListOf()
        }
        for (item in items) {
            if (item.turnStart) {
                flush()
                header = item
            } else {
                children.add(item)
            }
        }
        flush()
        list
    }

    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) groups
        else {
            val q = searchQuery.trim().lowercase()
            groups.filter { (header, children) ->
                header?.label?.lowercase()?.contains(q) == true ||
                children.any { it.label.lowercase().contains(q) }
            }
        }
    }

    fun isRowTopVisible(index: Int): Boolean {
        val layoutInfo = listState.layoutInfo
        val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        return info.offset in viewportStart until viewportEnd
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
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "会话树漫游 (Session Tree)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                    Text(
                        text = "共 ${groups.size} 个对话节点 · 搜索定位 · 切换分叉",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = GeekColors.TextMuted,
                    )
                }
            }
        },
        text = {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前会话暂无结构化大纲与分支节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = GeekColors.TextMuted,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Type to search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Type to search (搜索历史提问与节点)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { searchQuery = "" },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "清空",
                                            tint = GeekColors.TextMuted,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = GeekColors.TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeekColors.BrandAccent,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedContainerColor = GeekColors.CardElevated,
                            unfocusedContainerColor = GeekColors.CardElevated,
                        ),
                    )

                    if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "未检索到匹配的会话节点",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                            itemsIndexed(filteredGroups) { groupIndex, (header, children) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (header != null) {
                                val open = expandedDrawers.contains(header.id)
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()

                                val headerBgColor by animateColorAsState(
                                    targetValue = when {
                                        isPressed -> if (GeekColors.isDark) Color(0x18FFFFFF) else Color(0x0C000000)
                                        open -> if (GeekColors.isDark) Color(0x0EFFFFFF) else Color(0x06000000)
                                        else -> Color.Transparent
                                    },
                                    animationSpec = tween(120),
                                    label = "outlineHeaderBg",
                                )

                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (open) 180f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    label = "outlineChevron",
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = headerBgColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                        ) {
                                            expandedDrawers =
                                                if (open) expandedDrawers - header.id else expandedDrawers + header.id
                                            if (open && !isRowTopVisible(groupIndex)) {
                                                scope.launch { listState.scrollToItem(groupIndex) }
                                            }
                                        },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier.size(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = GeekColors.BrandAccent,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .graphicsLayer { rotationZ = chevronRotation },
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier.size(20.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = GeekColors.BrandAccent,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))

                                        Text(
                                            text = header.label,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = GeekColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(6.dp))

                                        if (onFork != null) {
                                            com.piremote.app.ui.gui.CardActionButton(
                                                icon = Icons.Default.AltRoute,
                                                label = "分叉",
                                                onClick = { onFork(header) },
                                            )
                                            Spacer(Modifier.width(5.dp))
                                        }

                                        com.piremote.app.ui.gui.CardActionButton(
                                            icon = Icons.Default.ArrowUpward,
                                            label = "定位",
                                            onClick = { onPick(header) },
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = header == null || expandedDrawers.contains(header.id),
                                enter = fadeIn(tween(160, delayMillis = 20)) + expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                ),
                                exit = fadeOut(tween(100)) + shrinkVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = if (header == null) 0.dp else 24.dp),
                                ) {
                                    children.forEach { child ->
                                        val displayTag = if (child.tag == "回复") "Agent" else child.tag
                                        val (childIcon, iconSize, tagColor) = when (child.tag) {
                                            "回复", "Agent" -> Triple(Icons.Default.SmartToy, 14.dp, GeekColors.TerminalCyan)
                                            "思考" -> Triple(Icons.Default.Psychology, 16.dp, GeekColors.BrandPurple)
                                            "工具" -> Triple(Icons.Default.Build, 14.dp, GeekColors.AmberWarn)
                                            "交互" -> Triple(Icons.Default.HelpOutline, 14.dp, GeekColors.NeonGreen)
                                            else -> Triple(Icons.Default.Circle, 6.dp, GeekColors.TextMuted)
                                        }

                                        val childInteraction = remember { MutableInteractionSource() }
                                        val isChildPressed by childInteraction.collectIsPressedAsState()
                                        val childBg by animateColorAsState(
                                            targetValue = if (isChildPressed) {
                                                if (GeekColors.isDark) Color(0x14FFFFFF) else Color(0x0A000000)
                                            } else {
                                                Color.Transparent
                                            },
                                            animationSpec = tween(120),
                                            label = "childBg",
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = childBg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(
                                                    interactionSource = childInteraction,
                                                    indication = null,
                                                ) { onPick(child) },
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(18.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = childIcon,
                                                        contentDescription = null,
                                                        tint = tagColor,
                                                        modifier = Modifier.size(iconSize),
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = displayTag,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = tagColor,
                                                    modifier = Modifier.width(38.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = child.label,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                    color = GeekColors.TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (groupIndex < filteredGroups.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(0.6.dp)
                                    .background(GeekColors.BorderSubtle),
                            )
                        }
                    }
                }
            }
        }
    }
},
)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
}

/**
 * Modern Glassmorphic Top Navigation Bar with authentic Apple HIG aesthetics.
 */
@Composable
fun SessionTopNavigationBar(
    title: String,
    sessionId: String,
    isGuiMode: Boolean,
    isRpcSession: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    modelProvider: String? = null,
    onShowOutline: (() -> Unit)? = null,
    onSwitchSession: (() -> Unit)? = null,
    onOpenAgentConfig: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modeSubtitle: String? = null,
    onRenameTitle: (() -> Unit)? = null,
    onToggleGuiMode: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeekColors.CardSurface.copy(alpha = 0.94f),
        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Apple-style Return Pill
            val backShape = RoundedCornerShape(14.dp)
            Surface(
                shape = backShape,
                color = GeekColors.CardElevated,
                border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                modifier = Modifier
                    .height(34.dp)
                    .clip(backShape)
                    .pressClickEffect(),
                onClick = onBack,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 6.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "返回",
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(1.dp))
                    Text(
                        text = "返回",
                        color = GeekColors.BrandAccent,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            val titleInteraction = remember { MutableInteractionSource() }
            val isTitlePressed by titleInteraction.collectIsPressedAsState()
            val titleBg by animateColorAsState(
                targetValue = if (isTitlePressed && onRenameTitle != null) GeekColors.CardElevated else Color.Transparent,
                animationSpec = tween(120),
                label = "navTitleBg",
            )

            // Title & Status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(titleBg)
                    .clickable(
                        interactionSource = titleInteraction,
                        indication = null,
                        enabled = onRenameTitle != null,
                    ) { onRenameTitle?.invoke() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isGuiMode) GeekColors.BrandPurple else GeekColors.NeonGreen),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = title.ifBlank { "Pi Agent" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onRenameTitle != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "修改会话名称",
                            tint = GeekColors.TextMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                val fontScale = LocalGuiFontScale.current
                val guiSubtitle = modelProvider?.takeIf { it.isNotBlank() } ?: "GUI"
                Text(
                    text = modeSubtitle ?: (if (isGuiMode) guiSubtitle else "终端仿真 (PTY)"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = (12f * fontScale).sp,
                    ),
                    color = if (isGuiMode) GeekColors.BrandPurple else GeekColors.TerminalCyan,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }

            if (onOpenAgentConfig != null) {
                Surface(
                    onClick = onOpenAgentConfig,
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressClickEffect(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Agent 配置与安装",
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            if (onClear != null) {
                Surface(
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressClickEffect(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "清屏",
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            if (onShowOutline != null) {
                Surface(
                    onClick = onShowOutline,
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressClickEffect(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "会话树漫游",
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            if (onSwitchSession != null) {
                val switchShape = RoundedCornerShape(12.dp)
                Surface(
                    shape = switchShape,
                    color = GeekColors.CardElevated,
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(switchShape)
                        .pressClickEffect(),
                    onClick = onSwitchSession,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "切换会话",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = GeekColors.BrandAccent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern floating button with glowing chevron to scroll back to bottom in terminal view.
 */
@Composable
fun FloatingScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = GeekColors.BrandAccent,
        shadowElevation = 6.dp,
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "↓",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun ExtraKeysBar(terminalView: TerminalView) {
    val context = LocalContext.current
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }

    LaunchedEffect(ctrl, alt) {
        terminalView.ctrlLatched = ctrl
        terminalView.altLatched = alt
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GeekColors.CardSurface,
        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Row 1: Modifiers & high-frequency shortcuts
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeycapToggle("Ctrl", active = ctrl) { ctrl = !ctrl }
                KeycapToggle("Alt", active = alt) { alt = !alt }
                KeycapButton("Esc") { terminalView.sendKeyCode(KeyEventCodes.ESCAPE) }
                KeycapButton("Tab") { terminalView.sendKeyCode(KeyEventCodes.TAB) }
                KeycapButton("^C", textColor = GeekColors.RoseError) { terminalView.sendSequence("\u0003") }
                KeycapButton("^D") { terminalView.sendSequence("\u0004") }
                KeycapButton("^O") { terminalView.sendSequence("\u000f") }
                KeycapButton("^L") { terminalView.sendSequence("\u000c") }
                KeycapButton("^Z") { terminalView.sendSequence("\u001a") }
            }

            // Row 2: Navigation & Action keys
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeycapButton("↑") { terminalView.sendKeyCode(KeyEventCodes.DPAD_UP) }
                KeycapButton("↓") { terminalView.sendKeyCode(KeyEventCodes.DPAD_DOWN) }
                KeycapButton("←") { terminalView.sendKeyCode(KeyEventCodes.DPAD_LEFT) }
                KeycapButton("→") { terminalView.sendKeyCode(KeyEventCodes.DPAD_RIGHT) }
                KeycapButton("Home") { terminalView.sendKeyCode(KeyEventCodes.MOVE_HOME) }
                KeycapButton("End") { terminalView.sendKeyCode(KeyEventCodes.MOVE_END) }
                KeycapButton("PgUp") { terminalView.sendKeyCode(KeyEventCodes.PAGE_UP) }
                KeycapButton("PgDn") { terminalView.sendKeyCode(KeyEventCodes.PAGE_DOWN) }
                KeycapButton("⏎ Enter", width = 74.dp, textColor = GeekColors.TerminalCyan) {
                    terminalView.sendKeyCode(KeyEventCodes.ENTER)
                }
                KeycapButton("粘贴", textColor = GeekColors.BrandAccent) {
                    val text = readClipboard(context)
                    if (text.isNotEmpty()) terminalView.paste(text)
                }
            }
        }
    }
}

@Composable
fun KeycapButton(
    label: String,
    width: androidx.compose.ui.unit.Dp = 54.dp,
    textColor: Color = GeekColors.TextPrimary,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        color = GeekColors.CardElevated,
        shape = shape,
        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
        modifier = Modifier
            .size(width = width, height = 36.dp)
            .clip(shape)
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

@Composable
fun KeycapToggle(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (active) GeekColors.BrandAccent else GeekColors.CardElevated
    val borderColor = if (active) GeekColors.BrandAccent else GeekColors.BorderSubtle
    val txtColor = if (active) Color.White else GeekColors.TextPrimary
    val shape = RoundedCornerShape(10.dp)

    Surface(
        color = bgColor,
        shape = shape,
        border = BorderStroke(if (active) 1.dp else 0.6.dp, borderColor),
        modifier = Modifier
            .size(width = 54.dp, height = 36.dp)
            .clip(shape)
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = txtColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}
