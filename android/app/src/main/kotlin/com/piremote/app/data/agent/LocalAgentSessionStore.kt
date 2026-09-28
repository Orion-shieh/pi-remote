package com.piremote.app.data.agent

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Data container for a serialized AI Agent session.
 */
data class SerializedAiSession(
    val id: String,
    val title: String,
    val workingDir: String,
    val startTime: Long,
    val lastActiveTime: Long,
    val alive: Boolean,
    val modelName: String,
    val items: List<LocalAgentItem>,
    val conversationHistory: List<JSONObject>,
)

/**
 * Robust JSON file persistence store for Local AI Agent conversations.
 * Stores each session in context.filesDir/agent_sessions/<id>.json
 */
class LocalAgentSessionStore(private val context: Context) {

    companion object {
        private const val TAG = "AgentSessionStore"
        private const val SESSIONS_DIR = "agent_sessions"
    }

    private val sessionsDir: File by lazy {
        File(context.filesDir, SESSIONS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    @Synchronized
    fun saveSession(session: SerializedAiSession) {
        if (session.items.isEmpty()) {
            val file = File(sessionsDir, "${session.id}.json")
            if (file.exists()) {
                file.delete()
            }
            return
        }
        try {
            val file = File(sessionsDir, "${session.id}.json")
            val root = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("workingDir", session.workingDir)
                put("startTime", session.startTime)
                put("lastActiveTime", session.lastActiveTime)
                put("alive", session.alive)
                put("modelName", session.modelName)

                // Conversation history for OpenAI API
                val histArray = JSONArray()
                session.conversationHistory.forEach { histArray.put(it) }
                put("conversationHistory", histArray)

                // UI message items
                val itemsArray = JSONArray()
                session.items.forEach { item ->
                    itemsArray.put(serializeItem(item))
                }
                put("items", itemsArray)
            }
            file.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save AI session ${session.id}", e)
        }
    }

    @Synchronized
    fun loadAllSessions(): List<SerializedAiSession> {
        val list = mutableListOf<SerializedAiSession>()
        val files = sessionsDir.listFiles { _, name -> name.endsWith(".json") } ?: return list

        for (file in files) {
            try {
                val text = file.readText(Charsets.UTF_8)
                val obj = JSONObject(text)
                val id = obj.getString("id")
                val title = obj.optString("title", "AI 会话")
                val workingDir = obj.optString("workingDir", "")
                val startTime = obj.optLong("startTime", file.lastModified())
                val lastActiveTime = obj.optLong("lastActiveTime", startTime)
                val alive = obj.optBoolean("alive", true)
                val modelName = obj.optString("modelName", "")

                // Conversation history
                val histArray = obj.optJSONArray("conversationHistory") ?: JSONArray()
                val history = mutableListOf<JSONObject>()
                for (i in 0 until histArray.length()) {
                    val entry = histArray.optJSONObject(i)
                    if (entry != null) history.add(entry)
                }

                // Items
                val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<LocalAgentItem>()
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.optJSONObject(i) ?: continue
                    deserializeItem(itemObj)?.let { items.add(it) }
                }

                if (items.isEmpty()) {
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                    continue
                }

                list.add(
                    SerializedAiSession(
                        id = id,
                        title = title,
                        workingDir = workingDir,
                        startTime = startTime,
                        lastActiveTime = lastActiveTime,
                        alive = alive,
                        modelName = modelName,
                        items = items,
                        conversationHistory = history,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse session file ${file.name}", e)
            }
        }
        return list.sortedByDescending { it.lastActiveTime }
    }

    @Synchronized
    fun deleteSession(sessionId: String) {
        try {
            val file = File(sessionsDir, "$sessionId.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session $sessionId", e)
        }
    }

    private fun serializeItem(item: LocalAgentItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        when (item) {
            is LocalAgentItem.User -> {
                obj.put("type", "user")
                obj.put("text", item.text)
                obj.put("timestamp", item.timestamp)
                if (!item.model.isNullOrBlank()) {
                    obj.put("model", item.model)
                }
                if (!item.provider.isNullOrBlank()) {
                    obj.put("provider", item.provider)
                }
                if (!item.sendType.isNullOrBlank()) {
                    obj.put("sendType", item.sendType)
                }
            }
            is LocalAgentItem.Question -> {
                obj.put("type", "question")
                obj.put("requestId", item.requestId)
                obj.put("question", item.question)
                val optsArray = JSONArray()
                item.options.forEach { optsArray.put(it) }
                obj.put("options", optsArray)
                obj.put("isMultiSelect", item.isMultiSelect)
                obj.put("allowCustomInput", item.allowCustomInput)
                if (!item.selectedAnswer.isNullOrBlank()) {
                    obj.put("selectedAnswer", item.selectedAnswer)
                }
                obj.put("isAnswered", item.isAnswered)
                obj.put("timestamp", item.timestamp)
            }
            is LocalAgentItem.Assistant -> {
                obj.put("type", "assistant")
                obj.put("text", item.text)
                obj.put("thinking", item.thinking)
                obj.put("isStreaming", false) // never persist active streaming flag
                obj.put("timestamp", item.timestamp)
            }
            is LocalAgentItem.SystemNotice -> {
                obj.put("type", "system_notice")
                obj.put("text", item.text)
                obj.put("timestamp", item.timestamp)
            }
            is LocalAgentItem.ToolCall -> {
                obj.put("type", "tool")
                obj.put("toolName", item.toolName)
                obj.put("inputArgs", item.inputArgs)
                obj.put("output", item.output)
                obj.put("isError", item.isError)
                obj.put("isFinished", item.isFinished)
                obj.put("elapsedMs", item.elapsedMs)
                obj.put("timestamp", item.timestamp)
                item.extraAction?.let { action ->
                    val actionObj = JSONObject()
                    when (action) {
                        is ExtraAction.WebPreview -> {
                            actionObj.put("type", "web_preview")
                            actionObj.put("serverUrl", action.serverUrl)
                            actionObj.put("filePath", action.filePath)
                        }
                        is ExtraAction.OfficeDocument -> {
                            actionObj.put("type", "office_doc")
                            actionObj.put("filePath", action.filePath)
                            actionObj.put("mimeType", action.mimeType)
                        }
                        is ExtraAction.FileCreated -> {
                            actionObj.put("type", "file_created")
                            actionObj.put("filePath", action.filePath)
                        }
                        is ExtraAction.QuestionAnswered -> {
                            actionObj.put("type", "question_answered")
                            actionObj.put("question", action.question)
                            actionObj.put("answer", action.answer)
                        }
                    }
                    obj.put("extraAction", actionObj)
                }
            }
        }
        return obj
    }

    private fun deserializeItem(obj: JSONObject): LocalAgentItem? {
        val id = obj.optString("id", "")
        val type = obj.optString("type", "")
        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

        return when (type) {
            "user" -> {
                LocalAgentItem.User(
                    id = id,
                    text = obj.optString("text", ""),
                    timestamp = timestamp,
                    model = obj.optString("model").takeIf { it.isNotBlank() },
                    provider = obj.optString("provider").takeIf { it.isNotBlank() },
                    sendType = obj.optString("sendType").takeIf { it.isNotBlank() },
                )
            }
            "question" -> {
                val optsArray = obj.optJSONArray("options") ?: JSONArray()
                val optionsList = mutableListOf<String>()
                for (i in 0 until optsArray.length()) {
                    optionsList.add(optsArray.optString(i))
                }
                LocalAgentItem.Question(
                    id = id,
                    requestId = obj.optString("requestId", ""),
                    question = obj.optString("question", ""),
                    options = optionsList,
                    isMultiSelect = obj.optBoolean("isMultiSelect", false),
                    allowCustomInput = obj.optBoolean("allowCustomInput", true),
                    selectedAnswer = obj.optString("selectedAnswer").takeIf { it.isNotBlank() },
                    isAnswered = obj.optBoolean("isAnswered", false),
                    timestamp = timestamp,
                )
            }
            "assistant" -> {
                LocalAgentItem.Assistant(
                    id = id,
                    text = obj.optString("text", ""),
                    thinking = obj.optString("thinking").takeIf { it.isNotBlank() },
                    isStreaming = false,
                    timestamp = timestamp,
                )
            }
            "tool" -> {
                val actionObj = obj.optJSONObject("extraAction")
                val extraAction: ExtraAction? = actionObj?.let {
                    when (it.optString("type")) {
                        "web_preview" -> ExtraAction.WebPreview(
                            serverUrl = it.optString("serverUrl", ""),
                            filePath = it.optString("filePath", ""),
                        )
                        "office_doc" -> ExtraAction.OfficeDocument(
                            filePath = it.optString("filePath", ""),
                            mimeType = it.optString("mimeType", ""),
                        )
                        "file_created" -> ExtraAction.FileCreated(
                            filePath = it.optString("filePath", ""),
                        )
                        "question_answered" -> ExtraAction.QuestionAnswered(
                            question = it.optString("question", ""),
                            answer = it.optString("answer", ""),
                        )
                        else -> null
                    }
                }

                LocalAgentItem.ToolCall(
                    id = id,
                    toolName = obj.optString("toolName", ""),
                    inputArgs = obj.optString("inputArgs", ""),
                    output = obj.optString("output", ""),
                    isError = obj.optBoolean("isError", false),
                    isFinished = obj.optBoolean("isFinished", true),
                    elapsedMs = obj.optLong("elapsedMs", 0),
                    extraAction = extraAction,
                    timestamp = timestamp,
                )
            }
            "system_notice", "system" -> {
                LocalAgentItem.SystemNotice(
                    id = id,
                    text = obj.optString("text", ""),
                    timestamp = timestamp,
                )
            }
            else -> null
        }
    }
}
