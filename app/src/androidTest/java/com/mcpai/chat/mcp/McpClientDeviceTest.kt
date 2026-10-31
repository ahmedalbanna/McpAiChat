package com.mcpai.chat.mcp

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Device-side test of the MCP client over real HTTP.
 *
 * A canned server on loopback answers the JSON-RPC calls, so the test covers
 * the parts that only exist on a phone: the Android HTTP stack, the network
 * security config (which has to parse on API 26 or every HTTPS call fails) and
 * the transport's own request/response handling.
 */
@RunWith(AndroidJUnit4::class)
class McpClientDeviceTest {

    private lateinit var server: CannedMcpServer

    @Before
    fun setUp() {
        server = CannedMcpServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun theHandshakeListsToolsAndRunsOne() = runBlocking {
        val transport = HttpMcpTransport(baseUrl = server.url())
        val client = McpClient(transport)

        val info = client.initialize()
        assertEquals("canned-server", info.name)
        assertEquals(McpClient.PROTOCOL_VERSION, info.protocolVersion)

        val tools = client.listTools()
        assertEquals(listOf("echo"), tools.map { it.name })

        val result = client.callTool("echo", JSONObject().put("text", "hello"))
        assertEquals("hello", result.content)
        assertTrue(!result.isError)

        client.close()
    }

    @Test
    fun aServerErrorBecomesAnMcpException() = runBlocking {
        val transport = HttpMcpTransport(baseUrl = server.url(failToolsList = true))
        val client = McpClient(transport)

        client.initialize()
        val error = runCatching { client.listTools() }.exceptionOrNull()
        assertTrue("expected an McpException, got $error", error is McpException)
        assertEquals(-32000, (error as McpException).code)

        client.close()
    }
}

/**
 * A tiny MCP server that answers JSON-RPC over HTTP on loopback.
 *
 * It exists so the client can be exercised against a real socket without
 * pulling a server onto the device.
 */
private class CannedMcpServer {

    private var socket: ServerSocket? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    /** When true, `tools/list` answers with a JSON-RPC error. */
    var failToolsList = false

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
            val reader = connection.getInputStream().bufferedReader(Charsets.UTF_8)
            val output = connection.getOutputStream()
            while (true) {
                val body = readBody(reader) ?: return
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

    /** Reads one HTTP request and returns its body, or null when the peer is gone. */
    private fun readBody(reader: BufferedReader): String? {
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toInt()
            }
        }
        if (contentLength == 0) return ""
        val body = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = reader.read(body, read, contentLength - read)
            if (count < 0) return null
            read += count
        }
        return String(body)
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
