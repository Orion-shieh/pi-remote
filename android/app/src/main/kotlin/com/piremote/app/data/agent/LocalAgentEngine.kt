package com.piremote.app.data.agent

import android.content.Context
import android.util.Log
import com.piremote.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred

sealed class LocalAgentItem(open val id: String) {
    data class User(
        override val id: String,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val model: String? = null,
        val provider: String? = null,
        val sendType: String? = null, // "prompt", "steer", "follow_up", "btw"
    ) : LocalAgentItem(id)

    data class Assistant(
        override val id: String,
        var text: String,
        var thinking: String? = null,
        var isStreaming: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
    ) : LocalAgentItem(id)

    data class SystemNotice(
        override val id: String,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : LocalAgentItem(id)

    data class Question(
        override val id: String,
        val requestId: String,
        val question: String,
        val options: List<String>,
        val isMultiSelect: Boolean = false,
        val allowCustomInput: Boolean = true,
        var selectedAnswer: String? = null,
        var isAnswered: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
    ) : LocalAgentItem(id)

    data class ToolCall(
        override val id: String,
        val toolName: String,
        val inputArgs: String,
        var output: String = "",
        var isError: Boolean = false,
        var isFinished: Boolean = false,
        var elapsedMs: Long = 0,
        var extraAction: ExtraAction? = null,
        val timestamp: Long = System.currentTimeMillis(),
    ) : LocalAgentItem(id)
}

/**
 * 100% Native Local AI Agent Engine running on Android without Termux or external binaries.
 * Coordinates multi-turn LLM calls, function tool execution, preemptive steering, and UI state updates.
 */
class LocalAgentEngine(private val context: Context) {

    companion object {
        private const val TAG = "LocalAgentEngine"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private const val SYSTEM_PROMPT = """You are Local AI Agent, an autonomous software engineering and generative creation agent running natively inside the user's Android phone.
Environment: Native Android application sandbox (no Termux, no root, no heavy build tools). Web assets should use CDN links or pure vanilla HTML/JS/CSS.

Core Directives:
1. Inspect First & Epistemic Humility: Never guess file contents or assume files exist. Always use `list_directory` or `read_file` to inspect the workspace before making changes.
2. Minimal Diff Editing: Prefer `edit_file_snippet` over rewriting entire files. Preserve existing code structure, indentation, and comments. Ensure valid syntax and balanced brackets/JSON.
3. Interactive Disambiguation (`ask_user_question`): When facing architecture choices, design forks, or ambiguous requirements, do NOT guess. Call `ask_user_question` with 2-4 concrete, distinct options (mark the recommended choice with "(推荐)"). Set `is_multi_select = true` only when choices are non-exclusive.
4. Dynamic Steering Ingestion: Injected steering messages (`【用户实时动态调整指令 / Steering】`) have highest priority. Immediately pivot ongoing plans to align with the user's real-time redirection.
5. Rich Deliverables:
   - Web projects: Call `create_webpage_project` (spawns an internal HTTP server and interactive in-app WebView).
   - Office docs: Call `generate_word_document` (.docx) or `generate_presentation` (.pptx) for rich documents viewable in WPS/Office.
6. Execution & Synthesis: Prioritize action via tools over commentary. After completing tool calls, always provide a complete, well-reasoned final summary in Chinese explaining the solution and how to verify it."""
    }

    private val settings = SettingsStore(context)
    val tools = LocalAgentTools(context, getInitialWorkspace())

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _items = MutableStateFlow<List<LocalAgentItem>>(emptyList())
    val items: StateFlow<List<LocalAgentItem>> = _items.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _activeWebPreview = MutableStateFlow<Pair<String, String>?>(null)
    val activeWebPreview: StateFlow<Pair<String, String>?> = _activeWebPreview.asStateFlow()

    // HDVA Full-Duplex queues & suspension hooks
    private val steerQueue = ConcurrentLinkedQueue<String>()
    private val followUpQueue = ConcurrentLinkedQueue<String>()
    private var activeQuestionDeferred: CompletableDeferred<String>? = null
    private var currentExecutionScope: CoroutineScope? = null

    // OpenAI message history
    private val conversationHistory = mutableListOf<JSONObject>()
    private var currentJob: Job? = null

    init {
        // Initialize conversation with system prompt
        conversationHistory.add(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))

        // Hook interactive questions into coroutine suspension
        tools.onQuestionRequested = { reqId, questionText, optionsList, isMulti, allowCustom ->
            val deferred = CompletableDeferred<String>()
            activeQuestionDeferred = deferred
            val qItem = LocalAgentItem.Question(
                id = UUID.randomUUID().toString(),
                requestId = reqId,
                question = questionText,
                options = optionsList,
                isMultiSelect = isMulti,
                allowCustomInput = allowCustom,
            )
            _items.value = _items.value + qItem
            _statusMessage.value = "等待用户确认选择..."
            onSessionUpdated?.invoke()

            val answer = deferred.await()
            activeQuestionDeferred = null
            _statusMessage.value = "用户已确认选择，继续执行..."
            answer
        }
    }

    private fun getInitialWorkspace(): File {
        val saved = settings.localAgentWorkspace
        if (saved.isNotBlank()) {
            val f = File(saved)
            if (f.exists() || f.mkdirs()) return f
        }
        return LocalAgentTools.getDefaultWorkspace(context)
    }

    var onSessionUpdated: (() -> Unit)? = null

    fun answerQuestion(requestId: String, answer: String) {
        val list = _items.value.toMutableList()
        val idx = list.indexOfLast { it is LocalAgentItem.Question && it.requestId == requestId }
        if (idx != -1) {
            val q = list[idx] as LocalAgentItem.Question
            list[idx] = q.copy(selectedAnswer = answer, isAnswered = true)
            _items.value = list
            onSessionUpdated?.invoke()
        }
        activeQuestionDeferred?.complete(answer)
    }

    fun getItems(): List<LocalAgentItem> = _items.value
    fun getConversationHistory(): List<JSONObject> = synchronized(conversationHistory) { conversationHistory.toList() }

    fun restoreSession(savedItems: List<LocalAgentItem>, savedHistory: List<JSONObject>) {
        _items.value = savedItems
        synchronized(conversationHistory) {
            conversationHistory.clear()
            if (savedHistory.none { it.optString("role") == "system" }) {
                conversationHistory.add(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            }
            conversationHistory.addAll(savedHistory)
        }
    }

    fun stop() {
        currentJob?.cancel()
        activeQuestionDeferred?.cancel()
        activeQuestionDeferred = null
        steerQueue.clear()
        followUpQueue.clear()
        tools.rollbackShadow()

        _isRunning.value = false
        _statusMessage.value = null
        val items = _items.value.toMutableList()
        val lastAssistant = items.filterIsInstance<LocalAgentItem.Assistant>().lastOrNull()
        if (lastAssistant != null) {
            val idx = items.indexOf(lastAssistant)
            if (lastAssistant.text.isBlank()) {
                items.removeAt(idx)
            } else {
                items[idx] = lastAssistant.copy(isStreaming = false)
            }
        }
        val lastNotice = items.lastOrNull() as? LocalAgentItem.SystemNotice
        if (lastNotice == null || lastNotice.text != "已停止生成") {
            items.add(
                LocalAgentItem.SystemNotice(
                    id = UUID.randomUUID().toString(),
                    text = "已停止生成 (已安全回滚临时改动)",
                )
            )
        }
        _items.value = items
        onSessionUpdated?.invoke()
    }

    fun clearSession() {
        currentJob?.cancel()
        activeQuestionDeferred?.cancel()
        activeQuestionDeferred = null
        steerQueue.clear()
        followUpQueue.clear()
        tools.rollbackShadow()

        _isRunning.value = false
        _statusMessage.value = null
        _activeWebPreview.value = null
        _items.value = emptyList()
        synchronized(conversationHistory) {
            conversationHistory.clear()
            conversationHistory.add(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        }
        onSessionUpdated?.invoke()
    }

    fun closeWebPreview() {
        _activeWebPreview.value = null
    }

    /**
     * HDVA Tier-1: Dynamic Steering during agent execution.
     * Injects realtime corrections/redirection into the active cognitive loop.
     */
    fun sendSteer(steerText: String, scope: CoroutineScope) {
        val text = steerText.trim()
        if (text.isBlank()) return
        if (!_isRunning.value) {
            sendPrompt(text, scope)
            return
        }

        steerQueue.offer(text)
        val userItem = LocalAgentItem.User(
            id = UUID.randomUUID().toString(),
            text = text,
            model = settings.localAgentModel.ifBlank { "deepseek-chat" },
            provider = settings.localAgentProviderId.ifBlank { "deepseek" },
            sendType = "steer",
        )
        _items.value = _items.value + userItem
        _statusMessage.value = "已注入实时调整指令，等待当前执行步骤响应..."
        onSessionUpdated?.invoke()

        // If waiting on question card, steer text can unblock it as a custom directive
        activeQuestionDeferred?.complete(text)
    }

    /**
     * HDVA Tier-1: Follow-up task queuing.
     * Enqueues subsequent requests that automatically launch when the current job completes.
     */
    fun sendFollowUp(followUpText: String, scope: CoroutineScope) {
        val text = followUpText.trim()
        if (text.isBlank()) return
        if (!_isRunning.value) {
            sendPrompt(text, scope)
            return
        }

        followUpQueue.offer(text)
        val userItem = LocalAgentItem.User(
            id = UUID.randomUUID().toString(),
            text = text,
            model = settings.localAgentModel.ifBlank { "deepseek-chat" },
            provider = settings.localAgentProviderId.ifBlank { "deepseek" },
            sendType = "follow_up",
        )
        _items.value = _items.value + userItem
        _statusMessage.value = "已排队跟进任务，当前任务完成后自动执行..."
        onSessionUpdated?.invoke()
    }

    /**
     * HDVA Tier-1: Out-of-band side question (/btw).
     * Runs concurrently without polluting the main agent loop or modifying execution state.
     */
    fun handleSidecarBtw(question: String, scope: CoroutineScope) {
        val cleanQ = question.removePrefix("/btw").removePrefix("/side").trim()
        if (cleanQ.isBlank()) return

        val userItem = LocalAgentItem.User(
            id = UUID.randomUUID().toString(),
            text = cleanQ,
            sendType = "btw",
        )
        _items.value = _items.value + userItem
        onSessionUpdated?.invoke()

        scope.launch(Dispatchers.IO) {
            val apiKey = settings.localAgentApiKey
            if (apiKey.isBlank()) {
                val err = LocalAgentItem.Assistant(
                    id = UUID.randomUUID().toString(),
                    text = "【BTW 旁支提问】未配置 API Key，无法解答。",
                    isStreaming = false
                )
                _items.value = _items.value + err
                onSessionUpdated?.invoke()
                return@launch
            }

            try {
                val sidecarHistory = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "你是一个快速旁支回答助手。请简短直接回答用户关于背景、概念或知识的旁支提问，不要调用工具，语气精炼。"))
                    put(JSONObject().put("role", "user").put("content", cleanQ))
                }
                val rawEndpoint = settings.localAgentEndpoint.trim().trimEnd('/')
                val endpoint = if (rawEndpoint.endsWith("/chat/completions")) rawEndpoint else "$rawEndpoint/chat/completions"
                val model = settings.localAgentModel.trim().ifEmpty { "deepseek-chat" }

                val requestJson = JSONObject()
                    .put("model", model)
                    .put("messages", sidecarHistory)
                    .put("max_tokens", 1024)

                val body = requestJson.toString().toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    val parsed = JSONObject(respBody)
                    val choices = parsed.optJSONArray("choices")
                    val answer = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")?.trim().orEmpty()
                    val ansItem = LocalAgentItem.Assistant(
                        id = UUID.randomUUID().toString(),
                        text = "💡 **旁支解答 (/btw)**\n\n$answer",
                        isStreaming = false
                    )
                    _items.value = _items.value + ansItem
                    onSessionUpdated?.invoke()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sidecar /btw request failed", e)
                val err = LocalAgentItem.Assistant(
                    id = UUID.randomUUID().toString(),
                    text = "【BTW 旁支解答异常】: ${e.message}",
                    isStreaming = false
                )
                _items.value = _items.value + err
                onSessionUpdated?.invoke()
            }
        }
    }

    fun sendPrompt(userPrompt: String, scope: CoroutineScope) {
        val prompt = userPrompt.trim()
        if (prompt.isBlank() || _isRunning.value) return

        currentExecutionScope = scope

        // Check if command is /btw
        if (prompt.startsWith("/btw", ignoreCase = true) || prompt.startsWith("/side", ignoreCase = true)) {
            handleSidecarBtw(prompt, scope)
            return
        }

        val userItemId = UUID.randomUUID().toString()
        val userItem = LocalAgentItem.User(
            id = userItemId,
            text = prompt,
            model = settings.localAgentModel.ifBlank { "deepseek-chat" },
            provider = settings.localAgentProviderId.ifBlank { "deepseek" },
            sendType = "prompt",
        )
        _items.value = _items.value + userItem

        synchronized(conversationHistory) {
            conversationHistory.add(JSONObject().put("role", "user").put("content", prompt))
        }
        onSessionUpdated?.invoke()

        currentJob = scope.launch {
            _isRunning.value = true
            _statusMessage.value = "正在思考..."
            try {
                runAgentLoop()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException ||
                    e.message?.contains("Canceled", ignoreCase = true) == true ||
                    e.message?.contains("aborted", ignoreCase = true) == true) {
                    Log.i(TAG, "Agent execution stopped by user")
                    val items = _items.value.toMutableList()
                    val lastAssistant = items.filterIsInstance<LocalAgentItem.Assistant>().lastOrNull()
                    if (lastAssistant != null) {
                        val idx = items.indexOf(lastAssistant)
                        if (lastAssistant.text.isBlank()) {
                            items.removeAt(idx)
                        } else {
                            items[idx] = lastAssistant.copy(isStreaming = false)
                        }
                    }
                    val lastNotice = items.lastOrNull() as? LocalAgentItem.SystemNotice
                    if (lastNotice == null || lastNotice.text != "已停止生成") {
                        items.add(
                            LocalAgentItem.SystemNotice(
                                id = UUID.randomUUID().toString(),
                                text = "已停止生成",
                            )
                        )
                    }
                    _items.value = items
                    onSessionUpdated?.invoke()
                    return@launch
                }
                Log.e(TAG, "Agent execution error", e)
                val errorItem = LocalAgentItem.Assistant(
                    id = UUID.randomUUID().toString(),
                    text = "执行遇到异常: ${e.localizedMessage ?: e.message}\n请检查 API Key 和网络连接是否配置正确。",
                    isStreaming = false
                )
                _items.value = _items.value + errorItem
            } finally {
                _isRunning.value = false
                _statusMessage.value = null
                onSessionUpdated?.invoke()

                // Check queued follow-up tasks
                val nextFollowUp = followUpQueue.poll()
                if (nextFollowUp != null) {
                    sendPrompt(nextFollowUp, scope)
                }
            }
        }
    }

    private suspend fun runAgentLoop() = withContext(Dispatchers.IO) {
        val apiKey = settings.localAgentApiKey
        if (apiKey.isBlank()) {
            val configNotice = LocalAgentItem.Assistant(
                id = UUID.randomUUID().toString(),
                text = "未配置本地 Agent API Key。请点击右上角「模型配置」按钮输入您的 API 密钥（支持 DeepSeek / Gemini / OpenAI / 硅基流动等任意兼容端点）。",
                isStreaming = false
            )
            _items.value = _items.value + configNotice
            return@withContext
        }

        var maxTurns = 25
        while (maxTurns-- > 0) {
            // HDVA Tier-1: Process any queued steering messages before the next planning step
            var steerMsg = steerQueue.poll()
            while (steerMsg != null) {
                synchronized(conversationHistory) {
                    conversationHistory.add(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "【用户实时动态调整指令 / Steering】: $steerMsg\n请严格基于用户最新调整的意图，立即校准行动规划并继续执行。")
                    )
                }
                _statusMessage.value = "已响应实时调整指令..."
                steerMsg = steerQueue.poll()
            }

            _statusMessage.value = "正在调用模型生成规划..."
            val responseObj = callLlmApi(apiKey) ?: break

            val choices = responseObj.optJSONArray("choices") ?: break
            if (choices.length() == 0) break
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val role = message.optString("role", "assistant")
            val content = message.optString("content", "")
            val reasoning = message.optString("reasoning_content", "").ifEmpty { null }
            val toolCalls = message.optJSONArray("tool_calls")

            // Add assistant response to history (cleaned to prevent invalid tool_calls: null or extra provider fields)
            val cleanAssistantMsg = JSONObject().apply {
                put("role", role)
                put("content", content)
                if (toolCalls != null && toolCalls.length() > 0) {
                    put("tool_calls", toolCalls)
                }
            }
            synchronized(conversationHistory) {
                conversationHistory.add(cleanAssistantMsg)
            }

            // Emit assistant message card if there is text content or thinking
            if (content.isNotBlank() || reasoning != null) {
                val assistantItem = LocalAgentItem.Assistant(
                    id = UUID.randomUUID().toString(),
                    text = content,
                    thinking = reasoning,
                    isStreaming = false
                )
                _items.value = _items.value + assistantItem
                onSessionUpdated?.invoke()
            }

            // Check if there are tool calls to execute
            if (toolCalls != null && toolCalls.length() > 0) {
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.optJSONObject(i) ?: continue
                    val callId = tc.optString("id", UUID.randomUUID().toString())
                    val fn = tc.optJSONObject("function") ?: continue
                    val fnName = fn.optString("name", "")
                    if (fnName.isBlank()) continue
                    val fnArgs = fn.optString("arguments", "{}")

                    val toolItem = LocalAgentItem.ToolCall(
                        id = callId,
                        toolName = fnName,
                        inputArgs = fnArgs,
                    )
                    _items.value = _items.value + toolItem
                    _statusMessage.value = "执行工具: $fnName..."

                    val startTime = System.currentTimeMillis()
                    val result = tools.executeTool(fnName, fnArgs)
                    val elapsed = System.currentTimeMillis() - startTime

                    // Update tool item state
                    toolItem.output = result.output
                    toolItem.isError = result.isError
                    toolItem.isFinished = true
                    toolItem.elapsedMs = elapsed
                    toolItem.extraAction = result.extraAction

                    // Trigger UI actions
                    when (val action = result.extraAction) {
                        is ExtraAction.WebPreview -> {
                            _activeWebPreview.value = Pair(action.serverUrl, action.filePath)
                        }
                        else -> {}
                    }

                    // Force recompose flow
                    _items.value = ArrayList(_items.value)

                    // Append tool result into history
                    val toolResultMsg = JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", callId)
                        .put("name", fnName)
                        .put("content", result.output)
                    synchronized(conversationHistory) {
                        conversationHistory.add(toolResultMsg)
                    }
                    onSessionUpdated?.invoke()
                }
                // Continue loop for the model to digest tool results
                continue
            } else {
                // No more tool calls, turn completed
                break
            }
        }

        // Guaranteed Final Synthesis:
        // If the agent performed tool calls and the last item in the conversation is a ToolCall
        // (meaning the model finished tool execution but did not generate a final textual answer/summary),
        // execute a guaranteed synthesis call with tools disabled (tool_choice = "none") so the model
        // MUST output its final analysis and conclusion!
        val lastItem = _items.value.lastOrNull()
        if (lastItem is LocalAgentItem.ToolCall) {
            _statusMessage.value = "正在输出最终结果与总结..."
            try {
                synchronized(conversationHistory) {
                    conversationHistory.add(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "请根据上述工具的执行结果与输出内容，给出详细、完整的最终分析、解释与结论总结。")
                    )
                }
                val finalResp = callLlmApi(apiKey, allowTools = false)
                val finalChoices = finalResp?.optJSONArray("choices")
                val finalMsg = finalChoices?.optJSONObject(0)?.optJSONObject("message")
                val finalText = finalMsg?.optString("content", "")?.trim().orEmpty()
                val finalReasoning = finalMsg?.optString("reasoning_content", "")?.ifEmpty { null }

                if (finalText.isNotBlank() || finalReasoning != null) {
                    val finalItem = LocalAgentItem.Assistant(
                        id = UUID.randomUUID().toString(),
                        text = finalText.ifBlank { "执行完成。详细过程请展开上方「执行详情」查看。" },
                        thinking = finalReasoning,
                        isStreaming = false
                    )
                    _items.value = _items.value + finalItem
                    synchronized(conversationHistory) {
                        conversationHistory.add(
                            JSONObject().put("role", "assistant").put("content", finalItem.text)
                        )
                    }
                    onSessionUpdated?.invoke()
                } else {
                    val fallback = LocalAgentItem.Assistant(
                        id = UUID.randomUUID().toString(),
                        text = "上述操作已执行完毕。详细执行输出记录请展开上方「执行详情」查看。",
                        isStreaming = false
                    )
                    _items.value = _items.value + fallback
                    onSessionUpdated?.invoke()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Final synthesis turn failed", e)
                val fallback = LocalAgentItem.Assistant(
                    id = UUID.randomUUID().toString(),
                    text = "工具执行完毕。详细输出记录请展开上方「执行详情」查看。（结果总结生成异常: ${e.message}）",
                    isStreaming = false
                )
                _items.value = _items.value + fallback
                onSessionUpdated?.invoke()
            }
        }

        // HDVA Tier-2: Speculative verification completed successfully; commit shadow writes
        tools.commitShadow()
    }

    private fun callLlmApi(apiKey: String, allowTools: Boolean = true): JSONObject? {
        val rawEndpoint = settings.localAgentEndpoint.trim().trimEnd('/')
        val endpoint = if (rawEndpoint.endsWith("/chat/completions")) {
            rawEndpoint
        } else {
            "$rawEndpoint/chat/completions"
        }

        val model = settings.localAgentModel.trim().ifEmpty { "deepseek-chat" }

        // Sanitize conversation messages:
        // DeepSeek R1 and OpenAI APIs fail with 400 Bad Request if "reasoning_content"
        // or non-standard fields are sent back in the messages array.
        val cleanMessages = JSONArray()
        synchronized(conversationHistory) {
            for (i in 0 until conversationHistory.size) {
                val msg = conversationHistory[i]
                val cleanMsg = JSONObject()
                cleanMsg.put("role", msg.optString("role", "user"))
                val content = msg.opt("content")
                if (content != null && content != JSONObject.NULL) {
                    cleanMsg.put("content", content)
                } else {
                    cleanMsg.put("content", "")
                }
                val name = msg.optString("name", "")
                if (name.isNotBlank()) cleanMsg.put("name", name)
                val toolCallId = msg.optString("tool_call_id", "")
                if (toolCallId.isNotBlank()) cleanMsg.put("tool_call_id", toolCallId)

                // Safely extract tool_calls: avoid crash if tool_calls is JSONObject.NULL or empty
                val toolCallsArr = msg.optJSONArray("tool_calls")
                if (toolCallsArr != null && toolCallsArr.length() > 0) {
                    cleanMsg.put("tool_calls", toolCallsArr)
                }
                cleanMessages.put(cleanMsg)
            }
        }

        val requestJson = JSONObject()
            .put("model", model)
            .put("messages", cleanMessages)
            .put("max_tokens", 8192)

        if (allowTools) {
            requestJson.put("tools", tools.getToolsSpecification())
            requestJson.put("tool_choice", "auto")
        } else {
            requestJson.put("tool_choice", "none")
        }

        val body = requestJson.toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val parsedError = try {
                    val errJson = JSONObject(respBody)
                    val errObj = errJson.optJSONObject("error")
                    errObj?.optString("message") ?: errJson.optString("error", respBody)
                } catch (_: Exception) {
                    respBody
                }
                throw RuntimeException("API 调用失败 [HTTP ${response.code}]: $parsedError")
            }
            return JSONObject(respBody)
        }
    }
}
