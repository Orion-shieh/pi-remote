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
        val cwd: String,
        val running: Boolean,
        val pid: Int,
    )

    data class Sessions(val list: List<SessionInfo>) : ControlMessage

    data class Created(
        val sid: String,
        val name: String,
        val preset: String,
        val cwd: String,
        val cols: Int,
        val rows: Int,
    ) : ControlMessage

    data class Exit(val sid: String, val code: Int) : ControlMessage

    data class AgentOnline(val id: String?) : ControlMessage
    data object AgentOffline : ControlMessage
    data class ReplayGap(val sid: String) : ControlMessage
    data class Error(val code: String, val message: String?, val sid: String? = null) : ControlMessage
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
                    SessionInfo(
                        sid = obj.optString("sid"),
                        name = obj.optString("name"),
                        preset = obj.optString("preset"),
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
                cwd = json.optString("cwd"),
                cols = json.optInt("cols", 80),
                rows = json.optInt("rows", 24),
            )

            "exit" -> Exit(json.optString("sid"), json.optInt("code", 0))
            "agent_online" -> AgentOnline(json.optString("id").takeIf { it.isNotEmpty() })
            "agent_offline" -> AgentOffline
            "replay_gap" -> ReplayGap(json.optString("sid"))
            "error" -> Error(
                code = json.optString("code"),
                message = json.optString("message").takeIf { it.isNotEmpty() },
                sid = json.optString("sid").takeIf { it.isNotEmpty() },
            )
            else -> Unknown(type)
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
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

    fun create(preset: String, cols: Int, rows: Int, cwd: String? = null): String {
        val json = JSONObject()
            .put("t", "create")
            .put("preset", preset)
            .put("cols", cols)
            .put("rows", rows)
        if (!cwd.isNullOrBlank()) json.put("cwd", cwd)
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
}
