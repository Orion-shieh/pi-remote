package com.piremote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A session the phone has seen, kept across app restarts.
 *
 * [alive] is refreshed from the agent's session list, so a session that stopped
 * being reported (or reported `running = false`) shows up under "ended" without
 * losing its name and working directory. [kind] distinguishes structured RPC
 * sessions ("rpc", rendered by the GUI from pi events) from raw PTY sessions
 * ("pty").
 */
data class StoredSession(
    val sid: String,
    val name: String,
    val preset: String,
    val kind: String,
    val cwd: String,
    val lastSeenAt: Long,
    val alive: Boolean,
)

/**
 * Persists the session list locally.
 *
 * The PC agent only keeps exited sessions for a short while, and forgets
 * everything when it restarts, so the phone is the only place that can show a
 * useful "previously used" list.
 */
class SessionHistory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pi-remote-history", Context.MODE_PRIVATE)

    fun load(): List<StoredSession> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val sid = obj.optString("sid").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                StoredSession(
                    sid = sid,
                    name = obj.optString("name", "session"),
                    preset = obj.optString("preset"),
                    kind = obj.optString("kind", "pty"),
                    cwd = obj.optString("cwd"),
                    lastSeenAt = obj.optLong("lastSeenAt", 0L),
                    // Anything that was alive when the app died is not alive now.
                    alive = false,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Sessions the user explicitly removed from the list.
     *
     * The PC agent keeps reporting an exited session for a while after it ends,
     * so without this list a removed entry comes straight back on the next
     * refresh.
     */
    fun loadRemoved(): List<String> {
        val raw = prefs.getString(KEY_REMOVED, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotEmpty() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRemoved(sids: List<String>) {
        prefs.edit().putString(KEY_REMOVED, JSONArray(sids).toString()).apply()
    }

    fun save(sessions: List<StoredSession>) {
        val array = JSONArray()
        for (session in sessions) {
            array.put(
                JSONObject()
                    .put("sid", session.sid)
                    .put("name", session.name)
                    .put("preset", session.preset)
                    .put("kind", session.kind)
                    .put("cwd", session.cwd)
                    .put("lastSeenAt", session.lastSeenAt),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "sessions"
        const val KEY_REMOVED = "removedSids"
    }
}
