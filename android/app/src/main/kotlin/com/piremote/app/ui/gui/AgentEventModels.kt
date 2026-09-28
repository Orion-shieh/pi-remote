package com.piremote.app.ui.gui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed view over pi's RPC event stream (`pi --mode rpc`, bridged by the PC
 * agent as `agent_event` control messages). The shapes mirror pi 0.85.x's
 * `modes/rpc/rpc-types.d.ts` and `modes/json-event.js`.
 *
 * The models are plain Kotlin so [GuiAgentState] can be unit-tested on the
 * JVM; only the JSON parsing at the bottom touches org.json.
 */

/** One content block of a pi message. */
sealed interface AgentBlock {
    data class Text(val text: String) : AgentBlock

    data class Thinking(val text: String) : AgentBlock

    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val rawInput: String?,
        val argumentsJson: String?,
    ) : AgentBlock
}

/** A pi message (user, assistant, toolResult) with typed content blocks. */
data class AgentMessage(
    val role: String,
    val toolCallId: String?,
    val isError: Boolean,
    val content: List<AgentBlock>,
    val usage: MessageUsage? = null,
    val isAborted: Boolean = false,
) {
    val text: String
        get() = content.filterIsInstance<AgentBlock.Text>().joinToString("") { it.text }
}

/** One session-file entry delivered in the attach snapshot. */
data class AgentEntry(
    val id: String,
    val type: String,
    val message: AgentMessage?,
    val label: String?,
    /** Entry time as epoch millis (pi stores ISO strings); 0 when unparsable. */
    val timestampMs: Long = 0L,
)

/** Assistant streaming delta carried inside message_update. */
data class AssistantDelta(
    val type: String,
    val contentIndex: Int,
    val deltaText: String?,
    val fullText: String?,
    val toolCallId: String?,
    val toolName: String?,
    val argumentsJson: String?,
)

/** Strips provider prefixes and suffixes (e.g. "anthropic/", " (Command Code)", " - Command Code") from model names/ids. */
fun stripProviderPrefix(text: String?, provider: String?): String? {
    if (text.isNullOrBlank()) return null
    var cleaned = text.trim()
    val prov = provider?.trim()?.lowercase()
    val candidateProviders = mutableListOf<String>()
    if (!prov.isNullOrEmpty()) {
        candidateProviders.add(prov)
    }
    candidateProviders.add("command code")
    candidateProviders.add("commandcode")
    candidateProviders.add("command_code")
    candidateProviders.add("command-code")

    // 1. Strip prefixes
    for (p in candidateProviders) {
        val lower = cleaned.lowercase()
        val prefixes = listOf(
            "$p/",
            "$p / ",
            "$p /",
            "$p:",
            "$p: ",
            "$p - ",
            "$p·",
            "$p ",
        )
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }
    }

    // If text still starts with "provider/" e.g. "anthropic/claude-3-7-sonnet" without explicit provider match
    if (cleaned.contains("/") && !cleaned.startsWith("/")) {
        val before = cleaned.substringBefore("/")
        if (!before.contains(" ") && before.length <= 30) {
            cleaned = cleaned.substringAfter("/").trim()
        }
    }

    // 2. Strip suffixes ("后面"), e.g. " (Command Code)", " - Command Code", " [Command Code]"
    for (p in candidateProviders) {
        val lower = cleaned.lowercase()
        val suffixes = listOf(
            " ($p)",
            " [$p]",
            " - $p",
            " -$p",
            " / $p",
            " · $p",
            "·$p",
        )
        for (suffix in suffixes) {
            if (lower.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length - suffix.length).trim()
                break
            }
        }
    }

    // Also strip generic trailing (provider) or [provider]
    if (cleaned.endsWith(")") && cleaned.contains("(")) {
        val inParen = cleaned.substringAfterLast("(").removeSuffix(")").trim().lowercase()
        if (candidateProviders.any { it == inParen }) {
            cleaned = cleaned.substringBeforeLast("(").trim()
        }
    }
    if (cleaned.endsWith("]") && cleaned.contains("[")) {
        val inBracket = cleaned.substringAfterLast("[").removeSuffix("]").trim().lowercase()
        if (candidateProviders.any { it == inBracket }) {
            cleaned = cleaned.substringBeforeLast("[").trim()
        }
    }

    return cleaned.takeIf { it.isNotBlank() } ?: text
}

/** pi session state snapshot (model, thinking level, streaming flag). */
data class SessionStateInfo(
    val modelProvider: String?,
    val modelId: String?,
    val modelName: String?,
    val thinkingLevel: String?,
    val isStreaming: Boolean,
    val sessionId: String? = null,
    val sessionName: String? = null,
) {
    /** Stable identity used to match the picker selection: "provider/id". */
    val modelKey: String?
        get() = modelProvider?.let { provider ->
            modelId?.let { id ->
                if (id.startsWith("$provider/")) id else "$provider/$id"
            }
        } ?: modelId

    /** Human-facing label; pi model ids often already contain a slash, and the name is clearer. */
    val modelLabel: String?
        get() = modelName?.takeIf { it.isNotBlank() } ?: modelId

    /** Clean model label with provider prefix stripped. */
    val cleanModelLabel: String?
        get() = stripProviderPrefix(modelName?.takeIf { it.isNotBlank() } ?: modelId, modelProvider)
}

/** One selectable model from get_available_models. */
data class PiModelOption(
    val provider: String,
    val id: String,
    val name: String?,
) {
    val key: String get() = if (id.startsWith("$provider/")) id else "$provider/$id"
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: id
    val cleanLabel: String get() = stripProviderPrefix(name?.takeIf { it.isNotBlank() } ?: id, provider) ?: label
    val cleanId: String get() = stripProviderPrefix(id, provider) ?: id
}

/** A slash command pi accepts via prompt (extension / prompt template / skill). */
data class PiSlashCommand(
    val name: String,
    val description: String?,
    val source: String?,
)

sealed interface AgentEvent {
    /**
     * Attach snapshot: pi state plus the FIRST batch of session entries. Long
     * histories continue in [SnapshotBatch] frames until the batch count in
     * `batch` is exhausted; the phone renders on the final batch.
     */
    data class Snapshot(
        val state: SessionStateInfo?,
        val entries: List<AgentEntry>,
        val truncated: Boolean,
        val error: String?,
        /** Agent-computed conversation name (session name or first user message from the FULL file). */
        val name: String? = null,
        val batchIndex: Int = 0,
        val batchTotal: Int = 1,
    ) : AgentEvent

    /** A continuation batch of a chunked snapshot. */
    data class SnapshotBatch(
        val entries: List<AgentEntry>,
        val batchIndex: Int,
        val batchTotal: Int,
    ) : AgentEvent

    data class MessageStart(val message: AgentMessage) : AgentEvent
    data class MessageUpdate(val delta: AssistantDelta) : AgentEvent
    data class MessageEnd(val message: AgentMessage) : AgentEvent

    data class ToolExecutionStart(val toolCallId: String, val toolName: String) : AgentEvent
    data class ToolExecutionUpdate(
        val toolCallId: String,
        val toolName: String?,
        val outputText: String?,
        val isError: Boolean,
    ) : AgentEvent

    data class ToolExecutionEnd(val toolCallId: String) : AgentEvent

    data object AgentStart : AgentEvent
    data object AgentEnd : AgentEvent
    data object AgentSettled : AgentEvent

    /** Interactive dialog from a pi extension: the phone answers with extension_ui_response. */
    data class ExtensionUiRequest(
        val requestId: String,
        val method: String,
        val title: String,
        val message: String?,
        val options: List<String>,
        val placeholder: String?,
        val statusKey: String?,
        val statusText: String?,
        val widgetLines: List<String>,
    ) : AgentEvent

    data class Notify(val message: String, val type: String?) : AgentEvent

    /** Response to a phone-issued command (prompt, abort, set_model, ...). */
    data class CommandResponse(
        val command: String? = null,
        val success: Boolean = false,
        val error: String? = null,
        /** switch_session responses report whether the switch was cancelled. */
        val cancelled: Boolean = false,
        /** get_state / set_model / cycle_model responses carry fresh session state. */
        val state: SessionStateInfo? = null,
        /** get_available_models response payload. */
        val models: List<PiModelOption> = emptyList(),
        /** get_available_thinking_levels response payload. */
        val thinkingLevels: List<String> = emptyList(),
        /** get_commands response payload: slash commands pi accepts via prompt. */
        val commands: List<PiSlashCommand> = emptyList(),
        /** get_session_stats response rendered as a display line. */
        val statsText: String? = null,
        /** get_tree response flattened for display. */
        val treeText: String? = null,
        /** get_last_assistant_text response payload. */
        val lastAssistantText: String? = null,
    ) : AgentEvent

    data class Error(val message: String, val code: String? = null) : AgentEvent

    data class Unknown(val type: String) : AgentEvent
}

object AgentEventJson {

    fun parse(event: JSONObject): AgentEvent = when (val type = event.optString("type")) {
        "snapshot" -> {
            val batch = event.optJSONObject("batch")
            AgentEvent.Snapshot(
                state = parseState(event.optJSONObject("state")),
                entries = event.optJSONArray("entries").mapObjects { parseEntry(it) },
                truncated = event.optBoolean("truncated", false),
                error = event.stringOrNull("error"),
                name = event.stringOrNull("name"),
                batchIndex = batch?.optInt("index", 0) ?: 0,
                batchTotal = batch?.optInt("total", 1) ?: 1,
            )
        }

        "snapshot_batch" -> {
            val batch = event.optJSONObject("batch")
            AgentEvent.SnapshotBatch(
                entries = event.optJSONArray("entries").mapObjects { parseEntry(it) },
                batchIndex = batch?.optInt("index", 0) ?: 0,
                batchTotal = batch?.optInt("total", 1) ?: 1,
            )
        }

        "message_start" -> event.optJSONObject("message")?.let {
            AgentEvent.MessageStart(parseMessage(it))
        } ?: AgentEvent.Unknown(type)

        "message_update" -> event.optJSONObject("assistantMessageEvent")?.let {
            AgentEvent.MessageUpdate(parseDelta(it))
        } ?: AgentEvent.Unknown(type)

        "message_end" -> event.optJSONObject("message")?.let {
            val msg = parseMessage(it)
            val finalMsg = if (msg.usage == null) {
                val outerUsage = parseUsage(event.optJSONObject("usage") ?: event.optJSONObject("tokens") ?: event)
                if (outerUsage != null) msg.copy(usage = outerUsage) else msg
            } else {
                msg
            }
            AgentEvent.MessageEnd(finalMsg)
        } ?: AgentEvent.Unknown(type)

        "tool_execution_start" -> AgentEvent.ToolExecutionStart(
            toolCallId = event.optString("toolCallId"),
            toolName = event.optString("toolName"),
        )

        "tool_execution_update" -> {
            val partial = event.optJSONObject("partialResult")
            AgentEvent.ToolExecutionUpdate(
                toolCallId = event.optString("toolCallId"),
                toolName = event.optString("toolName").takeIf { it.isNotEmpty() },
                outputText = partial?.contentText(),
                isError = partial?.optBoolean("isError", false) ?: false,
            )
        }

        "tool_execution_end" -> AgentEvent.ToolExecutionEnd(event.optString("toolCallId"))

        "agent_start" -> AgentEvent.AgentStart
        "agent_end" -> AgentEvent.AgentEnd
        "agent_settled" -> AgentEvent.AgentSettled

        "extension_ui_request" -> AgentEvent.ExtensionUiRequest(
            requestId = event.optString("id"),
            method = event.optString("method"),
            title = event.optString("title").ifEmpty { "请输入" },
            message = event.optString("message").takeIf { it.isNotEmpty() },
            options = event.optJSONArray("options").toStringList(),
            placeholder = event.optString("placeholder").takeIf { it.isNotEmpty() },
            statusKey = event.optString("statusKey").takeIf { it.isNotEmpty() },
            statusText = event.optString("statusText").takeIf { it.isNotEmpty() },
            widgetLines = event.optJSONArray("widgetLines").toStringList(),
        )

        "notify" -> AgentEvent.Notify(
            message = event.optString("message"),
            type = event.optString("notifyType").takeIf { it.isNotEmpty() },
        )

        "response" -> {
            val command = event.optString("command").takeIf { it.isNotEmpty() }
            val data = event.optJSONObject("data")
            AgentEvent.CommandResponse(
                command = command,
                success = event.optBoolean("success", false),
                error = event.optString("error").takeIf { it.isNotEmpty() },
                cancelled = data?.optBoolean("cancelled", false) ?: false,
                state = when (command) {
                    "get_state" -> parseState(data)
                    "set_model", "cycle_model" -> {
                        val modelObj = data?.optJSONObject("model") ?: data
                        val lvl = data?.stringOrNull("thinkingLevel")
                            ?: data?.stringOrNull("level")
                            ?: data?.stringOrNull("thinking_level")
                            ?: data?.stringOrNull("thinking")
                            ?: modelObj?.stringOrNull("thinkingLevel")
                            ?: modelObj?.stringOrNull("thinking_level")
                            ?: modelObj?.stringOrNull("level")
                            ?: modelObj?.stringOrNull("thinking")
                        parseModelResponse(modelObj, lvl)
                    }
                    "set_thinking_level", "cycle_thinking_level" -> {
                        val lvl = data?.stringOrNull("thinkingLevel")
                            ?: data?.stringOrNull("level")
                            ?: data?.stringOrNull("thinking_level")
                            ?: data?.stringOrNull("thinking")
                            ?: event.stringOrNull("thinkingLevel")
                            ?: event.stringOrNull("level")
                            ?: event.stringOrNull("thinking")
                        if (lvl != null) {
                            SessionStateInfo(
                                modelProvider = null,
                                modelId = null,
                                modelName = null,
                                thinkingLevel = lvl,
                                isStreaming = false,
                            )
                        } else parseState(data)
                    }
                    else -> null
                },
                models = if (command == "get_available_models") {
                    parseAvailableModels(data, event)
                } else {
                    emptyList()
                },
                thinkingLevels = if (command == "get_available_thinking_levels") {
                    data?.optJSONArray("levels")?.toStringList()
                        ?: data?.optJSONArray("thinkingLevels")?.toStringList()
                        ?: event.optJSONArray("data")?.toStringList()
                        ?: event.optJSONArray("levels")?.toStringList()
                        ?: emptyList()
                } else {
                    emptyList()
                },
                commands = if (command == "get_commands") {
                    data?.optJSONArray("commands").mapObjects { obj ->
                        PiSlashCommand(
                            name = obj.optString("name"),
                            description = obj.optString("description").takeIf { it.isNotEmpty() },
                            source = obj.optString("source").takeIf { it.isNotEmpty() },
                        )
                    }
                } else {
                    emptyList()
                },
                statsText = if (command == "get_session_stats") formatStats(data) else null,
                treeText = if (command == "get_tree") formatTree(data) else null,
                lastAssistantText = if (command == "get_last_assistant_text") {
                    data?.optString("text")?.takeIf { it.isNotEmpty() }
                } else {
                    null
                },
            )
        }

        "extension_error" -> AgentEvent.Notify(
            message = "扩展出错：${event.optString("error")}",
            type = "error",
        )

        "error", "agent_error", "stream_error", "message_error", "session_error" -> {
            val errorMsg = event.stringOrNull("error")
                ?: event.stringOrNull("message")
                ?: event.stringOrNull("errorMessage")
                ?: event.optJSONObject("error")?.stringOrNull("message")
                ?: "未知错误"
            AgentEvent.Error(
                message = errorMsg,
                code = event.stringOrNull("code") ?: event.optJSONObject("error")?.stringOrNull("code"),
            )
        }

        else -> AgentEvent.Unknown(type)
    }

    private fun parseDelta(delta: JSONObject): AssistantDelta {
        val type = delta.optString("type")
        val toolCall = delta.optJSONObject("toolCall")
        return AssistantDelta(
            type = type,
            contentIndex = delta.optInt("contentIndex", -1),
            deltaText = delta.optString("delta").takeIf { it.isNotEmpty() },
            fullText = delta.optString("content").takeIf { it.isNotEmpty() },
            toolCallId = delta.optString("id").takeIf { it.isNotEmpty() }
                ?: toolCall?.optString("id")?.takeIf { it.isNotEmpty() },
            toolName = delta.optString("toolName").takeIf { it.isNotEmpty() }
                ?: toolCall?.optString("name")?.takeIf { it.isNotEmpty() },
            argumentsJson = toolCall?.optJSONObject("arguments")?.toString(),
        )
    }

    fun parseUsage(json: JSONObject?): MessageUsage? {
        json ?: return null
        val usageObj = json.optJSONObject("usage")
            ?: json.optJSONObject("tokens")
            ?: json.optJSONObject("tokenUsage")
            ?: json.optJSONObject("token_usage")
            ?: json

        val input = usageObj.optInt("inputTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("input_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("input", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("promptTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("prompt_tokens", -1).takeIf { it >= 0 }
            ?: 0

        val output = usageObj.optInt("outputTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("output_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("output", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("completionTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("completion_tokens", -1).takeIf { it >= 0 }
            ?: 0

        val promptDetails = usageObj.optJSONObject("prompt_tokens_details")
            ?: usageObj.optJSONObject("promptTokensDetails")
            ?: usageObj.optJSONObject("input_tokens_details")
            ?: usageObj.optJSONObject("inputTokensDetails")

        val cache = usageObj.optInt("cacheRead", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cache_read", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("prompt_cache_hit_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("promptCacheHitTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cachedContentTokenCount", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cached_content_token_count", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cacheReadTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cache_read_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cacheReadInputTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cache_read_input_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cached_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cachedTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cache_hit_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cacheHitTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cache_hit", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cacheHit", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("cache", -1).takeIf { it >= 0 }
            ?: promptDetails?.optInt("cached_tokens", -1)?.takeIf { it >= 0 }
            ?: promptDetails?.optInt("cachedTokens", -1)?.takeIf { it >= 0 }
            ?: promptDetails?.optInt("cache_read_input_tokens", -1)?.takeIf { it >= 0 }
            ?: promptDetails?.optInt("cacheReadInputTokens", -1)?.takeIf { it >= 0 }
            ?: promptDetails?.optInt("cache_read", -1)?.takeIf { it >= 0 }
            ?: promptDetails?.optInt("cacheRead", -1)?.takeIf { it >= 0 }
            ?: 0

        val total = usageObj.optInt("totalTokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("total_tokens", -1).takeIf { it >= 0 }
            ?: usageObj.optInt("total", -1).takeIf { it >= 0 }
            ?: (input + output)

        if (input == 0 && output == 0 && cache == 0 && total == 0) return null
        return MessageUsage(
            inputTokens = input,
            outputTokens = output,
            cacheReadTokens = cache,
            totalTokens = total,
        )
    }

    fun parseMessage(json: JSONObject): AgentMessage {
        val blocks = mutableListOf<AgentBlock>()
        val content = json.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> blocks += AgentBlock.Text(block.optString("text"))
                    "thinking" -> blocks += AgentBlock.Thinking(block.optString("thinking"))
                    "toolCall" -> blocks += AgentBlock.ToolCall(
                        toolCallId = block.optString("id").ifEmpty { block.optString("toolCallId") },
                        toolName = block.optString("name").ifEmpty { block.optString("toolName") },
                        rawInput = block.optString("rawInput").takeIf { it.isNotEmpty() },
                        argumentsJson = block.optJSONObject("arguments")?.toString(),
                    )
                }
            }
        }
        val stopReason = json.stringOrNull("stopReason") ?: json.stringOrNull("stop_reason")
        val isAborted = stopReason.equals("aborted", ignoreCase = true)
        val isStopReasonError = stopReason.equals("error", ignoreCase = true)

        val rawErrorText = json.stringOrNull("error")
            ?: json.stringOrNull("errorMessage")
            ?: json.optJSONObject("error")?.stringOrNull("message")
            ?: json.stringOrNull("text")

        val isAbortSignal = isAborted || (rawErrorText != null && (
            rawErrorText.contains("aborted", ignoreCase = true) ||
            rawErrorText.contains("canceled", ignoreCase = true) ||
            rawErrorText.contains("cancelled", ignoreCase = true)
        ))

        val hasErrorKey = (json.has("error") || json.has("errorMessage")) && !isAbortSignal
        val isError = (json.optBoolean("isError", false) ||
            hasErrorKey ||
            json.stringOrNull("role") == "error" ||
            isStopReasonError) && !isAbortSignal

        val errorText = if (isError) {
            rawErrorText ?: json.stringOrNull("text")
        } else {
            null
        }

        if (isError && blocks.filterIsInstance<AgentBlock.Text>().isEmpty() && !errorText.isNullOrBlank()) {
            blocks += AgentBlock.Text(errorText)
        }

        val usage = parseUsage(json.optJSONObject("usage") ?: json.optJSONObject("tokens"))
            ?: if (json.has("input_tokens") || json.has("inputTokens") || json.has("prompt_tokens") || json.has("input")) parseUsage(json) else null
        return AgentMessage(
            role = json.optString("role"),
            toolCallId = json.optString("toolCallId").takeIf { it.isNotEmpty() },
            isError = isError,
            content = blocks,
            usage = usage,
            isAborted = isAbortSignal,
        )
    }

    fun parseEntry(entry: JSONObject): AgentEntry {
        val type = entry.optString("type")
        val rawMessage = if (type == "message") entry.optJSONObject("message")?.let { parseMessage(it) } else null
        val message = if (rawMessage != null && rawMessage.usage == null) {
            val entryUsage = parseUsage(entry.optJSONObject("usage") ?: entry.optJSONObject("tokens"))
            if (entryUsage != null) rawMessage.copy(usage = entryUsage) else rawMessage
        } else {
            rawMessage
        }
        val label = when (type) {
            "model_change" -> null
            "thinking_level_change" -> null
            "compaction" -> "上下文已压缩"
            "branch_summary" -> "分支摘要"
            "session_info" -> entry.optString("name").takeIf { it.isNotEmpty() }?.let { "会话命名 · $it" }
            "error", "agent_error", "stream_error" -> {
                val err = entry.stringOrNull("error")
                    ?: entry.stringOrNull("message")
                    ?: entry.optJSONObject("error")?.stringOrNull("message")
                    ?: "发生错误"
                "错误 · $err"
            }
            else -> null
        }
        return AgentEntry(
            id = entry.stringOrNull("id") ?: "entry_${entry.hashCode()}",
            type = type,
            message = message,
            label = label,
            timestampMs = entry.stringOrNull("timestamp")
                ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(0L) }
                ?: 0L,
        )
    }

    /**
     * Android's org.json returns the literal string "null" for JSON null
     * values via [JSONObject.optString], which would leak into display text
     * ("null · 图形视图"). Absent keys return "" — both map to Kotlin null.
     */
    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotEmpty() && it != "null" }

    private fun parseState(json: JSONObject?): SessionStateInfo? {
        json ?: return null
        val model = json.optJSONObject("model")
        val thinkingObj = json.optJSONObject("thinking")
        val mId = model?.stringOrNull("id") ?: json.stringOrNull("modelId") ?: json.stringOrNull("id")
        val mProvider = model?.stringOrNull("provider")
            ?: json.stringOrNull("provider")
            ?: (if (mId != null && mId.contains("/")) mId.substringBefore("/") else null)
        return SessionStateInfo(
            modelProvider = mProvider,
            modelId = mId,
            modelName = model?.stringOrNull("name") ?: json.stringOrNull("modelName") ?: json.stringOrNull("name"),
            thinkingLevel = json.stringOrNull("thinkingLevel")
                ?: json.stringOrNull("thinking_level")
                ?: json.stringOrNull("level")
                ?: json.stringOrNull("thinking")
                ?: model?.stringOrNull("thinkingLevel")
                ?: model?.stringOrNull("thinking_level")
                ?: model?.stringOrNull("thinking")
                ?: model?.stringOrNull("level")
                ?: thinkingObj?.stringOrNull("level"),
            isStreaming = json.optBoolean("isStreaming", false),
            sessionId = json.stringOrNull("sessionId"),
            sessionName = json.stringOrNull("sessionName"),
        )
    }

    private fun parseModelResponse(model: JSONObject?, thinkingLevel: String?): SessionStateInfo? {
        model ?: return null
        val mId = model.stringOrNull("id")
        val mProvider = model.stringOrNull("provider")
            ?: (if (mId != null && mId.contains("/")) mId.substringBefore("/") else null)
        return SessionStateInfo(
            modelProvider = mProvider,
            modelId = mId,
            modelName = model.stringOrNull("name"),
            thinkingLevel = thinkingLevel
                ?: model.stringOrNull("thinkingLevel")
                ?: model.stringOrNull("thinking_level")
                ?: model.stringOrNull("level")
                ?: model.stringOrNull("thinking"),
            isStreaming = false,
        )
    }

    private fun formatStats(data: JSONObject?): String {
        data ?: return "会话统计不可用"
        val builder = StringBuilder("会话统计 · 消息 ${data.optInt("totalMessages", 0)}")
        builder.append("（用户 ${data.optInt("userMessages", 0)} / 助手 ${data.optInt("assistantMessages", 0)} / 工具 ${data.optInt("toolCalls", 0)}）")
        val tokens = data.optJSONObject("tokens")
        if (tokens != null) {
            builder.append(" · tokens ↑${tokens.optInt("input", 0)} ↓${tokens.optInt("output", 0)}")
        }
        val cost = data.optDouble("cost", 0.0)
        if (cost > 0.0) builder.append(" · $${"%.4f".format(cost)}")
        return builder.toString()
    }

    private fun formatTree(data: JSONObject?): String {
        data ?: return "分支树不可用"
        val lines = mutableListOf<String>()
        fun walk(node: JSONObject?, depth: Int) {
            if (node == null || lines.size >= 40) return
            val entry = node.optJSONObject("entry")
            val type = entry?.optString("type") ?: "?"
            val label = if (type == "message") {
                val role = entry?.optJSONObject("message")?.optString("role") ?: "message"
                "message($role)"
            } else {
                type
            }
            val labelEntry = node.optString("label").takeIf { it.isNotEmpty() }?.let { " 「$it」" } ?: ""
            lines.add("  ".repeat(depth) + if (depth > 0) "└ $label$labelEntry" else label + labelEntry)
            val children = node.optJSONArray("children") ?: return
            for (i in 0 until children.length()) walk(children.optJSONObject(i), depth + 1)
        }
        val roots = data.optJSONArray("tree")
        if (roots != null) {
            for (i in 0 until roots.length()) walk(roots.optJSONObject(i), 0)
        }
        return if (lines.isEmpty()) "会话树为空" else lines.joinToString("\n")
    }

    private fun JSONObject.contentText(): String {
        val content = optJSONArray("content") ?: return ""
        val builder = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> builder.append(block.optString("text"))
            }
        }
        return builder.toString()
    }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
    }

    private fun parseAvailableModels(data: JSONObject?, event: JSONObject): List<PiModelOption> {
        val result = mutableListOf<PiModelOption>()

        fun addOption(provider: String, id: String, name: String?) {
            val cleanId = id.trim()
            if (cleanId.isEmpty() || cleanId == "null") return
            val cleanProv = if (provider.isNotBlank()) provider.trim() else if (cleanId.contains("/")) cleanId.substringBefore("/") else ""
            val actualId = if (provider.isNotBlank() && cleanId.startsWith("$provider/")) cleanId.removePrefix("$provider/") else cleanId
            if (!result.any { it.provider.equals(cleanProv, ignoreCase = true) && it.id.equals(actualId, ignoreCase = true) }) {
                result.add(PiModelOption(cleanProv, actualId, name))
            }
        }

        fun parseArray(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val p = obj.optString("provider").ifEmpty { obj.optString("vendor") }
                    val id = obj.optString("id").ifEmpty { obj.optString("modelId") }.ifEmpty { obj.optString("name") }
                    val name = obj.stringOrNull("name")
                    addOption(p, id, name)
                } else {
                    val s = arr.optString(i)?.takeIf { it.isNotEmpty() && it != "null" } ?: continue
                    if (s.contains("/")) {
                        addOption(s.substringBefore("/"), s.substringAfter("/"), null)
                    } else {
                        addOption("", s, null)
                    }
                }
            }
        }

        // 1. 扫描标准/候选数组
        parseArray(data?.optJSONArray("models"))
        parseArray(event.optJSONArray("models"))
        parseArray(data?.optJSONArray("data"))
        parseArray(data?.optJSONArray("items"))
        if (event.opt("data") is JSONArray) parseArray(event.optJSONArray("data"))

        // 2. 扫描 providers 字典树
        val providersObj = data?.optJSONObject("providers") ?: event.optJSONObject("providers")
        if (providersObj != null) {
            val keys = providersObj.keys()
            while (keys.hasNext()) {
                val prov = keys.next()
                val provVal = providersObj.opt(prov)
                if (provVal is JSONArray) {
                    for (i in 0 until provVal.length()) {
                        val mObj = provVal.optJSONObject(i)
                        if (mObj != null) {
                            val id = mObj.optString("id").ifEmpty { mObj.optString("name") }
                            addOption(prov, id, mObj.optString("name").takeIf { it.isNotEmpty() && it != "null" })
                        } else {
                            val s = provVal.optString(i)
                            if (s.isNotBlank()) addOption(prov, s, null)
                        }
                    }
                } else if (provVal is JSONObject) {
                    val mArr = provVal.optJSONArray("models")
                    if (mArr != null) {
                        for (i in 0 until mArr.length()) {
                            val mObj = mArr.optJSONObject(i)
                            if (mObj != null) {
                                val id = mObj.optString("id").ifEmpty { mObj.optString("name") }
                                addOption(prov, id, mObj.optString("name").takeIf { it.isNotEmpty() && it != "null" })
                            } else {
                                val s = mArr.optString(i)
                                if (s.isNotBlank()) addOption(prov, s, null)
                            }
                        }
                    }
                }
            }
        }

        // 3. 扫描 data 为字典映射的情况 (例如 { "deepseek/deepseek-chat": { ... } })
        val mapObj = (data ?: event.optJSONObject("data"))
        if (mapObj != null && result.isEmpty()) {
            val keys = mapObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == "type" || k == "command" || k == "success") continue
                val item = mapObj.optJSONObject(k)
                if (item != null) {
                    val id = item.optString("id").ifEmpty { k }
                    val p = item.optString("provider").ifEmpty { if (k.contains("/")) k.substringBefore("/") else "" }
                    val name = item.optString("name").takeIf { it.isNotEmpty() && it != "null" }
                    addOption(p, id, name)
                } else if (k.contains("/")) {
                    addOption(k.substringBefore("/"), k.substringAfter("/"), null)
                }
            }
        }

        return result
    }
}
