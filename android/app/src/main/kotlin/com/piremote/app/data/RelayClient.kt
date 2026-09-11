package com.piremote.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * WebSocket client for the relay, with certificate pinning, HMAC handshake and
 * automatic reconnection.
 *
 * Callbacks are delivered on the main thread so callers do not have to marshal
 * every state change themselves.
 */
class RelayClient(
    private val settings: SettingsStore,
    private val listener: Listener,
) {

    interface Listener {
        fun onConnected(agentId: String?, agents: List<String>)
        fun onDisconnected(reason: String)
        fun onControl(message: ControlMessage)
        fun onStdout(sessionId: UUID, seq: Int, payload: ByteArray)
        fun onReplayDone(sessionId: UUID, seq: Int)
    }

    private val main = Handler(Looper.getMainLooper())

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var endpointIndex = 0
    private var backoffMs = INITIAL_BACKOFF_MS
    private var handshakeComplete = false
    private var running = false
    private var reconnectScheduled = false

    val isConnected: Boolean get() = handshakeComplete

    val isRunning: Boolean get() = running

    /**
     * Returns true only when this call actually began a connection attempt.
     * Callers use that to avoid clobbering the state of a live connection;
     * both the foreground service and the UI ask to connect.
     */
    fun start(): Boolean {
        if (running) return false
        running = true
        endpointIndex = 0
        backoffMs = INITIAL_BACKOFF_MS
        connect()
        return true
    }

    fun stop() {
        running = false
        reconnectScheduled = false
        handshakeComplete = false
        webSocket?.close(1000, "client shutdown")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }

    fun send(json: String): Boolean {
        if (!handshakeComplete) return false
        return webSocket?.send(json) ?: false
    }

    fun sendBinary(frame: ByteArray): Boolean {
        if (!handshakeComplete) return false
        return webSocket?.send(frame.toByteString()) ?: false
    }

    // ------------------------------------------------------------- connecting

    private fun connect() {
        if (!running) return
        val endpoints = settings.endpoints
        if (endpoints.isEmpty()) {
            deliverDisconnected("no relay endpoint configured")
            return
        }

        val url = endpoints[endpointIndex % endpoints.size]
        Log.i(TAG, "connecting $url (pinned=${settings.pinnedSha256 != null})")
        val httpClient = buildClient()
        client = httpClient

        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, socketListener)
    }

    private fun buildClient(): OkHttpClient {
        val trustManager = PinnedTrustManager(settings.pinnedSha256)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            // Identity comes from the pinned hash, not from a hostname the
            // self-signed certificate could not possibly carry.
            .hostnameVerifier { _, _ -> true }
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "socket open, sending hello as ${settings.deviceId}")
            val ts = System.currentTimeMillis()
            val nonce = randomNonce()
            val role = "client"
            val id = settings.deviceId
            val signature = hmac(settings.deviceToken, role, id, ts, nonce)

            val hello = org.json.JSONObject()
                .put("t", "hello")
                .put("role", role)
                .put("id", id)
                .put("ts", ts)
                .put("nonce", nonce)
                .put("sig", signature)
            webSocket.send(hello.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = ControlMessage.parse(text)
            if (!handshakeComplete) {
                if (message is ControlMessage.HelloOk) {
                    handshakeComplete = true
                    backoffMs = INITIAL_BACKOFF_MS
                    Log.i(TAG, "authenticated, agentId=${message.agentId} agents=${message.agents}")
                    main.post { listener.onConnected(message.agentId, message.agents) }
                }
                return
            }
            main.post { listener.onControl(message ?: return@post) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!handshakeComplete) return
            val data = bytes.toByteArray()
            val header = Protocol.readHeader(data) ?: return
            when (header.type) {
                Protocol.FRAME_STDOUT -> {
                    val payload = Protocol.payloadOf(data)
                    main.post { listener.onStdout(header.sessionId, header.seq, payload) }
                }

                Protocol.FRAME_REPLAY_DONE ->
                    main.post { listener.onReplayDone(header.sessionId, header.seq) }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "connection failure: ${t.javaClass.simpleName}: ${t.message}", t)
            scheduleReconnect(t.message ?: "connection failed")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "closing ($code $reason)")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "closed ($code $reason)")
            scheduleReconnect(if (reason.isNotEmpty()) reason else "closed ($code)")
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (!running || reconnectScheduled) return
        reconnectScheduled = true
        handshakeComplete = false
        deliverDisconnected(reason)

        // Alternate endpoints on every retry so a blocked port is not retried
        // forever while the fallback sits idle.
        endpointIndex += 1
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        Log.i(TAG, "retrying in ${delay}ms")

        main.postDelayed({
            reconnectScheduled = false
            client?.dispatcher?.executorService?.shutdown()
            client = null
            connect()
        }, delay)
    }

    private fun deliverDisconnected(reason: String) {
        main.post { listener.onDisconnected(reason) }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hmac(token: String, role: String, id: String, ts: Long, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$role|$id|$ts|$nonce".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "PiRemoteRelay"
        const val INITIAL_BACKOFF_MS = 1000L
        const val MAX_BACKOFF_MS = 30000L
        const val PING_INTERVAL_SECONDS = 30L
    }
}

/**
 * Accepts the relay certificate only when its SHA-256 fingerprint matches the
 * value pinned at setup time. The chain is deliberately not validated: the
 * relay uses a self-signed certificate because there is no domain to issue one
 * for, so the pin *is* the trust anchor.
 */
internal class PinnedTrustManager(private val expectedSha256: String?) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()
            ?: throw CertificateException("relay presented no certificate")

        val expected = expectedSha256
        if (expected.isNullOrBlank()) {
            throw CertificateException("no certificate fingerprint pinned; refusing to connect")
        }

        val actual = MessageDigest.getInstance("SHA-256")
            .digest(leaf.encoded)
            .joinToString(":") { "%02X".format(it) }

        if (!actual.equals(expected, ignoreCase = true)) {
            throw CertificateException("certificate pin mismatch (expected $expected, got $actual)")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
