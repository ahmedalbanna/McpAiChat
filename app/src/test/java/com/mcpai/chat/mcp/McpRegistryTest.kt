package com.mcpai.chat.mcp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behaviour that decides how fast the chat screen gets its tools.
 *
 * The fake transport is the seam: [McpRegistry] only ever sees
 * `(config) -> McpTransport`, so these tests run on the JVM with no sockets.
 */
class McpRegistryTest {

    private fun config(
        id: String,
        name: String = id,
        url: String = "https://$id.example/mcp",
        enabled: Boolean = true,
    ) = McpServerConfig(id = id, name = name, url = url, enabled = enabled)

    /** A transport that answers the MCP calls a handshake makes. */
    private class FakeTransport(
        private val tools: List<String> = emptyList(),
        private val onSend: () -> Unit = {},
    ) : McpTransport {
        override val displayName: String = "fake"
        val calls = AtomicInteger(0)

        override suspend fun send(message: JSONObject): JSONObject {
            calls.incrementAndGet()
            onSend()
            return replyTo(message).also { reply ->
                if (message.optString("method") == "tools/list") {
                    reply.put(
                        "result",
                        JSONObject().put(
                            "tools",
                            JSONArray().apply {
                                tools.forEach { put(JSONObject().put("name", it).put("description", it)) }
                            },
                        ),
                    )
                }
            }
        }

        override fun close() {}
    }

    /** Registry wired to one fake transport per configured server. */
    private fun registryOf(
        vararg configs: McpServerConfig,
        toolsFor: (McpServerConfig) -> List<String> = { emptyList() },
        onSend: () -> Unit = {},
    ): Pair<McpRegistry, MutableList<FakeTransport>> {
        val made = mutableListOf<FakeTransport>()
        val registry = McpRegistry(
            transportFactory = { cfg ->
                FakeTransport(toolsFor(cfg), onSend).also { made += it }
            },
        )
        return registry to made
    }

    @Test
    fun `every enabled server is probed`() = runBlocking {
        val (registry, transports) = registryOf(config("a"), config("b"), config("c"))

        val (tools, probes) = registry.connect(listOf(config("a"), config("b"), config("c")))

        assertEquals(3, probes.size)
        assertTrue(probes.all { it is ServerProbe.Ok })
        assertEquals(listOf("a", "b", "c"), probes.map { it.serverId })
        // 3 messages per server: initialize, notifications/initialized, tools/list.
        assertEquals(9, transports.sumOf { it.calls.get() })
        assertTrue(tools.isEmpty())
    }

    @Test
    fun `tools are merged in configuration order`() = runBlocking {
        val names = mapOf(
            "a" to listOf("alpha"),
            "b" to listOf("beta1", "beta2"),
            "c" to listOf("gamma"),
        )
        val configs = listOf(config("a"), config("b"), config("c"))
        val registry = McpRegistry(transportFactory = { FakeTransport(names[it.id].orEmpty()) })

        val (tools, _) = registry.connect(configs)

        assertEquals(
            listOf("alpha", "beta1", "beta2", "gamma"),
            tools.map { it.tool.name },
        )
        assertEquals("b", tools[1].serverId)
        assertEquals("b", registry.serverIdOf("beta2"))
        assertEquals("b", registry.serverNameOf("beta1"))
    }

    @Test
    fun `a second connect with the same configs reuses the live session`() = runBlocking {
        val (registry, transports) = registryOf(config("a"))
        val configs = listOf(config("a"))

        registry.connect(configs)
        val afterFirst = transports.sumOf { it.calls.get() }
        registry.connect(configs)

        assertEquals("a reconnect must not re-handshake", afterFirst, transports.sumOf { it.calls.get() })
        assertTrue(registry.isWarm)
    }

    @Test
    fun `changing a server url forces a reconnect`() = runBlocking {
        val (registry, transports) = registryOf(config("a"))

        registry.connect(listOf(config("a", url = "https://a.example/mcp")))
        val afterFirst = transports.sumOf { it.calls.get() }
        registry.connect(listOf(config("a", url = "https://moved.example/mcp")))

        assertTrue("a moved server must re-handshake", transports.sumOf { it.calls.get() } > afterFirst)
    }

    @Test
    fun `a warm registry answers ensureConnected without probing again`() = runBlocking {
        val (registry, transports) = registryOf(config("a"))

        registry.connect(listOf(config("a")))
        val afterFirst = transports.sumOf { it.calls.get() }
        registry.ensureConnected(listOf(config("a")))

        assertEquals(afterFirst, transports.sumOf { it.calls.get() })
    }

    @Test
    fun `a cold registry is probed by ensureConnected`() = runBlocking {
        val (registry, transports) = registryOf(config("a"))

        registry.ensureConnected(listOf(config("a")))

        assertEquals(3, transports.single().calls.get())
        assertTrue(registry.isWarm)
    }

    @Test
    fun `a server exposing no tools still leaves the registry warm`() = runBlocking {
        // The bug this guards: an empty tool list used to be read as "not
        // connected yet", so every message triggered another full handshake.
        val (registry, _) = registryOf(config("solo", name = "Solo server"))

        registry.connect(listOf(config("solo", name = "Solo server")))

        assertTrue(registry.isConnected("solo"))
        assertTrue(registry.tools().isEmpty())
        assertTrue(registry.isWarm)
    }

    @Test
    fun `disabled and removed servers are dropped`() = runBlocking {
        val (registry, _) = registryOf(config("a"), config("b"))

        registry.connect(listOf(config("a"), config("b")))
        assertTrue(registry.isConnected("a"))

        registry.connect(listOf(config("a", enabled = false)))

        assertFalse(registry.isConnected("a"))
        assertFalse(registry.isConnected("b"))
    }

    @Test
    fun `closeAll resets warm state`() = runBlocking {
        val (registry, _) = registryOf(config("a"))

        registry.connect(listOf(config("a")))
        assertTrue(registry.isWarm)
        registry.closeAll()

        assertFalse(registry.isWarm)
        assertFalse(registry.isConnected("a"))
        assertTrue(registry.tools().isEmpty())
    }

    @Test
    fun `handshakes overlap instead of running one server at a time`() = runBlocking {
        // Each server blocks on a shared barrier until all three have reached
        // the transport. A sequential loop can never get past one, so this also
        // fails fast instead of hanging.
        val barrier = CompletableDeferred<Unit>()
        val entered = ConcurrentHashMap.newKeySet<String>()
        val registry = McpRegistry(
            transportFactory = { cfg ->
                object : McpTransport {
                    override val displayName: String = cfg.url
                    override suspend fun send(message: JSONObject): JSONObject {
                        entered += cfg.id
                        if (entered.size == 3) barrier.complete(Unit)
                        barrier.await()
                        return replyTo(message)
                    }
                    override fun close() {}
                }
            },
        )

        val finished = withTimeout(5_000) {
            val job = launch {
                registry.connect(listOf(config("a"), config("b"), config("c")))
            }
            // Wait for the third server to arrive, then let them all proceed.
            while (entered.size < 3 && job.isActive) yield()
            barrier.complete(Unit)
            job.join()
            true
        }

        assertTrue(finished)
        assertEquals("servers were probed one at a time", setOf("a", "b", "c"), entered)
    }
}

/** The JSON-RPC shape a fake server answers with. */
private fun replyTo(message: JSONObject): JSONObject {
    val reply = JSONObject().put("jsonrpc", "2.0")
    if (message.has("id")) reply.put("id", message.getLong("id"))
    val result = when (message.optString("method")) {
        "initialize" -> JSONObject()
            .put("serverInfo", JSONObject().put("name", "fake"))
            .put("protocolVersion", McpClient.PROTOCOL_VERSION)

        "tools/list" -> JSONObject().put("tools", JSONArray())
        else -> JSONObject()
    }
    return reply.put("result", result)
}
