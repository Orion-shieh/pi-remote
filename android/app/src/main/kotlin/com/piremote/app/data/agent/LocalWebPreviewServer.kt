package com.piremote.app.data.agent

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-lightweight local HTTP server for serving in-app web development projects.
 * Runs on 127.0.0.1:8765 without external dependencies.
 */
class LocalWebPreviewServer private constructor() {

    companion object {
        private const val TAG = "WebPreviewServer"
        const val DEFAULT_PORT = 8765

        @Volatile
        private var INSTANCE: LocalWebPreviewServer? = null

        fun getInstance(): LocalWebPreviewServer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalWebPreviewServer().also { INSTANCE = it }
            }
        }
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var rootDir: File? = null
    private var activePort = DEFAULT_PORT

    val running: Boolean get() = isRunning.get()
    val currentUrl: String get() = "http://127.0.0.1:$activePort/"

    /**
     * Starts serving the specified project directory.
     */
    @Synchronized
    fun start(directory: File, port: Int = DEFAULT_PORT): String {
        rootDir = directory
        activePort = port

        if (isRunning.get()) {
            // Already running; rootDir updated
            return currentUrl
        }

        try {
            serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            isRunning.set(true)
            Log.i(TAG, "LocalWebPreviewServer listening on http://127.0.0.1:$port, root=${directory.absolutePath}")

            serverJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive && isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Socket accept error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local server on port $port: ${e.message}")
        }

        return currentUrl
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "LocalWebPreviewServer stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendResponse(socket, 405, "text/plain", "Method Not Allowed".toByteArray())
                return
            }

            var requestPath = URLDecoder.decode(parts[1].substringBefore("?"), "UTF-8")
            if (requestPath == "/" || requestPath.isEmpty()) {
                requestPath = "/index.html"
            }

            val currentRoot = rootDir ?: File(".")
            val targetFile = File(currentRoot, requestPath.trimStart('/')).canonicalFile

            // Prevent path traversal
            if (!targetFile.absolutePath.startsWith(currentRoot.canonicalPath)) {
                sendResponse(socket, 403, "text/plain", "Forbidden".toByteArray())
                return
            }

            if (!targetFile.exists() || targetFile.isDirectory) {
                val indexFile = File(targetFile, "index.html")
                if (indexFile.exists() && !indexFile.isDirectory) {
                    serveFile(socket, indexFile)
                } else {
                    sendResponse(socket, 404, "text/plain", "File Not Found: $requestPath".toByteArray())
                }
                return
            }

            serveFile(socket, targetFile)
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveFile(socket: Socket, file: File) {
        val mimeType = getMimeType(file.name)
        val bytes = file.readBytes()
        sendResponse(socket, 200, mimeType, bytes)
    }

    private fun sendResponse(socket: Socket, statusCode: Int, contentType: String, body: ByteArray) {
        val out = BufferedOutputStream(socket.getOutputStream())
        val statusText = when (statusCode) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"

        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "txt" -> "text/plain"
            "woff", "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }
}
