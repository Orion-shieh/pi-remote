package com.piremote.app.ui.gui

/**
 * Pure-Kotlin reducer folding pi's structured agent events into the
 * [GuiMessageItem] card list rendered by the graphical workspace.
 *
 * The card list has two parts: [baseItems] holds everything finalized by a
 * message_end or read from the attach snapshot; the pending tail is recomputed
 * from live state (streaming blocks, the open extension dialog, optimistic
 * user bubbles, a "thinking" placeholder) so deltas never touch the finished
 * history. IDs assigned when items enter [baseItems] stay stable, which the
 * LazyColumn needs for its keys.
 *
 * No Android or org.json types appear here, so the reducer is unit-testable
 * on the JVM: tests construct typed [AgentEvent]s directly.
 */
class GuiAgentState {

    private class ToolRun(
        var toolName: String?,
        var output: StringBuilder?,
        var isError: Boolean,
        var finished: Boolean,
    )

    private val baseItems = mutableListOf<GuiMessageItem>()
    private val toolRuns = mutableMapOf<String, ToolRun>()

    // Chunked-snapshot accumulation: batches arrive in order and the cards
    // are built once the final batch lands.
    private var snapshotState: SessionStateInfo? = null
    private var snapshotName: String? = null
    private var snapshotError: String? = null
    private var snapshotTruncated = false
    private val snapshotEntries = mutableListOf<AgentEntry>()
    private var snapshotBatchesSeen = 0
    private var snapshotBatchesTotal = 1

    /** Assistant message currently being streamed via message_update deltas. */
    private val streaming = mutableListOf<AgentBlock?>()
    private var streamingActive = false

    private data class OptimisticUserEntry(
        val text: String,
        val model: String?,
        val provider: String?,
        val sendType: String? = null,
        var awaitingTurnFinish: Boolean = false,
    )
    private var pendingDialog: AgentEvent.ExtensionUiRequest? = null
    private val optimisticUser = mutableListOf<OptimisticUserEntry>()
    private val statuses = linkedMapOf<String, String>()

    var agentRunning = false
        private set

    /** Latest extension setStatus line, shown as a thin status strip. */
    var statusLine: String? = null
        private set

    /** Stable key ("provider/id") of the current pi model, for picker matching. */
    var currentModelKey: String? = null
        private set

    /** Human-facing label of the current model (name if pi provides one, else the id). */
    var currentModelLabel: String? = null
        private set

    /** Current model provider (e.g. anthropic, google, deepseek). */
    var currentModelProvider: String? = null
        private set

    /** Current thinking level label. */
    var currentThinking: String? = null
        private set

    /** Model catalogue from get_available_models, for the model picker dialog. */
    var availableModels: List<PiModelOption> = emptyList()
        private set

    /** Valid thinking levels from get_available_thinking_levels. */
    var thinkingLevels: List<String> = emptyList()
        private set

    /** Whether thinking levels have been confirmed loaded from the agent. */
    var thinkingLevelsLoaded: Boolean = false
        private set

    /** Slash commands pi accepts via prompt (extensions, prompt templates, skills). */
    var slashCommands: List<PiSlashCommand> = emptyList()
        private set

    private var baseSeq = 0
    private var sysSeq = 0
    private var abortedTurnAlreadyFinalized = false

    val items: List<GuiMessageItem>
        get() {
            val result = ArrayList<GuiMessageItem>(baseItems.size + 8)
            result.addAll(baseItems)
            result.addAll(pendingItems())
            return result
        }

    // ------------------------------------------------------------------ input

    fun onEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Snapshot -> beginSnapshot(event)
            is AgentEvent.SnapshotBatch -> accumulateBatch(event.entries, event.batchIndex, event.batchTotal)
            is AgentEvent.MessageStart -> onMessageStart(event)
            is AgentEvent.MessageUpdate -> applyDelta(event.delta)
            is AgentEvent.MessageEnd -> onMessageEnd(event.message)

            is AgentEvent.ToolExecutionStart ->
                toolRuns[event.toolCallId] = ToolRun(event.toolName, StringBuilder(), false, finished = false)

            is AgentEvent.ToolExecutionUpdate -> {
                val run = toolRuns.getOrPut(event.toolCallId) {
                    ToolRun(event.toolName, StringBuilder(), event.isError, finished = false)
                }
                if (event.toolName != null) run.toolName = event.toolName
                if (!event.outputText.isNullOrEmpty()) {
                    val buffer = run.output ?: StringBuilder().also { run.output = it }
                    buffer.append(event.outputText)
                }
                if (event.isError) run.isError = true
            }

            is AgentEvent.ToolExecutionEnd -> toolRuns[event.toolCallId]?.finished = true

            AgentEvent.AgentStart -> agentRunning = true
            // agent_end fires before retries/compaction/queued turns; only
            // agent_settled means the run is really over (same as pi-web).
            AgentEvent.AgentEnd -> Unit
            AgentEvent.AgentSettled -> agentRunning = false

            is AgentEvent.ExtensionUiRequest -> onDialogRequest(event)

            is AgentEvent.Notify -> baseItems += SystemStatusMessage(
                id = "sys-${sysSeq++}",
                text = event.message,
            )

            is AgentEvent.Error -> {
                agentRunning = false
                streamingActive = false
                streaming.clear()
                baseItems += ErrorMessageBlock(
                    id = "err-${sysSeq++}",
                    error = event.message,
                    details = event.code,
                )
            }

            is AgentEvent.CommandResponse -> onCommandResponse(event)
            is AgentEvent.Unknown -> Unit
        }
    }

    private fun onCommandResponse(event: AgentEvent.CommandResponse) {
        if (!event.success) {
            agentRunning = false
            val rawErr = event.error ?: "[失败] ${event.command ?: "命令"} 执行失败"
            val displayErr = if (event.command == "set_model" && rawErr.contains("Model not found", ignoreCase = true)) {
                "$rawErr\n\n💡 提示：Pi Agent 运行中的会话无法热重载新配置。若这是刚添加的模型，请返回会话列表【新建会话】即可生效；或检查电脑端 ~/.pi/agent/models.json 中模型 ID 与服务商名称是否完全匹配。"
            } else {
                rawErr
            }
            baseItems += ErrorMessageBlock(
                id = "err-${sysSeq++}",
                error = displayErr,
                details = event.command,
            )
            return
        }
        event.state?.let { updateModelState(it) }
        if (event.command == "get_available_models") {
            availableModels = event.models
        } else if (event.models.isNotEmpty()) {
            availableModels = event.models
        }
        if (event.command == "get_available_thinking_levels") {
            thinkingLevels = event.thinkingLevels
            thinkingLevelsLoaded = true
        } else if (event.thinkingLevels.isNotEmpty()) {
            thinkingLevels = event.thinkingLevels
            thinkingLevelsLoaded = true
        }
        if (event.commands.isNotEmpty()) slashCommands = event.commands
        event.statsText?.let { baseItems += SystemStatusMessage(id = "sys-${sysSeq++}", text = it) }
        event.treeText?.let { baseItems += SystemStatusMessage(id = "sys-${sysSeq++}", text = it) }
        if (event.command == "get_last_assistant_text" && event.lastAssistantText != null) {
            baseItems += SystemStatusMessage(id = "sys-${sysSeq++}", text = "最终回复已复制到剪贴板")
        }
    }

    private fun updateModelState(state: SessionStateInfo) {
        if (!state.modelKey.isNullOrBlank()) currentModelKey = state.modelKey
        if (!state.modelProvider.isNullOrBlank()) {
            currentModelProvider = state.modelProvider
        } else if (!state.modelKey.isNullOrBlank() && state.modelKey!!.contains("/")) {
            currentModelProvider = state.modelKey!!.substringBefore("/")
        }
        val clean = state.cleanModelLabel ?: state.modelLabel
        if (!clean.isNullOrBlank()) currentModelLabel = clean
        if (!state.thinkingLevel.isNullOrBlank() && state.thinkingLevel != "null") {
            currentThinking = state.thinkingLevel
        }
    }

    /** Optimistically updates model state when user picks a model. */
    fun updateModel(option: PiModelOption) {
        currentModelKey = option.key
        currentModelProvider = option.provider
        currentModelLabel = option.cleanLabel
    }

    /** Optimistically removes a model from the session's available models. */
    fun removeModel(provider: String, id: String) {
        availableModels = availableModels.filterNot {
            it.provider.equals(provider, ignoreCase = true) && it.id.equals(id, ignoreCase = true)
        }
    }

    /** Optimistically adds a model to the session's available models. */
    fun addModel(option: PiModelOption) {
        if (!availableModels.any { it.provider.equals(option.provider, ignoreCase = true) && it.id.equals(option.id, ignoreCase = true) }) {
            availableModels = availableModels + option
        }
    }

    /** Optimistically updates thinking level when user taps the thinking switch button. */
    fun updateThinking(level: String?) {
        if (!level.isNullOrBlank() && level != "null") {
            currentThinking = level
        }
    }

    /** Clears the transcript after a new_session; the next attach snapshot rebuilds it. */
    fun resetForNewSession() {
        baseItems.clear()
        toolRuns.clear()
        streaming.clear()
        streamingActive = false
        pendingDialog = null
        optimisticUser.clear()
        agentRunning = false
        thinkingLevelsLoaded = false
        currentModelProvider = null
        currentModelLabel = null
        currentModelKey = null
        abortedTurnAlreadyFinalized = false
    }

    /** Abort handler: immediately stops agent running and adds system status message without creating an empty box. */
    fun onAbort() {
        agentRunning = false
        if (streamingActive) {
            val validBlocks = streaming.filterNotNull().filter {
                when (it) {
                    is AgentBlock.Text -> !it.text.contains("aborted", ignoreCase = true) && !it.text.contains("canceled", ignoreCase = true)
                    else -> true
                }
            }
            val textBlocks = validBlocks.filterIsInstance<AgentBlock.Text>().filter { it.text.isNotBlank() }
            val baseId = "abort-${baseSeq++}"
            val timestamp = System.currentTimeMillis()
            if (textBlocks.isNotEmpty()) {
                mapBlocks(baseId, validBlocks, finished = true, timestampMs = timestamp, into = baseItems)
            }
            val lastItem = baseItems.lastOrNull()
            if (lastItem !is SystemStatusMessage || lastItem.text != "已停止生成") {
                baseItems += SystemStatusMessage(
                    id = "abort-$baseId-status",
                    timestamp = timestamp,
                    text = "已停止生成",
                )
            }
            streamingActive = false
            streaming.clear()
            abortedTurnAlreadyFinalized = true
            for (entry in optimisticUser) {
                entry.awaitingTurnFinish = false
            }
        } else {
            val lastItem = baseItems.lastOrNull()
            if (lastItem !is SystemStatusMessage || lastItem.text != "已停止生成") {
                baseItems += SystemStatusMessage(
                    id = "abort-${baseSeq++}-status",
                    timestamp = System.currentTimeMillis(),
                    text = "已停止生成",
                )
            }
            abortedTurnAlreadyFinalized = true
        }
    }

    /** Optimistic user bubble shown until pi echoes the message back. */
    fun addUserMessage(
        text: String,
        model: String? = currentModelLabel ?: currentModelKey,
        provider: String? = currentModelProvider,
        sendType: String? = null,
    ) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            val awaiting = (sendType == "steer" || sendType == "follow_up") && streamingActive
            optimisticUser.add(OptimisticUserEntry(trimmed, model, provider, sendType, awaitingTurnFinish = awaiting))
        }
    }

    /** Marks the extension dialog answered. Once answered, the dialog completes. */
    fun resolveDialog(
        requestId: String,
        summary: String = "",
        selectedKeys: Set<String> = emptySet(),
    ) {
        if (pendingDialog?.requestId == requestId) {
            pendingDialog = null
        }
    }

    // ----------------------------------------------------------------- events

    private fun beginSnapshot(event: AgentEvent.Snapshot) {
        snapshotState = event.state
        snapshotName = event.name
        snapshotError = event.error
        snapshotTruncated = event.truncated
        snapshotEntries.clear()
        snapshotBatchesSeen = 0
        snapshotBatchesTotal = event.batchTotal.coerceAtLeast(1)
        accumulateBatch(event.entries, event.batchIndex, event.batchTotal)
    }

    private fun accumulateBatch(entries: List<AgentEntry>, batchIndex: Int, batchTotal: Int) {
        snapshotEntries.addAll(entries)
        snapshotBatchesSeen += 1
        if (batchIndex >= batchTotal - 1) {
            buildSnapshot()
        }
    }

    private fun buildSnapshot() {
        baseItems.clear()
        baseSeq = 0
        toolRuns.clear()
        streaming.clear()
        streamingActive = false
        pendingDialog = null
        optimisticUser.clear()
        agentRunning = snapshotState?.isStreaming ?: false
        snapshotState?.let { updateModelState(it) }

        if (snapshotError != null) {
            baseItems += SystemStatusMessage(id = "sys-${sysSeq++}", text = "快照获取失败：$snapshotError")
        }
        if (snapshotTruncated) {
            baseItems += SystemStatusMessage(id = "sys-${sysSeq++}", text = "历史较长，仅显示最近部分")
        }
        // Tool results must be registered before the cards that reference them,
        // or a toolCall entry rendered above its result would show no output.
        for (entry in snapshotEntries) {
            val message = entry.message
            if (message?.role == "toolResult") recordFinalToolResult(message)
        }
        for (entry in snapshotEntries) {
            val message = entry.message
            when {
                message != null -> appendMessage("e${baseSeq++}", message, entry.timestampMs, entry.id)
                entry.label != null -> baseItems += SystemStatusMessage(id = "sys-${entry.id}", text = entry.label)
            }
        }
    }

    private fun onMessageStart(event: AgentEvent.MessageStart) {
        when (event.message.role) {
            "assistant" -> {
                streaming.clear()
                streamingActive = true
                streaming.addAll(event.message.content)
            }

            "user" -> {
                // The authoritative copy arrives with message_end.
            }
        }
    }

    private fun onMessageEnd(message: AgentMessage) {
        when (message.role) {
            "user" -> {
                val text = message.text.trim()
                val index = optimisticUser.indexOfFirst { entry ->
                    val cleanEntry = entry.text.removePrefix("/btw").removePrefix("/side").trim()
                    entry.text == text ||
                    (text.startsWith("【旁支提问/BTW】") && (text.contains(entry.text) || (cleanEntry.isNotEmpty() && text.contains(cleanEntry)))) ||
                    (entry.sendType == "btw" && text.startsWith("【旁支提问/BTW】"))
                }
                val optEntry = if (index >= 0) optimisticUser.removeAt(index) else null
                if (text.isNotEmpty()) {
                    val isBtw = optEntry?.sendType == "btw" || text.startsWith("【旁支提问/BTW】") || text.startsWith("/btw", ignoreCase = true) || text.startsWith("/side", ignoreCase = true)
                    val displayText = if (optEntry != null && optEntry.sendType == "btw") {
                        optEntry.text
                    } else if (text.startsWith("【旁支提问/BTW】")) {
                        text.substringAfter("【旁支提问/BTW】").substringBefore("\n(说明：").trim()
                    } else {
                        text
                    }
                    baseItems += UserMessage(
                        id = "u${baseSeq++}",
                        text = displayText,
                        model = optEntry?.model ?: currentModelLabel ?: currentModelKey,
                        provider = optEntry?.provider ?: currentModelProvider,
                        sendType = if (isBtw) "btw" else optEntry?.sendType,
                    )
                }
                streamingActive = false
                streaming.clear()
            }

            "assistant" -> {
                if (abortedTurnAlreadyFinalized) {
                    abortedTurnAlreadyFinalized = false
                    agentRunning = false
                    streamingActive = false
                    streaming.clear()
                    return
                }
                appendMessage("m${baseSeq++}", message, System.currentTimeMillis())
                if (message.isError || message.isAborted || message.text.contains("aborted", ignoreCase = true)) {
                    agentRunning = false
                }
                streamingActive = false
                streaming.clear()
                for (entry in optimisticUser) {
                    entry.awaitingTurnFinish = false
                }
            }

            "toolResult" -> recordFinalToolResult(message)
        }
    }

    private fun onDialogRequest(request: AgentEvent.ExtensionUiRequest) {
        when (request.method) {
            "select", "confirm", "input", "editor", "multi_select", "multiselect" -> {
                pendingDialog = request
            }

            "notify" -> baseItems += SystemStatusMessage(
                id = "sys-${sysSeq++}",
                text = listOf(request.title, request.message).filterNotNull().filter { it.isNotEmpty() }
                    .joinToString(" · ").ifEmpty { "通知" },
            )

            "setStatus" -> {
                val key = request.statusKey ?: return
                val text = request.statusText
                if (text == null) statuses.remove(key) else statuses[key] = text
                statusLine = statuses.values.joinToString(" · ").takeIf { it.isNotEmpty() }
            }

            "setWidget" -> {
                val lines = request.widgetLines
                if (!lines.isNullOrEmpty()) {
                    baseItems += SystemStatusMessage(
                        id = "sys-${sysSeq++}",
                        text = lines.joinToString("\n"),
                    )
                }
            }
        }
    }

    private fun recordFinalToolResult(message: AgentMessage) {
        val id = message.toolCallId ?: return
        val run = toolRuns.getOrPut(id) { ToolRun(null, null, message.isError, finished = true) }
        run.finished = true
        run.isError = run.isError || message.isError
        run.output = StringBuilder(message.text)
    }

    // ------------------------------------------------------------------ delta

    private fun applyDelta(delta: AssistantDelta) {
        if (!streamingActive) return
        val index = delta.contentIndex
        if (index < 0) return
        while (streaming.size <= index) streaming.add(null)

        val current = streaming[index]
        when (delta.type) {
            "text_start" -> streaming[index] = current as? AgentBlock.Text ?: AgentBlock.Text("")
            "text_delta" -> {
                val text = current as? AgentBlock.Text ?: return
                streaming[index] = text.copy(text = text.text + (delta.deltaText ?: ""))
            }
            "text_end" -> {
                val text = current as? AgentBlock.Text ?: AgentBlock.Text("")
                streaming[index] = text.copy(text = delta.fullText ?: text.text)
            }

            "thinking_start" -> streaming[index] = current as? AgentBlock.Thinking ?: AgentBlock.Thinking("")
            "thinking_delta" -> {
                val thinking = current as? AgentBlock.Thinking ?: return
                streaming[index] = thinking.copy(text = thinking.text + (delta.deltaText ?: ""))
            }
            "thinking_end" -> {
                val thinking = current as? AgentBlock.Thinking ?: AgentBlock.Thinking("")
                streaming[index] = thinking.copy(text = delta.fullText ?: thinking.text)
            }

            "toolcall_start" -> {
                val call = current as? AgentBlock.ToolCall
                streaming[index] = AgentBlock.ToolCall(
                    toolCallId = delta.toolCallId ?: call?.toolCallId.orEmpty(),
                    toolName = delta.toolName ?: call?.toolName.orEmpty(),
                    rawInput = call?.rawInput ?: "",
                    argumentsJson = null,
                )
            }
            "toolcall_delta" -> {
                val call = current as? AgentBlock.ToolCall ?: return
                streaming[index] = call.copy(rawInput = (call.rawInput ?: "") + (delta.deltaText ?: ""))
            }
            "toolcall_end" -> {
                val call = current as? AgentBlock.ToolCall
                streaming[index] = AgentBlock.ToolCall(
                    toolCallId = delta.toolCallId ?: call?.toolCallId.orEmpty(),
                    toolName = delta.toolName ?: call?.toolName.orEmpty(),
                    rawInput = null,
                    argumentsJson = delta.argumentsJson ?: call?.argumentsJson,
                )
            }
        }
    }

    // ------------------------------------------------------------------ cards

    private fun pendingItems(): List<GuiMessageItem> {
        val extra = mutableListOf<GuiMessageItem>()

        // 1. Initial user prompt for starting a turn, or steer messages whose prior turn has completed
        for ((index, entry) in optimisticUser.withIndex()) {
            if (!entry.awaitingTurnFinish) {
                extra += UserMessage(
                    id = "optimistic-$index",
                    text = entry.text,
                    model = entry.model,
                    provider = entry.provider,
                    sendType = entry.sendType,
                )
            }
        }

        // 2. Currently streaming blocks (assistant response, thinking, live tool calls)
        if (streamingActive) {
            mapBlocks("streaming", streaming.filterNotNull(), finished = false, timestampMs = System.currentTimeMillis(), into = extra)
        }

        // 3. Fallback thinking indicator if agent is running but hasn't streamed content yet
        val hasExecutionBlock = extra.any { it is ThinkingBlock || it is ToolCallBlock || it is AssistantResponse }
        if (agentRunning && !hasExecutionBlock) {
            extra += ThinkingBlock(id = "pending-run", content = "正在思考...", isFinished = false)
        }

        // 4. Pending interactive dialog
        pendingDialog?.let {
            extra += dialogItem(it)
        }

        // 5. Steer messages sent while prior turn is still actively streaming: rendered AFTER that prior turn at the bottom
        for ((index, entry) in optimisticUser.withIndex()) {
            if (entry.awaitingTurnFinish) {
                extra += UserMessage(
                    id = "optimistic-$index",
                    text = entry.text,
                    model = entry.model,
                    provider = entry.provider,
                    sendType = entry.sendType,
                )
            }
        }

        return extra
    }

    private fun appendMessage(baseId: String, message: AgentMessage, timestampMs: Long, entryId: String? = null) {
        // Snapshot entries carry their real time; live events arrive as they
        // happen, so "now" is correct there. 0 (unparsable) falls back to now.
        val timestamp = if (timestampMs > 0) timestampMs else System.currentTimeMillis()
        when (message.role) {
            "user" -> if (message.text.isNotBlank()) {
                val raw = message.text.trim()
                val isBtw = raw.startsWith("【旁支提问/BTW】") || raw.startsWith("/btw", ignoreCase = true) || raw.startsWith("/side", ignoreCase = true)
                val displayText = if (raw.startsWith("【旁支提问/BTW】")) {
                    raw.substringAfter("【旁支提问/BTW】").substringBefore("\n(说明：").trim()
                } else {
                    raw
                }
                baseItems += UserMessage(
                    id = "u-$baseId",
                    timestamp = timestamp,
                    text = displayText,
                    model = currentModelLabel ?: currentModelKey,
                    provider = currentModelProvider,
                    sendType = if (isBtw) "btw" else null,
                    entryId = entryId,
                )
            }

            "assistant" -> {
                val rawText = message.text.trim()
                val isAbort = message.isAborted ||
                    rawText.contains("aborted", ignoreCase = true) ||
                    rawText.contains("canceled", ignoreCase = true) ||
                    rawText.contains("cancelled", ignoreCase = true) ||
                    rawText.contains("中断", ignoreCase = true)

                if (message.isError && !isAbort) {
                    val nonText = message.content.filterNot { it is AgentBlock.Text }
                    if (nonText.isNotEmpty()) {
                        mapBlocks(baseId, nonText, finished = true, timestampMs = timestamp, usage = message.usage, isError = true, into = baseItems)
                    }
                    val errText = rawText.ifEmpty { "模型调用遇到异常" }
                    baseItems += ErrorMessageBlock(
                        id = "err-$baseId",
                        error = errText,
                        timestamp = timestamp,
                    )
                } else if (isAbort) {
                    val validBlocks = message.content.filter {
                        when (it) {
                            is AgentBlock.Text -> !it.text.contains("aborted", ignoreCase = true) && !it.text.contains("canceled", ignoreCase = true)
                            else -> true
                        }
                    }
                    val textBlocks = validBlocks.filterIsInstance<AgentBlock.Text>().filter { it.text.isNotBlank() }
                    val cleanText = textBlocks.joinToString("") { it.text }.trim()
                    val lastAssistant = baseItems.lastOrNull { it is AssistantResponse } as? AssistantResponse
                    if (textBlocks.isNotEmpty() && (lastAssistant == null || lastAssistant.text != cleanText)) {
                        mapBlocks(baseId, validBlocks, finished = true, timestampMs = timestamp, usage = message.usage, isError = false, into = baseItems)
                    }
                    val lastItem = baseItems.lastOrNull()
                    if (lastItem !is SystemStatusMessage || lastItem.text != "已停止生成") {
                        baseItems += SystemStatusMessage(
                            id = "abort-$baseId",
                            timestamp = timestamp,
                            text = "已停止生成",
                        )
                    }
                } else {
                    val cleanText = message.content.filterIsInstance<AgentBlock.Text>().joinToString("") { it.text }.trim()
                    val lastAssistant = baseItems.lastOrNull { it is AssistantResponse } as? AssistantResponse
                    if (cleanText.isEmpty() || lastAssistant == null || lastAssistant.text != cleanText) {
                        mapBlocks(baseId, message.content, finished = true, timestampMs = timestamp, usage = message.usage, isError = false, into = baseItems)
                    }
                }
            }

            "toolResult" -> recordFinalToolResult(message)
        }
    }

    private fun mapBlocks(
        baseId: String,
        blocks: List<AgentBlock>,
        finished: Boolean,
        timestampMs: Long,
        usage: MessageUsage? = null,
        isError: Boolean = false,
        into: MutableList<GuiMessageItem>,
    ) {
        val lastTextIndex = blocks.indices.filter { blocks[it] is AgentBlock.Text }.lastOrNull() ?: -1
        blocks.forEachIndexed { index, block ->
            when (block) {
                is AgentBlock.Text -> {
                    val cleanText = block.text
                        .replace("Request aborted", "", ignoreCase = true)
                        .replace("aborted", "", ignoreCase = true)
                        .trim()
                    if (cleanText.isNotBlank()) {
                        into += AssistantResponse(
                            id = "a-$baseId-$index",
                            timestamp = timestampMs,
                            text = cleanText,
                            usage = if (index == lastTextIndex) usage else null,
                            isError = isError,
                        )
                    }
                }

                is AgentBlock.Thinking -> if (block.text.isNotBlank()) {
                    into += ThinkingBlock(
                        id = "t-$baseId-$index",
                        timestamp = timestampMs,
                        content = block.text.trim(),
                        isFinished = finished,
                    )
                }

                is AgentBlock.ToolCall -> {
                    val run = toolRuns[block.toolCallId]
                    into += ToolCallBlock(
                        id = "c-$baseId-$index",
                        timestamp = timestampMs,
                        toolName = block.toolName.ifEmpty { "tool" },
                        summary = block.toolName.ifEmpty { "tool" },
                        arguments = block.rawInput ?: block.argumentsJson.orEmpty(),
                        output = run?.output?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                        // A completed run decides the status even while its
                        // parent message is still streaming; a live run stays
                        // RUNNING until tool_execution_end arrives.
                        status = when {
                            run?.finished == true && run.isError -> ToolStatus.ERROR
                            run?.finished == true -> ToolStatus.SUCCESS
                            !finished -> ToolStatus.RUNNING
                            else -> ToolStatus.SUCCESS
                        },
                    )
                }
            }
        }
    }

    private fun dialogItem(
        request: AgentEvent.ExtensionUiRequest,
    ): QuestionOptionBlock {
        var isMulti = request.method == "multi_select" || request.method == "multiselect"
        var questionTitle = request.title
        var options = when {
            request.method == "confirm" -> listOf(
                InteractiveOption(key = "yes", label = "是"),
                InteractiveOption(key = "no", label = "否"),
            )

            request.method == "select" || isMulti -> request.options.mapIndexed { index, option ->
                InteractiveOption(key = (index + 1).toString(), label = option)
            }

            else -> emptyList()
        }

        // If options is empty (e.g. sent via input dialog method), auto-extract numbered options from title/message
        if (options.isEmpty()) {
            val textToParse = listOfNotNull(request.title, request.message).joinToString("\n")
            val extracted = TerminalChatParser.extractOptionsFromText(textToParse)
            if (extracted.size >= 2) {
                options = extracted
                isMulti = isMulti || TerminalChatParser.detectIsMulti(textToParse, request.placeholder)
                questionTitle = TerminalChatParser.extractTitleOnly(textToParse)
            }
        }

        val effectiveMethod = when {
            options.isNotEmpty() && isMulti -> "multi_select"
            options.isNotEmpty() && (request.method == "input" || request.method == "editor") -> "select"
            else -> request.method
        }

        return QuestionOptionBlock(
            id = "dlg-${request.requestId}",
            question = questionTitle.ifEmpty { "请选择操作方案：" },
            options = options,
            requestId = request.requestId,
            method = effectiveMethod,
            placeholder = if (options.isNotEmpty()) null else request.placeholder,
            isMultiSelect = isMulti,
        )
    }
}
