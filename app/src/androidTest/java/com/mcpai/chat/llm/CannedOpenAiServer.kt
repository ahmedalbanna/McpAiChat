package com.mcpai.chat.llm

import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A canned OpenAI-compatible chat-completions server speaking SSE on loopback.
 *
 * On the first exchange of a turn it emits a `tool_calls` delta requesting the
 * `echo` tool; once the request carries a `tool` message it emits a plain
 * final-answer delta. The engine therefore walks the full
 * assistant -> tool -> assistant loop with no real provider involved.
 *
 * The response never depends on the tools offered in the request; the canned
 * behaviour is the point.
 */
class CannedOpenAiServer {

    private var socket: ServerSocket? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    /** How many chat-completions requests have been served. */
    val requestCount = AtomicInteger(0)

    fun start() {
        val server = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        socket = server
        running.set(true)
        thread = Thread {
            while (running.get()) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    if (running.get()) continue else break
                }
                Thread { serve(client) }.start()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
    }

    /** The `base_url` to seed into Settings; the provider appends `/chat/completions`. */
    fun url(): String = "http://127.0.0.1:${socket!!.localPort}"

    private fun serve(client: Socket) {
        client.use { connection ->
            val input = connection.getInputStream()
            val output = connection.getOutputStream()
            while (true) {
                val body = readRequest(input) ?: return
                requestCount.incrementAndGet()
                val sse = respond(body)
                val bytes = sse.toByteArray(Charsets.UTF_8)
                output.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "\r\n"
                        ).toByteArray(Charsets.US_ASCII),
                )
                output.write(bytes)
                output.flush()
            }
        }
    }

    /**
     * Reads one HTTP request and returns its body decoded as UTF-8, or null when
     * the peer is gone. `Content-Length` counts bytes, so everything is read
     * from the raw stream: a character count hangs on multibyte bodies, and a
     * buffered reader would swallow body bytes into its read-ahead.
     */
    private fun readRequest(input: java.io.InputStream): String? {
        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toInt()
            }
        }
        if (contentLength == 0) return ""
        val raw = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(raw, read, contentLength - read)
            if (count < 0) return null
            read += count
        }
        return String(raw, Charsets.UTF_8)
    }

    /** One ASCII request line (headers are ASCII); null at end of stream. */
    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return null
            if (c == 10) break // \n
            if (c == 13) continue // \r
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun respond(body: String): String {
        val messages = runCatching { JSONObject(body).optJSONArray("messages") }.getOrNull()
        val last = messages
            ?.takeIf { arr -> arr.length() > 0 }
            ?.let { arr -> arr.optJSONObject(arr.length() - 1) }
        val lastIsToolResult = last?.optString("role") == "tool"

        return if (lastIsToolResult) {
            // The most recent message is this turn's tool result: answer now.
            val delta = JSONObject().put("content", "Echoed: canned echo.")
            sse(chunk(delta, null), chunk(JSONObject(), "stop"))
        } else {
            // Iteration 1 of the turn: request the echo tool. A tool result in
            // older history does not count: the newest request is what matters.
            val toolCall = JSONObject()
                .put("index", 0)
                .put("id", "call_canned_1")
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", "echo")
                        .put("arguments", """{"text":"canned echo"}"""),
                )
            val delta = JSONObject()
                .put("role", "assistant")
                .put("tool_calls", JSONArray().put(toolCall))
            sse(chunk(delta, null), chunk(JSONObject(), "tool_calls"))
        }
    }

    private fun chunk(delta: JSONObject, finishReason: String?): String = JSONObject()
        .put("id", "canned-1")
        .put("object", "chat.completion.chunk")
        .put("created", System.currentTimeMillis() / 1000)
        .put("model", "canned-model")
        .put(
            "choices",
            JSONArray().put(
                JSONObject()
                    .put("index", 0)
                    .put("delta", delta)
                    .put("finish_reason", finishReason ?: JSONObject.NULL),
            ),
        ).toString()

    private fun sse(vararg chunks: String): String =
        chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"
}
