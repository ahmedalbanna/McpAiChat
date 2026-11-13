package com.mcpai.chat.mcp

import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A tiny MCP server that answers JSON-RPC over HTTP on loopback.
 *
 * It exists so the client and the chat engine can be exercised against a real
 * socket without pulling a server onto the device. Shared by the client
 * device tests and the tool-approval UI tests.
 */
internal class CannedMcpServer {

    private var socket: ServerSocket? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    /** When true, `tools/list` answers with a JSON-RPC error. */
    var failToolsList = false

    /** How many `tools/call` requests have been served. */
    val toolsCallCount = AtomicInteger(0)

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

    fun url(failToolsList: Boolean = false): String {
        this.failToolsList = failToolsList
        return "http://127.0.0.1:${socket!!.localPort}/mcp"
    }

    private fun serve(client: Socket) {
        client.use { connection ->
            val input = connection.getInputStream()
            val output = connection.getOutputStream()
            while (true) {
                val body = readRequest(input) ?: return
                val response = respond(body)
                val bytes = response.toByteArray(Charsets.UTF_8)
                output.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "\r\n"
                        ).toByteArray(Charsets.UTF_8),
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
        val request = runCatching { JSONObject(body) }.getOrNull()
            ?: return """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"parse error"}}"""
        val id = request.opt("id")
        return when (request.optString("method")) {
            "initialize" -> result(id, """
                {
                  "protocolVersion": "${McpClient.PROTOCOL_VERSION}",
                  "capabilities": {"tools": {}},
                  "serverInfo": {"name": "canned-server", "version": "1.0"}
                }
            """.trimIndent())

            "tools/list" -> if (failToolsList) {
                """{"jsonrpc":"2.0","id":${id ?: "null"},"error":{"code":-32000,"message":"tools are unavailable"}}"""
            } else {
                result(id, """
                    {
                      "tools": [
                        {
                          "name": "echo",
                          "description": "Echoes its argument",
                          "inputSchema": {"type": "object", "properties": {"text": {"type": "string"}}}
                        }
                      ]
                    }
                """.trimIndent())
            }

            "tools/call" -> {
                toolsCallCount.incrementAndGet()
                val args = request.optJSONObject("params")?.optJSONObject("arguments")
                result(id, """{"content":[{"type":"text","text":${quote(args?.optString("text").orEmpty())}}]}""")
            }

            else -> result(id, "{}")
        }
    }

    private fun result(id: Any?, payload: String): String =
        """{"jsonrpc":"2.0","id":${id ?: "null"},"result":$payload}"""

    private fun quote(text: String): String = JSONObject.quote(text)
}
