package com.mcpai.chat.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tool together with the server that owns it. */
data class RoutedTool(
    val tool: McpTool,
    val serverId: String,
)

/** Result of probing a server. */
sealed interface ServerProbe {
    data class Ok(
        val serverId: String,
        val configName: String,
        val info: McpServerInfo,
        val toolCount: Int,
        val spkiPin: String? = null,
    ) : ServerProbe

    data class Failed(val serverId: String, val configName: String, val reason: String) : ServerProbe
}

/**
 * Holds the live [McpClient] instances for every enabled server and routes
 * tool calls to the server that advertised the tool.
 *
 * Connection is lazy: servers are not connected until [connect] or
 * [ensureConnected] is called. On connection failure, a single retry is
 * attempted before marking the server as failed.
 */
class McpRegistry(
    private val transportFactory: (McpServerConfig) -> McpTransport = { it.transport },
) {

    private val clients = mutableMapOf<String, McpClient>()
    private val toolIndex = mutableMapOf<String, String>() // tool name -> server id
    private val serverNames = mutableMapOf<String, String>() // server id -> config name
    private val storedTools = mutableListOf<McpTool>() // tools from last connect
    private var connectedConfigs: List<McpServerConfig> = emptyList()

    /** Connects (or reconnects) every enabled server and returns the merged tool list. */
    suspend fun connect(configs: List<McpServerConfig>): Pair<List<RoutedTool>, List<ServerProbe>> =
        withContext(Dispatchers.IO) {
            val wanted = configs.filter { it.enabled }.map { it.id }.toSet()
            // drop servers that were removed or disabled
            clients.keys.filter { it !in wanted }.forEach { id ->
                clients.remove(id)?.close()
                serverNames.remove(id)
            }
            toolIndex.clear()
            connectedConfigs = configs.filter { it.enabled }

            val probes = mutableListOf<ServerProbe>()
            val tools = mutableListOf<RoutedTool>()
            storedTools.clear()

            val retryPolicy = ConnectionRetryPolicy(maxAttempts = 2)

            for (config in configs.filter { it.enabled }) {
                var extractedPin: String? = null
                val result = retryPolicy.execute {
                    val transport = transportFactory(config)
                    val client = McpClient(transport).also { c ->
                        c.initialize()
                    }
                    if (!client.initialized) {
                        client.initialize()
                    }
                    val serverTools = client.listTools()
                    tools += serverTools.map { RoutedTool(it, config.id) }
                    storedTools += serverTools
                    serverTools.forEach { toolIndex[it.name] = config.id }
                    serverNames[config.id] = config.name
                    extractedPin = (transport as? HttpMcpTransport)?.extractedSpkiPin
                    probes += ServerProbe.Ok(
                        serverId = config.id,
                        configName = config.name,
                        info = client.serverInfo ?: McpServerInfo(config.name, "", "", null),
                        toolCount = serverTools.size,
                        spkiPin = extractedPin,
                    )
                    clients[config.id] = client
                    client
                }

                if (result.isFailure) {
                    clients.remove(config.id)?.close()
                    probes += ServerProbe.Failed(
                        config.id,
                        config.name,
                        result.exceptionOrNull()?.message ?: result.exceptionOrNull()?.javaClass?.simpleName ?: "Unknown error",
                    )
                }
            }
            tools to probes
        }

    /**
     * Ensures all enabled servers from [configs] are connected. If already connected,
     * does nothing (no redundant network calls).
     */
    suspend fun ensureConnected(configs: List<McpServerConfig>) {
        if (clients.isEmpty() || toolIndex.isEmpty()) {
            connect(configs)
        }
    }

    /** Returns the currently connected tools. */
    fun tools(): List<McpTool> = storedTools.toList()

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

    /** Returns the server id that advertises [toolName], or null if no connected server exposes it. */
    fun serverIdOf(toolName: String): String? = toolIndex[toolName]

    fun closeAll() {
        clients.values.forEach { it.close() }
        clients.clear()
        toolIndex.clear()
        serverNames.clear()
        storedTools.clear()
        connectedConfigs = emptyList()
    }
}