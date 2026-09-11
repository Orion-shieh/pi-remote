package com.piremote.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val agentId: String?) : ConnectionState
    data class Disconnected(val reason: String) : ConnectionState
}

/** What the terminal screen has to react to. */
sealed interface TerminalEvent {
    /**
     * Raw bytes from the shell. Kept undecoded because the emulator's own byte
     * queue reassembles multi-byte characters that the network split across
     * chunks; decoding here would corrupt them.
     */
    class Output(val data: ByteArray) : TerminalEvent {
        override fun equals(other: Any?): Boolean =
            other is Output && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }

    /** The session can no longer be read, so the screen must be cleared. */
    data object Reset : TerminalEvent

    data object ReplayDone : TerminalEvent
}

/**
 * Single owner of the relay connection and the session table.
 *
 * Living outside the UI means switching screens, or the activity being
 * recreated, never drops the connection — the process is kept alive by
 * [com.piremote.app.service.TerminalService] instead.
 */
class SessionRepository(context: Context) {

    private companion object {
        const val TAG = "PiRemoteRepo"
        const val ENDED_HISTORY_LIMIT = 40
        const val REMOVED_LIMIT = 200
    }

    private val settings = SettingsStore(context)
    private val history = SessionHistory(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Newest first. Sessions the user removed stay hidden even though the PC
     * agent keeps reporting an exited session for a while after it ends.
     */
    private val removedSids = ArrayDeque(history.loadRemoved())

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    /**
     * Every session this phone has seen: live ones from the agent merged with
     * ended ones remembered locally. The UI splits them on [StoredSession.alive].
     */
    private val _knownSessions = MutableStateFlow(history.load())
    val knownSessions: StateFlow<List<StoredSession>> = _knownSessions.asStateFlow()

    private val _presets = MutableStateFlow<List<ControlMessage.Preset>>(emptyList())
    val presets: StateFlow<List<ControlMessage.Preset>> = _presets.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // A buffered channel rather than a SharedFlow: replay frames arrive the
    // instant the session is attached, which can be before Compose has started
    // collecting. A SharedFlow with no subscriber silently drops those frames
    // and the terminal stays blank until the program happens to print again.
    private val eventChannel = Channel<TerminalEvent>(Channel.UNLIMITED)
    val events: Flow<TerminalEvent> = eventChannel.receiveAsFlow()

    val settingsStore: SettingsStore get() = settings

    /** Highest sequence number seen per session, used to resume without gaps. */
    private val lastSeq = mutableMapOf<String, Int>()

    val pendingResize = MutableStateFlow<Pair<Int, Int>?>(null)

    private val client: RelayClient = RelayClient(settings, object : RelayClient.Listener {

        override fun onConnected(agentId: String?, agents: List<String>) {
            _connection.value = ConnectionState.Connected(agentId)
            if (agentId == null) {
                markAllOffline()
                return
            }
            // The agent pushes settings/sessions on client_online, but asking
            // explicitly keeps the list fresh even if that message was missed.
            client.send(ControlRequests.list())
        }

        override fun onDisconnected(reason: String) {
            _connection.value = ConnectionState.Disconnected(reason)
            // The session list cannot be trusted while offline, but the history
            // stays so nothing disappears from the UI.
        }

        override fun onControl(message: ControlMessage) {
            when (message) {
                is ControlMessage.Settings -> _presets.value = message.presets
                is ControlMessage.Sessions -> mergeLiveSessions(message.list)

                is ControlMessage.Created -> {
                    upsert(
                        StoredSession(
                            sid = message.sid,
                            name = message.name,
                            preset = message.preset,
                            cwd = message.cwd,
                            lastSeenAt = System.currentTimeMillis(),
                            alive = true,
                        ),
                    )
                    // Open it straight away: creating a session and then having
                    // to find and tap it again is a pointless extra step.
                    openSession(message.sid)
                }

                is ControlMessage.Exit -> markSessionEnded(message.sid)

                is ControlMessage.AgentOnline -> client.send(ControlRequests.list())

                is ControlMessage.ReplayGap -> eventChannel.trySend(TerminalEvent.Reset)

                is ControlMessage.Error -> handleError(message)

                else -> Unit
            }
        }

        override fun onStdout(sessionId: UUID, seq: Int, payload: ByteArray) {
            val sid = sessionId.toString()
            if (sid != _activeSessionId.value) return

            lastSeq[sid] = maxOf(lastSeq[sid] ?: 0, seq)
            if (payload.isNotEmpty()) {
                eventChannel.trySend(TerminalEvent.Output(payload))
            }
        }

        override fun onReplayDone(sessionId: UUID, seq: Int) {
            val sid = sessionId.toString()
            lastSeq[sid] = maxOf(lastSeq[sid] ?: 0, seq)
            if (sid == _activeSessionId.value) {
                eventChannel.trySend(TerminalEvent.ReplayDone)
            }
        }
    })

    // --------------------------------------------------------- session table

    /**
     * Reconciles the agent's list with what we remember.
     *
     * Sessions the agent stopped reporting are kept but flagged as ended, so
     * they move to the "ended" section instead of vanishing.
     */
    private fun mergeLiveSessions(live: List<ControlMessage.SessionInfo>) {
        val now = System.currentTimeMillis()
        val visible = live.filterNot { it.sid in removedSids }
        val liveIds = visible.map { it.sid }.toSet()

        val merged = mutableListOf<StoredSession>()
        for (info in visible) {
            merged += StoredSession(
                sid = info.sid,
                name = info.name.ifEmpty { info.preset.ifEmpty { "session" } },
                preset = info.preset,
                cwd = info.cwd,
                lastSeenAt = now,
                alive = info.running,
            )
        }
        for (previous in _knownSessions.value) {
            if (previous.sid !in liveIds && previous.sid !in removedSids) {
                merged += previous.copy(alive = false)
            }
        }

        publishSessions(merged)
    }

    private fun markAllOffline() {
        publishSessions(_knownSessions.value.map { it.copy(alive = false) })
    }

    private fun markSessionEnded(sid: String) {
        if (sid in removedSids) return
        val existing = _knownSessions.value.firstOrNull { it.sid == sid }
        if (existing == null) {
            Log.w(TAG, "exit for an unknown session $sid")
            return
        }
        publishSessions(
            _knownSessions.value.map {
                if (it.sid == sid) it.copy(alive = false, lastSeenAt = System.currentTimeMillis())
                else it
            },
        )
    }

    private fun upsert(session: StoredSession) {
        // A session that comes back to life must be shown even if it was
        // removed before, otherwise a freshly created session could be invisible.
        if (session.alive && removedSids.remove(session.sid)) {
            history.saveRemoved(removedSids.toList())
        }
        val without = _knownSessions.value.filterNot { it.sid == session.sid }
        publishSessions(without + session)
    }

    private fun publishSessions(list: List<StoredSession>) {
        val running = list.filter { it.alive }.sortedByDescending { it.lastSeenAt }
        val ended = list.filterNot { it.alive }
            .sortedByDescending { it.lastSeenAt }
            .take(ENDED_HISTORY_LIMIT)
        val merged = running + ended
        _knownSessions.value = merged
        history.save(merged)
    }

    private fun handleError(message: ControlMessage.Error) {
        val text = when (message.code) {
            "no_agent" -> "电脑端 agent 不在线"
            "no_session" -> "这个会话在电脑上已经不存在了"
            "too_many_sessions" -> {
                // The agent reports "running/limit", which is far more useful
                // than a bare "limit reached".
                val detail = message.message?.let { "（当前 $it）" }.orEmpty()
                "电脑端运行中的会话太多$detail，先终止一些再建"
            }
            "spawn_failed" -> "电脑端启动会话失败：${message.message ?: "未知原因"}"
            else -> message.message ?: message.code
        }
        _lastError.value = text

        // An ended session that the agent has already forgotten cannot be
        // shown, so drop out of the terminal instead of leaving it blank.
        if (message.code == "no_session" && _activeSessionId.value == message.sid) {
            _activeSessionId.value = null
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    // ------------------------------------------------------------- lifecycle

    fun connect() {
        if (!settings.isConfigured) {
            _connection.value = ConnectionState.Disconnected("relay not configured")
            return
        }
        // Both the foreground service and the UI ask to connect. Starting an
        // already-running client must be a no-op, otherwise the second call
        // resets a healthy connection's state back to "connecting" and nothing
        // ever moves it forward again.
        if (!client.start()) return
        _connection.value = ConnectionState.Connecting
    }

    fun disconnect() {
        client.stop()
        _connection.value = ConnectionState.Idle
        markAllOffline()
    }

    fun shutdown() {
        client.stop()
    }

    // -------------------------------------------------------------- commands

    fun refresh() {
        client.send(ControlRequests.list())
    }

    fun createSession(presetId: String, cols: Int, rows: Int, cwd: String? = null) {
        client.send(ControlRequests.create(presetId, cols, rows, cwd))
    }

    fun openSession(sid: String) {
        clearError()
        _activeSessionId.value = sid
        _title.value = ""
        client.send(ControlRequests.attach(sid, lastSeq[sid] ?: 0))
    }

    /** Leaves the session running on the PC but stops streaming it here. */
    fun detachActive() {
        val sid = _activeSessionId.value ?: return
        client.send(ControlRequests.detach(sid))
        _activeSessionId.value = null
    }

    /** Terminates a live session on the PC. It then moves to the ended list. */
    fun killSession(sid: String) {
        client.send(ControlRequests.kill(sid))
        if (_activeSessionId.value == sid) {
            _activeSessionId.value = null
        }
        lastSeq.remove(sid)
    }

    /** Forgets an ended session locally, and keeps it forgotten across refreshes. */
    fun forgetSession(sid: String) {
        removedSids.remove(sid)
        removedSids.addFirst(sid)
        while (removedSids.size > REMOVED_LIMIT) removedSids.removeLast()
        history.saveRemoved(removedSids.toList())

        publishSessions(_knownSessions.value.filterNot { it.sid == sid })
        lastSeq.remove(sid)
    }

    fun sendInput(text: String) {
        val sid = _activeSessionId.value ?: return
        Log.d("PiRemoteInput", "stdin ${text.length} char(s) -> $sid")
        sendStdin(sid, text.toByteArray(Charsets.UTF_8))
    }

    fun sendRaw(bytes: ByteArray) {
        val sid = _activeSessionId.value ?: return
        sendStdin(sid, bytes)
    }

    private fun sendStdin(sid: String, payload: ByteArray) {
        val uuid = runCatching { UUID.fromString(sid) }.getOrNull() ?: return
        client.sendBinary(Protocol.buildFrame(Protocol.FRAME_STDIN, 0, uuid, payload))
    }

    fun resize(cols: Int, rows: Int) {
        val sid = _activeSessionId.value ?: return
        Log.d("PiRemoteInput", "resize ${cols}x$rows -> $sid")
        client.send(ControlRequests.resize(sid, cols, rows))
    }

    fun onTitleChanged(value: String) {
        _title.value = value
    }

    fun lastSequence(sid: String): Int = lastSeq[sid] ?: 0
}
