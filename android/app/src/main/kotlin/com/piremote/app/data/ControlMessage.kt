package com.piremote.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Typed view over the JSON control messages exchanged with the relay. */
sealed interface ControlMessage {

    data class HelloOk(
        val role: String,
        val id: String,
        val agentId: String?,
        val agents: List<String>,
    ) : ControlMessage

    data class Preset(val id: String, val name: String, val cwd: String, val description: String)

    data class Settings(val presets: List<Preset>) : ControlMessage

    data class SessionInfo(
        val sid: String,
        val name: String,
        val preset: String,
        val kind: String,
        val piName: String?,
        val cwd: String,
        val running: Boolean,
        val pid: Int,
    )

    data class Sessions(val list: List<SessionInfo>) : ControlMessage

    data class Created(
        val sid: String,
        val name: String,
        val preset: String,
        val kind: String,
        val cwd: String,
        val cols: Int,
        val rows: Int,
    ) : ControlMessage

    data class Exit(val sid: String, val code: Int) : ControlMessage

    data class AgentOnline(val id: String?) : ControlMessage
    data object AgentOffline : ControlMessage
    data class ReplayGap(val sid: String) : ControlMessage

    /**
     * One structured pi event from an RPC session, forwarded verbatim as JSON.
     * The GUI state machine (`ui.gui.AgentEventJson` / `GuiAgentState`) owns
     * the payload; the data layer never interprets it.
     */
    data class AgentEvent(val sid: String, val event: JSONObject) : ControlMessage

    data class Error(val code: String, val message: String?, val sid: String? = null) : ControlMessage
    data class DirListing(val path: String, val dirs: List<String>, val files: List<String> = emptyList()) : ControlMessage
    data class FileContent(val path: String, val content: String, val error: String? = null) : ControlMessage
    data class AuthUpdated(val provider: String, val success: Boolean, val error: String? = null) : ControlMessage
    data class ModelsDetected(val provider: String, val success: Boolean, val models: List<String> = emptyList(), val error: String? = null) : ControlMessage
    data class ModelDeleted(val provider: String, val modelId: String, val success: Boolean, val error: String? = null) : ControlMessage
    data class PiSessionDeleted(val file: String, val id: String, val success: Boolean, val error: String? = null) : ControlMessage
    data class ModelsList(val models: List<com.piremote.app.ui.gui.PiModelOption>) : ControlMessage

    data class Unknown(val type: String) : ControlMessage

    companion object {

        fun parse(raw: String): ControlMessage? = try {
            fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }

        private fun fromJson(json: JSONObject): ControlMessage = when (val type = json.optString("t")) {
            "hello_ok" -> HelloOk(
                role = json.optString("role"),
                id = json.optString("id"),
                agentId = json.optString("agentId").takeIf { it.isNotEmpty() && it != "null" },
                agents = json.optJSONArray("agents").toStringList(),
            )

            "settings" -> Settings(
                presets = json.optJSONArray("presets").mapObjects { obj ->
                    Preset(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        cwd = obj.optString("cwd"),
                        description = obj.optString("description"),
                    )
                },
            )

            "sessions" -> Sessions(
                list = json.optJSONArray("list").mapObjects { obj ->
                    // org.json maps JSON null to the literal "null" string.
                    fun stringOrNull(key: String): String? =
                        obj.optString(key).takeIf { it.isNotEmpty() && it != "null" }
                    SessionInfo(
                        sid = obj.optString("sid"),
                        name = obj.optString("name"),
                        preset = obj.optString("preset"),
                        kind = obj.optString("kind", "pty"),
                        piName = stringOrNull("piName"),
                        cwd = obj.optString("cwd"),
                        running = obj.optBoolean("running", true),
                        pid = obj.optInt("pid", 0),
                    )
                },
            )

            "created" -> Created(
                sid = json.optString("sid"),
                name = json.optString("name"),
                preset = json.optString("preset"),
                kind = json.optString("kind", "pty"),
                cwd = json.optString("cwd"),
                cols = json.optInt("cols", 80),
                rows = json.optInt("rows", 24),
            )

            "exit" -> Exit(json.optString("sid"), json.optInt("code", 0))
            "agent_event" -> AgentEvent(
                sid = json.optString("sid"),
                event = json.optJSONObject("event") ?: JSONObject(),
            )
            "agent_online" -> AgentOnline(json.optString("id").takeIf { it.isNotEmpty() })
            "agent_offline" -> AgentOffline
            "replay_gap" -> ReplayGap(json.optString("sid"))
            "error" -> Error(
                code = json.optString("code"),
                message = json.optString("message").takeIf { it.isNotEmpty() },
                sid = json.optString("sid").takeIf { it.isNotEmpty() },
            )
            "dir_listing" -> {
                val dirsArray = json.optJSONArray("dirs") ?: json.optJSONArray("directories")
                val filesArray = json.optJSONArray("files") ?: json.optJSONArray("fileList")
                DirListing(
                    path = json.optString("path"),
                    dirs = dirsArray.toStringList(),
                    files = filesArray.toStringList(),
                )
            }
            "file_content" -> FileContent(
                path = json.optString("path"),
                content = json.optString("content"),
                error = json.optString("error").takeIf { it.isNotEmpty() && it != "null" },
            )
            "auth_updated" -> AuthUpdated(
                provider = json.optString("provider"),
                success = json.optBoolean("success", true),
                error = json.optString("error").takeIf { it.isNotEmpty() && it != "null" },
            )
            "models_detected" -> {
                val modelsArr = json.optJSONArray("models") ?: json.optJSONArray("data")
                ModelsDetected(
                    provider = json.optString("provider"),
                    success = json.optBoolean("success", true),
                    models = modelsArr.toStringList(),
                    error = json.optString("error").takeIf { it.isNotEmpty() && it != "null" },
                )
            }
            "model_deleted" -> ModelDeleted(
                provider = json.optString("provider"),
                modelId = json.optString("modelId"),
                success = json.optBoolean("success", true),
                error = json.optString("error").takeIf { it.isNotEmpty() && it != "null" },
            )
            "pi_session_deleted" -> PiSessionDeleted(
                file = json.optString("file"),
                id = json.optString("id"),
                success = json.optBoolean("success", true),
                error = json.optString("error").takeIf { it.isNotEmpty() && it != "null" },
            )
            "models_list" -> {
                val arr = json.optJSONArray("models") ?: json.optJSONArray("data")
                val list = (0 until (arr?.length() ?: 0)).mapNotNull { idx ->
                    val obj = arr?.optJSONObject(idx)
                    if (obj != null) {
                        val provider = obj.optString("provider").ifEmpty { obj.optString("vendor") }
                        val id = obj.optString("id").ifEmpty { obj.optString("modelId") }.ifEmpty { obj.optString("name") }
                        val name = obj.optString("name").takeIf { it.isNotEmpty() && it != "null" }
                        if (id.isNotEmpty()) com.piremote.app.ui.gui.PiModelOption(provider, id, name) else null
                    } else {
                        val str = arr?.optString(idx)?.takeIf { it.isNotEmpty() && it != "null" }
                        if (str != null) {
                            val prov = if (str.contains("/")) str.substringBefore("/") else ""
                            val id = if (str.contains("/")) str.substringAfter("/") else str
                            com.piremote.app.ui.gui.PiModelOption(prov, id, null)
                        } else null
                    }
                }
                ModelsList(list)
            }
            else -> Unknown(type)
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { idx ->
                val obj = optJSONObject(idx)
                if (obj != null) {
                    obj.optString("name").takeIf { it.isNotEmpty() && it != "null" }
                        ?: obj.optString("path").takeIf { it.isNotEmpty() && it != "null" }
                } else {
                    optString(idx).takeIf { it.isNotEmpty() && it != "null" }
                }
            }
        }

        private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
        }
    }
}

/** Builders for the messages the client sends. */
object ControlRequests {

    fun list(): String = JSONObject().put("t", "list").toString()

    fun create(
        preset: String,
        cols: Int,
        rows: Int,
        cwd: String? = null,
        args: List<String>? = null,
        command: String? = null,
    ): String {
        val json = JSONObject()
            .put("t", "create")
            .put("preset", preset)
            .put("cols", cols)
            .put("rows", rows)
        if (!cwd.isNullOrBlank()) json.put("cwd", cwd)
        if (!args.isNullOrEmpty()) {
            val arr = org.json.JSONArray()
            args.forEach { arr.put(it) }
            json.put("args", arr)
        }
        if (!command.isNullOrBlank()) json.put("command", command)
        return json.toString()
    }

    fun attach(sid: String, lastSeq: Int): String = JSONObject()
        .put("t", "attach")
        .put("sid", sid)
        .put("lastSeq", lastSeq)
        .toString()

    fun detach(sid: String): String =
        JSONObject().put("t", "detach").put("sid", sid).toString()

    fun kill(sid: String): String =
        JSONObject().put("t", "kill").put("sid", sid).toString()

    fun resize(sid: String, cols: Int, rows: Int): String = JSONObject()
        .put("t", "resize")
        .put("sid", sid)
        .put("cols", cols)
        .put("rows", rows)
        .toString()

    /** Forwards a pi RPC command (prompt, abort, extension_ui_response, ...) to the agent. */
    fun agentCommand(sid: String, command: JSONObject): String = JSONObject()
        .put("t", "agent_command")
        .put("sid", sid)
        .put("command", command)
        .toString()

    /** Asks the agent to scan pi's session files for the session picker (agent-side, not a pi command). */
    fun listPiSessions(sid: String, cwd: String?): String {
        val json = JSONObject()
            .put("t", "list_pi_sessions")
            .put("sid", sid)
        if (!cwd.isNullOrBlank()) json.put("cwd", cwd)
        return json.toString()
    }

    /** Lists the subdirectories of a path on the PC (drives when path is empty). */
    fun listDirs(path: String): String = JSONObject()
        .put("t", "list_dirs")
        .put("path", path)
        .toString()

    /** Asks the agent to read the text content of a file on the PC. */
    fun readFile(path: String): String = JSONObject()
        .put("t", "read_file")
        .put("path", path)
        .toString()

    /** Updates Pi Agent's ~/.pi/agent/auth.json on the PC. */
    fun updateAuth(
        provider: String,
        apiKey: String,
        baseUrl: String? = null,
        models: List<String> = emptyList(),
    ): String {
        val json = JSONObject()
            .put("t", "update_auth")
            .put("provider", provider)
            .put("apiKey", apiKey)
        if (!baseUrl.isNullOrBlank()) json.put("baseUrl", baseUrl)
        if (models.isNotEmpty()) {
            val arr = JSONArray()
            models.distinct().forEach { arr.put(it) }
            json.put("models", arr)
        }
        return json.toString()
    }

    /** Requests the PC Agent to detect available models for a provider/endpoint. */
    fun detectModels(
        provider: String,
        apiKey: String,
        baseUrl: String? = null,
    ): String {
        val json = JSONObject()
            .put("t", "detect_models")
            .put("provider", provider)
            .put("apiKey", apiKey)
        if (!baseUrl.isNullOrBlank()) json.put("baseUrl", baseUrl)
        return json.toString()
    }

    /** Requests the PC Agent to delete a model from ~/.pi/agent/models.json or auth.json */
    fun deleteModel(provider: String, modelId: String): String = JSONObject()
        .put("t", "delete_model")
        .put("provider", provider)
        .put("modelId", modelId)
        .toString()

    /** Requests the PC Agent to delete a pi session file permanently */
    fun deletePiSession(file: String, id: String): String = JSONObject()
        .put("t", "delete_pi_session")
        .put("file", file)
        .put("id", id)
        .toString()

    /** Requests the PC Agent to read the latest models from ~/.pi/agent/models.json & auth.json */
    fun getModels(): String = JSONObject()
        .put("t", "get_models")
        .toString()
}
