package com.mcpai.chat.mcp

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Tests for [StdioMcpTransport] against the reference stdio server.
 *
 * The transport launches a local process, so the test needs Python. When
 * Python is missing the test is skipped rather than failed — the transport
 * itself is still covered by the type system and the device tests.
 */
class StdioMcpTransportTest {

    companion object {
        private lateinit var script: File
        private var python: String? = null

        @BeforeClass
        @JvmStatic
        fun locateRuntime() {
            script = findScript()
            python = findPython()
        }

        /** The reference server lives in `tools/`, one level above the module. */
        private fun findScript(): File {
            var dir: File? = File("").absoluteFile
            while (dir != null) {
                val candidate = File(dir, "tools/stdio_mcp_server.py")
                if (candidate.isFile) return candidate
                dir = dir.parentFile
            }
            throw IllegalStateException("tools/stdio_mcp_server.py not found")
        }

        private fun findPython(): String? {
            for (candidate in listOf("python", "python3", "py")) {
                for (arguments in listOf(listOf("--version"), listOf("-3", "--version"))) {
                    val ok = runCatching {
                        val process = ProcessBuilder(candidate, *arguments.toTypedArray())
                            .redirectErrorStream(true)
                            .start()
                        process.inputStream.readBytes()
                        process.waitFor() == 0
                    }.getOrDefault(false)
                    if (ok) return candidate
                }
            }
            return null
        }
    }

    @Test
    fun `the handshake lists the tools and runs one`() = runBlocking {
        assumeTrue("python is not installed", python != null)

        val transport = StdioMcpTransport(listOf(python!!, script.absolutePath))
        val client = McpClient(transport)
        try {
            val info = client.initialize()
            assertEquals("stdio-reference-mcp-server", info.name)
            assertEquals(McpClient.PROTOCOL_VERSION, info.protocolVersion)

            val tools = client.listTools()
            assertEquals(listOf("echo", "now", "add"), tools.map { it.name })

            val result = client.callTool("echo", JSONObject().put("text", "hello"))
            assertEquals("hello", result.content)
            assertTrue(!result.isError)
        } finally {
            client.close()
        }
    }

    @Test
    fun `an unknown method comes back as an MCP error`() = runBlocking {
        assumeTrue("python is not installed", python != null)

        val transport = StdioMcpTransport(listOf(python!!, script.absolutePath))
        val client = McpClient(transport)
        try {
            client.initialize()
            val error = runCatching { client.listResources() }.exceptionOrNull()
            assertTrue("expected an McpException, got $error", error is McpException)
            assertEquals(-32601, (error as McpException).code)
        } finally {
            client.close()
        }
    }

    @Test
    fun `a notification does not wait for a reply`() = runBlocking {
        assumeTrue("python is not installed", python != null)

        val transport = StdioMcpTransport(listOf(python!!, script.absolutePath))
        try {
            // The handshake sends notifications/initialized, which the server
            // answers with nothing. Waiting for a reply would hang here.
            val answer = transport.send(
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "notifications/initialized")
                },
            )
            assertEquals(0, answer.length())
        } finally {
            transport.close()
        }
    }
}
