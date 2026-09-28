package com.piremote.app.data

import android.content.Context
import android.util.Log
import com.piremote.terminal.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import org.json.JSONObject

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

/** One historical pi conversation, listed by the agent from the session files. */
data class PiSessionBrief(
    val file: String,
    val id: String,
    val cwd: String?,
    val name: String?,
    val preview: String?,
    val timestamp: String?,
    val messageCount: Int,
    val parentSession: String? = null,
)

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
    val lineageStore = SessionLineageStore(context)
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

    private val _missingSessionPrompt = MutableStateFlow<StoredSession?>(null)
    val missingSessionPrompt: StateFlow<StoredSession?> = _missingSessionPrompt.asStateFlow()

    private val liveSessionSids = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile
    private var hasReceivedLiveSessions = false
    private var lastAttemptedSid: String? = null

    fun promptMissingSession(session: StoredSession) {
        _missingSessionPrompt.value = session
    }

    fun dismissMissingSessionPrompt() {
        _missingSessionPrompt.value = null
    }

    /** Checks if an ended session is known to no longer exist on the connected PC. */
    fun isSessionMissingOnPc(sid: String): Boolean {
        val conn = _connection.value
        val isConnected = conn is ConnectionState.Connected && conn.agentId != null
        return isConnected && hasReceivedLiveSessions && !liveSessionSids.contains(sid)
    }

    // A buffered channel rather than a SharedFlow: replay frames arrive the
    // instant the session is attached, which can be before Compose has started
    // collecting. A SharedFlow with no subscriber silently drops those frames
    // and the terminal stays blank until the program happens to print again.
    private val eventChannel = Channel<TerminalEvent>(Channel.UNLIMITED)
    val events: Flow<TerminalEvent> = eventChannel.receiveAsFlow()

    // Structured pi events from RPC sessions. Same channel rationale as
    // [eventChannel]: the attach snapshot can arrive before Compose subscribes
    // and must not be dropped.
    private val guiEventChannel = Channel<ControlMessage.AgentEvent>(Channel.UNLIMITED)
    val guiEvents: Flow<ControlMessage.AgentEvent> = guiEventChannel.receiveAsFlow()

    /** Historical pi conversations for the session picker (pi_sessions events). */
    private val _piSessions = MutableStateFlow<List<PiSessionBrief>>(emptyList())
    val piSessions: StateFlow<List<PiSessionBrief>> = _piSessions.asStateFlow()

    /** Latest directory listing for the working-directory picker (home screen). */
    private val _dirListing = MutableStateFlow<ControlMessage.DirListing?>(null)
    val dirListing: StateFlow<ControlMessage.DirListing?> = _dirListing.asStateFlow()

    /** Latest file content preview loaded from the PC. */
    private val _fileContent = MutableStateFlow<ControlMessage.FileContent?>(null)
    val fileContent: StateFlow<ControlMessage.FileContent?> = _fileContent.asStateFlow()

    /** Latest auth.json update status on the PC agent. */
    private val _authUpdateResult = MutableStateFlow<ControlMessage.AuthUpdated?>(null)
    val authUpdateResult: StateFlow<ControlMessage.AuthUpdated?> = _authUpdateResult.asStateFlow()

    /** Latest remote model detection status on the PC agent. */
    private val _modelsDetectedResult = MutableStateFlow<ControlMessage.ModelsDetected?>(null)
    val modelsDetectedResult: StateFlow<ControlMessage.ModelsDetected?> = _modelsDetectedResult.asStateFlow()

    /** Latest model deletion result from the PC agent. */
    private val _modelDeletedResult = MutableStateFlow<ControlMessage.ModelDeleted?>(null)
    val modelDeletedResult: StateFlow<ControlMessage.ModelDeleted?> = _modelDeletedResult.asStateFlow()

    /** Latest pi session deletion result from the PC agent. */
    private val _piSessionDeletedResult = MutableStateFlow<ControlMessage.PiSessionDeleted?>(null)
    val piSessionDeletedResult: StateFlow<ControlMessage.PiSessionDeleted?> = _piSessionDeletedResult.asStateFlow()

    /** Latest models list read directly from Pi Agent's models.json / auth.json */
    private val _piAgentModels = MutableStateFlow<List<com.piremote.app.ui.gui.PiModelOption>>(emptyList())
    val piAgentModels: StateFlow<List<com.piremote.app.ui.gui.PiModelOption>> = _piAgentModels.asStateFlow()

    /** Locally added custom models that should always be available immediately. */
    private val _userAddedModels = MutableStateFlow<List<com.piremote.app.ui.gui.PiModelOption>>(emptyList())
    val userAddedModels: StateFlow<List<com.piremote.app.ui.gui.PiModelOption>> = _userAddedModels.asStateFlow()

    /** Blacklist of deleted models that must never reappear unless re-added. */
    private val _deletedModelKeys = MutableStateFlow<Set<String>>(emptySet())
    val deletedModelKeys: StateFlow<Set<String>> = _deletedModelKeys.asStateFlow()

    /** Blacklist of deleted session paths/ids that must never reappear. */
    private val _deletedSessionFiles = MutableStateFlow<Set<String>>(emptySet())
    val deletedSessionFiles: StateFlow<Set<String>> = _deletedSessionFiles.asStateFlow()

    /** Display name of the active session's pi conversation, from attach snapshots. */
    private val _piSessionName = MutableStateFlow<String?>(null)
    val piSessionName: StateFlow<String?> = _piSessionName.asStateFlow()

    /** Model provider of the active session's pi model, shown in the top navigation bar. */
    private val _piModelProvider = MutableStateFlow<String?>(null)
    val piModelProvider: StateFlow<String?> = _piModelProvider.asStateFlow()

    /** Quick-navigation outline of the active gui conversation (☰ in the top bar). */
    private val _chatOutline = MutableStateFlow<List<com.piremote.app.ui.gui.GuiChatOutlineItem>>(emptyList())
    val chatOutline: StateFlow<List<com.piremote.app.ui.gui.GuiChatOutlineItem>> = _chatOutline.asStateFlow()

    val settingsStore: SettingsStore get() = settings

    /** Highest sequence number seen per session, used to resume without gaps. */
    private val lastSeq = mutableMapOf<String, Int>()

    /** Cached terminal views per session, preserving scrollback history across navigation. */
    private val terminalViews = mutableMapOf<String, TerminalView>()

    /** Cached GUI agent chat states per session, preserving transcript cards across navigation. */
    private val guiAgentStates = mutableMapOf<String, com.piremote.app.ui.gui.GuiAgentState>()
    private val newlyCreatedSessionIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun getOrCreateGuiAgentState(sid: String): com.piremote.app.ui.gui.GuiAgentState {
        return synchronized(guiAgentStates) {
            guiAgentStates.getOrPut(sid) { com.piremote.app.ui.gui.GuiAgentState() }
        }
    }

    fun isNewlyCreatedSession(sid: String): Boolean = newlyCreatedSessionIds.contains(sid)
    fun markSessionStarted(sid: String) { newlyCreatedSessionIds.remove(sid) }

    /**
     * View mode per session picked when the session was created; true = 图形视图.
     * Sessions without a recorded choice fall back to the preset default in
     * [viewModeFor].
     */
    private val sessionGuiModes = mutableMapOf<String, Boolean>()

    /** View choice made in the quick-launch panel for the session being created. */
    private var pendingCreateGuiMode: Boolean? = null

    fun getOrCreateTerminalView(sid: String, context: Context): TerminalView {
        val cached = terminalViews[sid]
        if (cached != null) return cached

        val created = TerminalView(context)
        terminalViews[sid] = created
        return created
    }

    val pendingResize = MutableStateFlow<Pair<Int, Int>?>(null)


    private val client: RelayClient = RelayClient(settings, object : RelayClient.Listener {

        override fun onConnected(agentId: String?, agents: List<String>) {
            _connection.value = ConnectionState.Connected(agentId)
            if (agentId == null) {
                hasReceivedLiveSessions = false
                liveSessionSids.clear()
                markAllOffline()
                return
            }
            if (_lastError.value == "电脑端 agent 不在线") {
                _lastError.value = null
            }
            // The agent pushes settings/sessions on client_online, but asking
            // explicitly keeps the list fresh even if that message was missed.
            client.send(ControlRequests.list())
            requestPiAgentModels()
            pendingDirListingPath?.let { path ->
                pendingDirListingPath = null
                client.send(ControlRequests.listDirs(path))
            }
        }

        override fun onDisconnected(reason: String) {
            _connection.value = ConnectionState.Disconnected(reason)
            hasReceivedLiveSessions = false
            liveSessionSids.clear()
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
                            kind = message.kind,
                            cwd = message.cwd,
                            lastSeenAt = System.currentTimeMillis(),
                            alive = true,
                        ),
                    )
                    // Open it straight away: creating a session and then having
                    // to find and tap it again is a pointless extra step.
                    val requestedMode = pendingCreateGuiMode
                    pendingCreateGuiMode = null
                    if (requestedMode != null) {
                        sessionGuiModes[message.sid] = requestedMode
                    }
                    newlyCreatedSessionIds.add(message.sid)
                    openSession(message.sid)

                }

                is ControlMessage.Exit -> markSessionEnded(message.sid)

                is ControlMessage.AgentOnline -> {
                    _connection.value = ConnectionState.Connected(message.id ?: "agent")
                    if (_lastError.value == "电脑端 agent 不在线") {
                        _lastError.value = null
                    }
                    client.send(ControlRequests.list())
                    requestPiAgentModels()
                    pendingDirListingPath?.let { path ->
                        pendingDirListingPath = null
                        client.send(ControlRequests.listDirs(path))
                    }
                }

                is ControlMessage.AgentOffline -> {
                    _connection.value = ConnectionState.Connected(agentId = null)
                    hasReceivedLiveSessions = false
                    liveSessionSids.clear()
                    markAllOffline()
                }

                is ControlMessage.DirListing -> {
                    _dirListing.value = message
                    pendingDirListingPath = null
                }

                is ControlMessage.FileContent -> _fileContent.value = message

                is ControlMessage.AuthUpdated -> {
                    _authUpdateResult.value = message
                    if (message.success) {
                        requestPiAgentModels()
                        _activeSessionId.value?.let { sid ->
                            sendAgentCommand(sid, JSONObject().put("type", "get_available_models"))
                        }
                    }
                }

                is ControlMessage.ModelsDetected -> {
                    _modelsDetectedResult.value = message
                }

                is ControlMessage.ModelsList -> {
                    val blacklist = _deletedModelKeys.value
                    _piAgentModels.value = message.models.filter {
                        val k1 = "${it.provider}/${it.id}".lowercase()
                        val k2 = it.id.lowercase()
                        val k3 = it.key.lowercase()
                        !blacklist.contains(k1) && !blacklist.contains(k2) && !blacklist.contains(k3)
                    }
                }

                is ControlMessage.ModelDeleted -> {
                    _modelDeletedResult.value = message
                    val fullKey = "${message.provider}/${message.modelId}".lowercase()
                    _deletedModelKeys.value = _deletedModelKeys.value + fullKey + message.modelId.lowercase()
                    _piAgentModels.value = _piAgentModels.value.filter {
                        !(it.provider.equals(message.provider, ignoreCase = true) &&
                          it.id.equals(message.modelId, ignoreCase = true))
                    }
                    _userAddedModels.value = _userAddedModels.value.filter {
                        !(it.provider.equals(message.provider, ignoreCase = true) &&
                          it.id.equals(message.modelId, ignoreCase = true))
                    }
                }

                is ControlMessage.PiSessionDeleted -> {
                    _piSessionDeletedResult.value = message
                    if (message.file.isNotBlank()) _deletedSessionFiles.value = _deletedSessionFiles.value + message.file
                    if (message.id.isNotBlank()) _deletedSessionFiles.value = _deletedSessionFiles.value + message.id
                    _piSessions.value = _piSessions.value.filter { it.file != message.file && it.id != message.id }
                    forgetSession(message.id)
                }

                is ControlMessage.AgentEvent -> {
                    if (message.sid == _activeSessionId.value) {
                        val event = message.event
                        if (event.optString("type") == "pi_sessions") {
                            // org.json maps JSON null to the literal string
                            // "null" through optString; strip it here because
                            // the agent serializes absent names as null.
                            val deletedFiles = _deletedSessionFiles.value
                            _piSessions.value = buildList {
                                val array = event.optJSONArray("sessions")
                                if (array != null) {
                                    for (i in 0 until array.length()) {
                                        val obj = array.optJSONObject(i) ?: continue
                                        fun field(key: String): String? =
                                            obj.optString(key).takeIf { it.isNotEmpty() && it != "null" }
                                        val brief = PiSessionBrief(
                                            file = obj.optString("file"),
                                            id = obj.optString("id"),
                                            cwd = field("cwd"),
                                            name = field("name"),
                                            preview = field("preview"),
                                            timestamp = field("timestamp"),
                                            messageCount = obj.optInt("messageCount", 0),
                                            parentSession = field("parentSession")
                                                ?: field("parentSessionId")
                                                ?: field("parentSessionPath")
                                                ?: field("parentId")
                                                ?: field("forkedFrom")
                                                ?: field("forkedFromSessionId"),
                                        )
                                        if (!deletedFiles.contains(brief.file) && !deletedFiles.contains(brief.id)) {
                                            add(brief)
                                        }
                                    }
                                }
                            }.sortedByDescending { it.timestamp.orEmpty() }
                        } else {
                            guiEventChannel.trySend(message)
                        }
                    }
                }

                is ControlMessage.ReplayGap -> {
                    _activeSessionId.value?.let { terminalViews.remove(it) }
                    eventChannel.trySend(TerminalEvent.Reset)
                }

                is ControlMessage.Error -> {
                    handleError(message)
                    val sid = message.sid ?: _activeSessionId.value
                    if (sid != null) {
                        val errText = message.message ?: message.code
                        val errorJson = JSONObject()
                            .put("type", "error")
                            .put("error", errText)
                            .put("code", message.code)
                        guiEventChannel.trySend(ControlMessage.AgentEvent(sid, errorJson))
                    }
                }

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

        hasReceivedLiveSessions = true
        liveSessionSids.clear()
        liveSessionSids.addAll(liveIds)

        val merged = mutableListOf<StoredSession>()
        for (info in visible) {
            val existing = _knownSessions.value.firstOrNull { it.sid == info.sid }
            val lastSeen = existing?.lastSeenAt ?: now
            merged += StoredSession(
                sid = info.sid,
                // Prefer the pi conversation name ("测试") over the preset name
                // ("Pi Agent GUI") — that is what the user recognises.
                name = info.piName
                    ?: info.name.ifEmpty { info.preset.ifEmpty { "session" } },
                preset = info.preset,
                kind = info.kind,
                cwd = info.cwd,
                lastSeenAt = lastSeen,
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
        if (message.code == "no_session") {
            val targetSid = message.sid ?: _activeSessionId.value ?: lastAttemptedSid
            if (_activeSessionId.value == targetSid) {
                _activeSessionId.value = null
            }
            if (targetSid != null) {
                liveSessionSids.remove(targetSid)
                val session = _knownSessions.value.firstOrNull { it.sid == targetSid }
                    ?: StoredSession(
                        sid = targetSid,
                        name = "该会话",
                        preset = "",
                        kind = "pty",
                        cwd = "",
                        lastSeenAt = 0L,
                        alive = false,
                    )
                _missingSessionPrompt.value = session
                return
            }
        }

        val text = when (message.code) {
            "no_agent" -> {
                _connection.value = ConnectionState.Connected(agentId = null)
                "电脑端 agent 不在线"
            }
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

    fun reconnect() {
        if (!settings.isConfigured) {
            _connection.value = ConnectionState.Disconnected("relay not configured")
            return
        }
        _lastError.value = null
        _connection.value = ConnectionState.Connecting
        client.reconnect()
    }

    fun refresh() {
        _lastError.value = null
        val currentConn = _connection.value
        if (currentConn is ConnectionState.Connecting) {
            // Already in the process of connecting/handshaking, do not interrupt with a reconnect
            return
        }
        val isHealthy = client.isConnected && currentConn is ConnectionState.Connected && currentConn.agentId != null
        if (!isHealthy) {
            reconnect()
        } else {
            client.send(ControlRequests.list())
            requestPiAgentModels()
        }
    }

    fun createSession(
        presetId: String,
        cols: Int,
        rows: Int,
        cwd: String? = null,
        args: List<String>? = null,
        command: String? = null,
        startInGui: Boolean? = null,
    ) {
        pendingCreateGuiMode = startInGui
        client.send(ControlRequests.create(presetId, cols, rows, cwd, args, command))
    }

    /**
     * Which view a session opens in. An explicit choice from the quick-launch
     * panel wins; otherwise structured RPC sessions are graphical by nature and
     * every other preset (plain shells, PTY pi) opens in the terminal view.
     */
    fun viewModeFor(sid: String): Boolean {
        sessionGuiModes[sid]?.let { return it }
        val record = _knownSessions.value.firstOrNull { it.sid == sid } ?: return false
        if (record.kind == "rpc") return true
        return record.preset.startsWith("pi", ignoreCase = true)
    }

    fun setGuiMode(sid: String, isGui: Boolean) {
        sessionGuiModes[sid] = isGui
    }

    /** "rpc" for structured pi sessions, "pty" for everything else. */
    fun sessionKind(sid: String): String =
        _knownSessions.value.firstOrNull { it.sid == sid }?.kind ?: "pty"

    /** The working directory of a session (its spawn cwd). */
    fun sessionCwd(sid: String): String =
        _knownSessions.value.firstOrNull { it.sid == sid }?.cwd.orEmpty()

    fun touchSession(sid: String) {
        val now = System.currentTimeMillis()
        val list = _knownSessions.value
        val existing = list.firstOrNull { it.sid == sid } ?: return
        if (existing.lastSeenAt == now) return
        val updated = list.map {
            if (it.sid == sid) it.copy(lastSeenAt = now) else it
        }
        publishSessions(updated)
    }

    fun openSession(sid: String) {
        lastAttemptedSid = sid
        clearError()
        drainEvents()
        _activeSessionId.value = sid
        _title.value = ""
        _piSessionName.value = null
        _piModelProvider.value = guiAgentStates[sid]?.currentModelProvider
        _piSessions.value = emptyList()
        _chatOutline.value = emptyList()

        // If we don't have a cached view for this session (e.g. freshly opened or after clear),
        // we must attach with seq 0 so that the PC replays the entire history buffer.
        // If we already have a cached view with an emulator, we attach with lastSeq[sid] to seamlessly append.
        val hasCachedView = terminalViews[sid]?.emulator != null
        val attachSeq = if (hasCachedView) (lastSeq[sid] ?: 0) else 0
        if (!hasCachedView) {
            lastSeq[sid] = 0
        }
        client.send(ControlRequests.attach(sid, attachSeq))
    }

    private fun drainEvents() {
        while (true) {
            val res = eventChannel.tryReceive()
            if (res.isFailure) break
        }
    }

    /** Leaves the session running on the PC but stops streaming it here. */
    fun detachActive() {
        val sid = _activeSessionId.value ?: return
        client.send(ControlRequests.detach(sid))
        _activeSessionId.value = null
    }

    /** Terminates a live session on the PC. It then moves to the ended list. */
    fun killSession(sid: String) {
        liveSessionSids.remove(sid)
        client.send(ControlRequests.kill(sid))
        if (_activeSessionId.value == sid) {
            _activeSessionId.value = null
        }
        lastSeq.remove(sid)
        terminalViews.remove(sid)
        guiAgentStates.remove(sid)
        newlyCreatedSessionIds.remove(sid)
        sessionGuiModes.remove(sid)
        val updated = _knownSessions.value.map {
            if (it.sid == sid) it.copy(alive = false) else it
        }
        publishSessions(updated)
    }

    /** Forgets an ended session locally, and keeps it forgotten across refreshes. */
    fun forgetSession(sid: String) {
        liveSessionSids.remove(sid)
        if (_missingSessionPrompt.value?.sid == sid) {
            _missingSessionPrompt.value = null
        }
        removedSids.remove(sid)
        removedSids.addFirst(sid)
        while (removedSids.size > REMOVED_LIMIT) removedSids.removeLast()
        history.saveRemoved(removedSids.toList())

        publishSessions(_knownSessions.value.filterNot { it.sid == sid })
        lastSeq.remove(sid)
        terminalViews.remove(sid)
        guiAgentStates.remove(sid)
        newlyCreatedSessionIds.remove(sid)
        sessionGuiModes.remove(sid)
    }

    fun sendInput(text: String) {
        val sid = _activeSessionId.value ?: return
        Log.d("PiRemoteInput", "stdin ${text.length} char(s) -> $sid")
        sendStdin(sid, text.toByteArray(Charsets.UTF_8))
    }

    fun sendText(text: String) {
        sendInput(text)
    }

    fun sendText(sid: String, text: String) {
        Log.d("PiRemoteInput", "stdin ${text.length} char(s) -> $sid")
        sendStdin(sid, text.toByteArray(Charsets.UTF_8))
    }

    private val messageAgentCommands = setOf("prompt", "steer", "follow_up", "fork", "extension_ui_response")

    /** Sends a structured pi RPC command (prompt / abort / extension_ui_response) for an RPC session. */
    fun sendAgentCommand(sid: String, command: org.json.JSONObject) {
        val type = command.optString("type")
        if (type in messageAgentCommands) {
            touchSession(sid)
        }
        Log.d("PiRemoteInput", "agent command $type -> $sid")
        client.send(ControlRequests.agentCommand(sid, command))
    }

    /** Asks the agent to rescan pi session files for the picker; clears the stale list. */
    fun requestPiSessionList(sid: String, cwd: String? = null) {
        _piSessions.value = emptyList()
        client.send(ControlRequests.listPiSessions(sid, cwd))
    }

    private var pendingDirListingPath: String? = null

    /** Asks the agent to list the subdirectories of a path (drives when blank). */
    fun requestDirListing(path: String) {
        _dirListing.value = null
        if (!client.send(ControlRequests.listDirs(path))) {
            pendingDirListingPath = path
        } else {
            pendingDirListingPath = null
        }
    }

    /** Requests the agent to read and return the text/preview content of a file. */
    fun requestFileContent(path: String) {
        _fileContent.value = null
        client.send(ControlRequests.readFile(path))
    }

    /** Clears the current file content preview. */
    fun clearFileContent() {
        _fileContent.value = null
    }

    /** Asks the PC agent to update Pi Agent's ~/.pi/agent/auth.json credentials. */
    fun updatePiAuth(
        provider: String,
        apiKey: String,
        baseUrl: String? = null,
        models: List<String> = emptyList(),
    ) {
        _authUpdateResult.value = null
        client.send(ControlRequests.updateAuth(provider, apiKey, baseUrl, models))
    }

    /** Clears the auth update result after handling UI feedback. */
    fun clearAuthUpdateResult() {
        _authUpdateResult.value = null
    }

    /** Asks the PC agent to detect available models for a provider/endpoint in the PC environment. */
    fun detectRemoteModels(
        provider: String,
        apiKey: String,
        baseUrl: String? = null,
    ) {
        _modelsDetectedResult.value = null
        client.send(ControlRequests.detectModels(provider, apiKey, baseUrl))
    }

    /** Clears the model detection result. */
    fun clearModelsDetectedResult() {
        _modelsDetectedResult.value = null
    }

    /** Immediately adds a model to the local active pool and removes it from the deleted blacklist. */
    fun addUserModel(option: com.piremote.app.ui.gui.PiModelOption) {
        val k1 = "${option.provider}/${option.id}".lowercase()
        val k2 = option.id.lowercase()
        val k3 = option.key.lowercase()
        _deletedModelKeys.value = _deletedModelKeys.value.filter { it != k1 && it != k2 && it != k3 }.toSet()
        if (!_userAddedModels.value.any { it.provider.equals(option.provider, ignoreCase = true) && it.id.equals(option.id, ignoreCase = true) }) {
            _userAddedModels.value = _userAddedModels.value + option
        }
        if (!_piAgentModels.value.any { it.provider.equals(option.provider, ignoreCase = true) && it.id.equals(option.id, ignoreCase = true) }) {
            _piAgentModels.value = _piAgentModels.value + option
        }
    }

    /** Requests the PC Agent to delete a model from ~/.pi/agent/models.json / auth.json */
    fun deletePiModel(provider: String, modelId: String) {
        _modelDeletedResult.value = null
        val fullKey = "$provider/$modelId".lowercase()
        _deletedModelKeys.value = _deletedModelKeys.value + fullKey + modelId.lowercase()
        _userAddedModels.value = _userAddedModels.value.filter {
            !(it.provider.equals(provider, ignoreCase = true) && it.id.equals(modelId, ignoreCase = true))
        }
        _piAgentModels.value = _piAgentModels.value.filter {
            !(it.provider.equals(provider, ignoreCase = true) && it.id.equals(modelId, ignoreCase = true))
        }
        client.send(ControlRequests.deleteModel(provider, modelId))
    }

    /** Clears the model deletion result. */
    fun clearModelDeletedResult() {
        _modelDeletedResult.value = null
    }

    /** Asks the PC agent to read and return all models from ~/.pi/agent/models.json & auth.json */
    fun requestPiAgentModels() {
        client.send(ControlRequests.getModels())
    }

    /** Permanently deletes a pi session file on the PC and cleans up local references. */
    fun deletePiSession(file: String, id: String) {
        _piSessionDeletedResult.value = null
        val set = mutableSetOf<String>()
        if (file.isNotBlank()) set.add(file)
        if (id.isNotBlank()) set.add(id)
        _deletedSessionFiles.value = _deletedSessionFiles.value + set
        // Optimistically clean up local cache
        _piSessions.value = _piSessions.value.filter { it.file != file && it.id != id }
        forgetSession(id)
        client.send(ControlRequests.deletePiSession(file, id))
    }

    /** Clears the pi session deletion result. */
    fun clearPiSessionDeletedResult() {
        _piSessionDeletedResult.value = null
    }

    /** Deletes a session by sid, coordinating with PC if it corresponds to a Pi session file. */
    fun deleteSessionWithPc(sid: String) {
        val brief = _piSessions.value.firstOrNull { it.id == sid }
        val filePath = brief?.file ?: ""
        deletePiSession(filePath, sid)
    }

    /** Records the pi conversation name reported by attach snapshots (top-bar display). */
    fun setPiSessionName(name: String?) {
        _piSessionName.value = name?.takeIf { it.isNotBlank() }
    }

    /** Records the active session's model provider (top-bar display). */
    fun setPiModelProvider(provider: String?) {
        _piModelProvider.value = provider?.takeIf { it.isNotBlank() }
    }

    /** Publishes the quick-navigation outline for the active gui conversation. */
    fun setChatOutline(items: List<com.piremote.app.ui.gui.GuiChatOutlineItem>) {
        _chatOutline.value = items
    }

    fun sendRaw(bytes: ByteArray) {
        val sid = _activeSessionId.value ?: return
        val session = _knownSessions.value.firstOrNull { it.sid == sid }
        if (session?.kind == "rpc") {
            // RPC sessions interact via agent_command JSON, not raw PTY stdin frames.
            return
        }
        sendStdin(sid, bytes)
    }

    private fun sendStdin(sid: String, payload: ByteArray) {
        val session = _knownSessions.value.firstOrNull { it.sid == sid }
        if (session?.kind == "rpc") {
            Log.d("PiRemoteInput", "ignored stdin for RPC session $sid")
            return
        }
        touchSession(sid)
        val uuid = runCatching { UUID.fromString(sid) }.getOrNull() ?: return
        client.sendBinary(Protocol.buildFrame(Protocol.FRAME_STDIN, 0, uuid, payload))
    }

    fun resize(cols: Int, rows: Int) {
        val sid = _activeSessionId.value ?: return
        val session = _knownSessions.value.firstOrNull { it.sid == sid }
        if (session?.kind == "rpc") return
        Log.d("PiRemoteInput", "resize ${cols}x$rows -> $sid")
        client.send(ControlRequests.resize(sid, cols, rows))
    }

    fun onTitleChanged(value: String) {
        _title.value = value
    }

    fun lastSequence(sid: String): Int = lastSeq[sid] ?: 0
}
