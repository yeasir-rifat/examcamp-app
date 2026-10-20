package com.examcamp.app.data

import com.examcamp.app.Config
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Supabase Realtime (Phoenix protocol v1) listener for the same channel the website uses
 * ("live-exam-changes"). Any change to the listed tables calls [onChange], so the engine reloads.
 * Reconnects with backoff while started, and sends a heartbeat every 25 s to keep the socket open.
 */
class LiveRealtime(
    private val scope: CoroutineScope,
    private val onChange: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val tables = listOf("subjects", "members", "live_exams", "live_exam_submissions", "enrollment_requests")

    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var retry: Job? = null
    private var wanted = false
    private var attempt = 0
    private var ref = 0

    fun start() {
        if (wanted) return
        wanted = true
        connect()
    }

    fun stop() {
        wanted = false
        retry?.cancel()
        heartbeat?.cancel()
        socket?.close(1000, "leaving live exam")
        socket = null
    }

    private fun connect() {
        val url = Config.SUPABASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("realtime/v1/websocket")
            .addQueryParameter("apikey", Config.SUPABASE_ANON_KEY)
            .addQueryParameter("vsn", "1.0.0")
            .build()
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun scheduleRetry() {
        if (!wanted) return
        retry?.cancel()
        val waitMs = minOf(30_000L, 1_000L shl minOf(attempt, 5))
        attempt++
        retry = scope.launch {
            delay(waitMs)
            if (wanted) connect()
        }
    }

    private fun send(ws: WebSocket, topic: String, event: String, payload: JsonObject) {
        ref++
        val msg = buildJsonObject {
            put("topic", topic)
            put("event", event)
            put("payload", payload)
            put("ref", ref.toString())
        }
        ws.send(msg.toString())
    }

    private fun join(ws: WebSocket) {
        val changes = buildJsonArray {
            tables.forEach { table ->
                add(buildJsonObject {
                    put("event", "*")
                    put("schema", "public")
                    put("table", table)
                })
            }
        }
        val payload = buildJsonObject {
            put("config", buildJsonObject {
                put("broadcast", buildJsonObject {
                    put("ack", false)
                    put("self", false)
                })
                put("presence", buildJsonObject { put("key", "") })
                put("postgres_changes", changes)
            })
            put("access_token", Config.SUPABASE_ANON_KEY)
        }
        send(ws, "realtime:live-exam-changes", "phx_join", payload)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            join(webSocket)
            heartbeat?.cancel()
            heartbeat = scope.launch {
                while (isActive && wanted) {
                    delay(25_000L)
                    socket?.let { send(it, "phoenix", "heartbeat", JsonObject(emptyMap())) }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?.get("event")?.let { (it as? JsonPrimitive)?.contentOrNull }
            if (event == "postgres_changes") scope.launch { onChange() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            heartbeat?.cancel()
            scheduleRetry()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeat?.cancel()
            scheduleRetry()
        }
    }
}
