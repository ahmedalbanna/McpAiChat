package com.mcpai.chat.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** A tool together with the server that owns it. */
data class RoutedTool(
    val tool: McpTool,
    val serverId: String,
)

/** Result of probing a server. */
sealed interface ServerProbe {
    /** The server this result is about — common to both outcomes. */
    val serverId: String
    val configName: String

    data class Ok(
        override val serverId: String,
        override val configName: String,
        val info: McpServerInfo,
        val toolCount: Int,
        val spkiPin: String? = null,
    ) : ServerProbe

    data class Failed(
        override val serverId: String,
        override val configName: String,
        val reason: String,
    ) : ServerProbe
}

/** What one server contributed to a [McpRegistry.connect] pass. */
private data class ServerOutcome(
    val serverId: String,
    val tools: List<McpTool>,
    val probe: ServerProbe,
    /** A freshly handshaked client to install, or null when the old one stays. */
    val newClient: McpClient? = null,
    /** True when the server could not be reached and its old client must go. */
    val failed: Boolean = false,
)

/**
 * Holds the live [McpClient] instances for every enabled server and routes
 * tool calls to the server that advertised the tool.
 *
 * Connection is lazy: servers are not connected until [connect] is called.
 * Three properties matter for how fast the app feels:
 *
 *  - **Handshakes run concurrently.** Every JSON-RPC message is one transport
 *    round trip, so a sequential loop makes the user wait N× as long for N
 *    servers. Servers are probed in parallel and merged in configuration order.
 *  - **Live sessions are reused.** Re-entering the server list used to close and
 *    re-handshake every server, which also threw away the `Mcp-Session-Id` and
 *    made the chat feel like it reloaded. A server whose URL is unchanged is
 *    left connected and its tools are answered from the registry.
 *  - **A server with no tools is not re-probed.** Whether a server contributed
 *    tools has nothing to do with whether it is connected, so an empty tool list
 *    must not force a reconnect on the next message.
 */
class McpRegistry(
    private val transportFactory: (McpServerConfig) -> McpTransport = { it.transport },
) {

    private val clients = mutableMapOf<String, McpClient>()
    private val toolsByServer = mutableMapOf<String, List<McpTool>>()
    private val toolIndex = mutableMapOf<String, String>() // tool name -> server id
    private val serverNames = mutableMapOf<String, String>() // server id -> config name

    /** The configuration behind the live sessions, used to detect what changed. */
    private var connectedConfigs: List<McpServerConfig> = emptyList()

    /** True once every enabled server has been probed at least once. */
    private var probed = false

    /** Connects (or reconnects) every enabled server and returns the merged tool list. */
    suspend fun connect(configs: List<McpServerConfig>): Pair<List<RoutedTool>, List<ServerProbe>> =
        withContext(Dispatchers.IO) {
            val enabled = configs.filter { it.enabled }
            val wanted = enabled.map { it.id }.toSet()

            // Drop servers the user disabled or removed.
            val removed = clients.keys.filter { it !in wanted }
            removed.forEach { id ->
                clients.remove(id)?.close()
                serverNames.remove(id)
                toolsByServer.remove(id)
            }
            val changed = connectedConfigs.filter { it.id in wanted } != enabled.filter { it.id in wanted }

            // Snapshot the live state before handshaking in parallel. The
            // `async` workers below must not touch the shared maps — two
            // threads putting into a plain HashMap can silently lose an entry,
            // which would leave a server half-registered and make the next
            // connect see the wrong catalogue.
            val snapshotClients = clients.toMap()
            val snapshotTools = toolsByServer.toMap()
            val snapshotConfigs = connectedConfigs.toList()

            val outcomes = coroutineScope {
                enabled
                    .map { config ->
                        async {
                            connectOne(
                                config = config,
                                existing = snapshotClients[config.id],
                                previous = snapshotConfigs.firstOrNull { it.id == config.id },
                                previousTools = snapshotTools[config.id].orEmpty(),
                            )
                        }
                    }
                    .awaitAll()
            }

            // Install results on the calling thread, in configuration order.
            for (outcome in outcomes) {
                val newClient = outcome.newClient
                if (newClient != null) {
                    clients.remove(outcome.serverId)?.close()
                    clients[outcome.serverId] = newClient
                }
                if (outcome.failed) {
                    clients.remove(outcome.serverId)?.close()
                }
                if (outcome.probe is ServerProbe.Ok) {
                    serverNames[outcome.serverId] = outcome.probe.configName
                }
            }

            toolIndex.clear()
            toolsByServer.clear()
            val tools = mutableListOf<RoutedTool>()
            val probes = mutableListOf<ServerProbe>()
            for (outcome in outcomes) {
                probes += outcome.probe
                toolsByServer[outcome.serverId] = outcome.tools
                outcome.tools.forEach { tool ->
                    toolIndex[tool.name] = outcome.serverId
                }
            }
            // Merged in configuration order, so the tool list never reorders
            // between turns while parallel handshakes finish out of order.
            enabled.forEach { config ->
                toolsByServer[config.id].orEmpty().forEach { tool ->
                    tools += RoutedTool(tool, config.id)
                }
            }

            if (outcomes.none { it.probe is ServerProbe.Failed } || changed) {
                connectedConfigs = enabled
                probed = true
            }
            tools to probes
        }

    /**
     * Probes one server, without touching any shared state.
     *
     * The caller passes a snapshot of the live clients, so the concurrent
     * workers never race on the registry's maps; a fresh client is handed back
     * for the caller to install on its own thread.
     */
    private suspend fun connectOne(
        config: McpServerConfig,
        existing: McpClient?,
        previous: McpServerConfig?,
        previousTools: List<McpTool>,
    ): ServerOutcome {
        val reusable = existing != null &&
            existing.initialized &&
            previous != null &&
            previous.url == config.url &&
            authOf(previous) == authOf(config)

        if (reusable) {
            return ServerOutcome(
                serverId = config.id,
                tools = previousTools,
                probe = ServerProbe.Ok(
                    serverId = config.id,
                    configName = config.name,
                    info = existing!!.serverInfo ?: McpServerInfo(config.name, "", "", null),
                    toolCount = previousTools.size,
                    spkiPin = config.spkiPin,
                ),
            )
        }

        val retryPolicy = ConnectionRetryPolicy(maxAttempts = 2)
        var extractedPin: String? = null
        var attemptClient: McpClient? = null
        val result = retryPolicy.execute {
            // The handshake is three round trips, so a single unresponsive
            // server would otherwise hold the whole catalogue for the
            // configured timeout (default 30s) before the retry even starts.
            // Tool *calls* legitimately take a long time, so only the
            // handshake gets this shorter ceiling.
            val transport = transportFactory(config.copy(timeoutSeconds = minOf(config.timeoutSeconds, HANDSHAKE_TIMEOUT_CAP_SECONDS)))
            val client = McpClient(transport)
            attemptClient = client
            try {
                client.initialize()
                val serverTools = client.listTools()
                extractedPin = (transport as? HttpMcpTransport)?.extractedSpkiPin
                client to serverTools
            } catch (t: Throwable) {
                client.close()
                attemptClient = null
                throw t
            }
        }

        return if (result.isSuccess) {
            val (client, serverTools) = result.getOrThrow()
            if (attemptClient !== client) client.close()
            ServerOutcome(
                serverId = config.id,
                tools = serverTools,
                probe = ServerProbe.Ok(
                    serverId = config.id,
                    configName = config.name,
                    info = client.serverInfo ?: McpServerInfo(config.name, "", "", null),
                    toolCount = serverTools.size,
                    spkiPin = extractedPin,
                ),
                newClient = client,
            )
        } else {
            attemptClient?.close()
            val reason = result.exceptionOrNull()?.message
                ?: result.exceptionOrNull()?.javaClass?.simpleName
                ?: "Unknown error"
            ServerOutcome(config.id, emptyList(), ServerProbe.Failed(config.id, config.name, reason), failed = true)
        }
    }

    /**
     * Connects any enabled server that has not been probed yet, and does
     * nothing when the catalogue is already known.
     *
     * This is what the chat screen calls when it opens, so the handshake
     * overlaps with the user typing instead of blocking their first message.
     */
    suspend fun ensureConnected(configs: List<McpServerConfig>) {
        if (!probed || clients.isEmpty()) connect(configs)
    }

    /** Whether [ensureConnected] would still have work to do. */
    val isWarm: Boolean get() = probed && clients.isNotEmpty()

    /** Returns the currently connected tools. */
    fun tools(): List<McpTool> =
        connectedConfigs.flatMap { config -> toolsByServer[config.id].orEmpty() }

    /** Calls a tool on whichever server advertised it. */
    suspend fun callTool(toolName: String, arguments: org.json.JSONObject): McpToolResult {
        val serverId = toolIndex[toolName]
            ?: throw McpException("callTool", -1, "No connected MCP server exposes a tool named '$toolName'")
        val client = clients[serverId]
            ?: throw McpException("callTool", -1, "MCP server '$serverId' is not connected")
        return client.callTool(toolName, arguments)
    }

    fun isConnected(serverId: String): Boolean = clients[serverId]?.initialized == true

    /**
     * The display name of the server that advertises [toolName], or null when no
     * connected server exposes it. Shown in the tool-approval dialog so the user
     * knows which remote endpoint is about to run.
     */
    fun serverNameOf(toolName: String): String? {
        val serverId = toolIndex[toolName] ?: return null
        return serverNames[serverId] ?: serverId
    }

    /** Returns the server id that advertises [toolName], or null. */
    fun serverIdOf(toolName: String): String? = toolIndex[toolName]

    fun closeAll() {
        clients.values.forEach { it.close() }
        clients.clear()
        toolsByServer.clear()
        toolIndex.clear()
        serverNames.clear()
        connectedConfigs = emptyList()
        probed = false
    }

    companion object {
        /** Ceiling for one handshake attempt (connect + read). */
        private const val HANDSHAKE_TIMEOUT_CAP_SECONDS = 12
    }
}

/** The auth-relevant fields of a config, so a header edit forces a reconnect. */
private fun authOf(config: McpServerConfig): String =
    "${config.authHeaderName.orEmpty()}|${config.authHeaderValue.orEmpty()}|" +
        config.customHeaders.entries.joinToString(",") { "${it.key}=${it.value}" }
