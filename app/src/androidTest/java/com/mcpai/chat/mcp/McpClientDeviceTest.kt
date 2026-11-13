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
