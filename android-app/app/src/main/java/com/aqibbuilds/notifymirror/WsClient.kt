package com.aqibbuilds.notifymirror

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

object WsClient {
    private const val TAG = "NotifyMirrorWs"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var autoReconnect = false
    private var currentHost: String = ""
    private var currentPort: Int = 8765
    private var currentDeviceId: String = ""

    fun connect(host: String, port: Int, deviceId: String) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) {
            Log.w(TAG, "connect: empty host, not connecting")
            _status.value = ConnectionStatus.DISCONNECTED
            return
        }
        // If already connected to the same target, do nothing.
        if (_status.value == ConnectionStatus.CONNECTED &&
            currentHost == cleanHost && currentPort == port
        ) return

        disconnectSocketOnly()
        currentHost = cleanHost
        currentPort = port
        currentDeviceId = deviceId
        autoReconnect = true
        _status.value = ConnectionStatus.CONNECTING

        val url = "ws://$cleanHost:$port"
        Log.i(TAG, "Connecting to $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket open")
                _status.value = ConnectionStatus.CONNECTED
                reconnectJob?.cancel()
                // Hello — exact protocol the Windows app expects.
                val hello = JSONObject().apply {
                    put("type", "hello")
                    put("device", "OnePlus")
                    put("deviceId", currentDeviceId)
                }
                webSocket.send(hello.toString())
                startPing()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: $code $reason")
                onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Failure: ${t.message}")
                onDisconnected()
            }
        })
    }

    private fun onDisconnected() {
        stopPing()
        if (webSocket != null) {
            webSocket = null
        }
        if (_status.value != ConnectionStatus.DISCONNECTED) {
            _status.value = ConnectionStatus.DISCONNECTED
        }
        if (autoReconnect) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val delays = longArrayOf(2000, 5000, 10000, 30000)
            var i = 0
            while (autoReconnect && _status.value != ConnectionStatus.CONNECTED) {
                val wait = delays[minOf(i, delays.size - 1)]
                Log.i(TAG, "Reconnect in ${wait}ms")
                delay(wait)
                if (!autoReconnect) break
                i++
                // Re-attempt; connect() resets state.
                val host = currentHost
                val port = currentPort
                val deviceId = currentDeviceId
                if (host.isNotEmpty()) {
                    // Inline reconnect attempt without clearing autoReconnect.
                    disconnectSocketOnly()
                    _status.value = ConnectionStatus.CONNECTING
                    // Reuse the same listener logic by delegating to connect()
                    // but connect() would cancel the reconnect job; so do it
                    // via a fresh call guarded by a flag.
                    // Simplest safe path: call connect() — it sets a new
                    // socket; the loop exits when CONNECTED.
                    // To avoid cancelling this job from inside connect(),
                    // launch the attempt and return.
                    scope.launch { connect(host, port, deviceId) }
                    // Wait for the attempt to settle before next backoff step.
                    delay(3000)
                    if (_status.value == ConnectionStatus.CONNECTED) break
                } else break
            }
        }
    }

    private fun startPing() {
        stopPing()
        pingJob = scope.launch {
            while (_status.value == ConnectionStatus.CONNECTED) {
                delay(25_000)
                if (_status.value == ConnectionStatus.CONNECTED) {
                    sendRaw(JSONObject().apply { put("type", "ping") }.toString())
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun disconnectSocketOnly() {
        try { webSocket?.close(1000, "reconnect") } catch (_: Exception) { }
        webSocket = null
        stopPing()
    }

    /** Full disconnect: stops auto-reconnect. Called when the service stops. */
    fun disconnect() {
        autoReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectSocketOnly()
        _status.value = ConnectionStatus.DISCONNECTED
    }

    private fun sendRaw(text: String): Boolean {
        val ws = webSocket ?: return false
        if (_status.value != ConnectionStatus.CONNECTED) return false
        return try { ws.send(text) } catch (e: Exception) {
            Log.w(TAG, "send failed: ${e.message}")
            false
        }
    }

    fun sendNotification(
        id: String,
        packageName: String,
        appName: String,
        title: String,
        text: String,
        timeMillis: Long,
        ticker: String?
    ) {
        val obj = JSONObject().apply {
            put("type", "notification")
            put("id", id)
            put("packageName", packageName)
            put("appName", appName)
            put("title", title)
            put("text", text)
            put("time", timeMillis)
            put("ticker", ticker ?: "")
        }
        sendRaw(obj.toString())
        LogStore.add(LogEntry(appName, title, text, timeMillis, packageName))
    }

    fun sendRemoved(id: String) {
        val obj = JSONObject().apply {
            put("type", "removed")
            put("id", id)
        }
        sendRaw(obj.toString())
    }

    /** Sends a fake WhatsApp notification through the same pipeline. */
    fun sendTest() {
        val now = System.currentTimeMillis()
        sendNotification(
            id = "com.whatsapp.test:$now",
            packageName = "com.whatsapp",
            appName = "WhatsApp (test)",
            title = "Test contact",
            text = "Hello from NotifyMirror — this is a test notification.",
            timeMillis = now,
            ticker = "Test notification"
        )
    }
}
