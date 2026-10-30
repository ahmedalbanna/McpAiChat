package com.mcpai.chat.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A configured MCP server the app can talk to. */
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val authHeaderName: String? = null,
    val authHeaderValue: String? = null,
    val timeoutSeconds: Int = 30,
    val customHeaders: Map<String, String> = emptyMap(),
    val isTrusted: Boolean = false,
    val spkiPin: String? = null,
) {
    /** Authorization header value built from name + value, or null when not configured. */
    val authHeader: String?
        get() = if (authHeaderValue.orEmpty().isBlank()) null else buildString {
            append(authHeaderName.orEmpty().trim())
            append(": ")
            append(authHeaderValue.orEmpty().trim())
        }

    val transport: McpTransport
        get() = HttpMcpTransport(
            baseUrl = url,
            headers = buildMap {
                authHeaderName?.takeIf { it.isNotBlank() }?.let { put(it, authHeaderValue.orEmpty()) }
                customHeaders.filterKeys { it.isNotBlank() }.forEach { (k, v) -> put(k, v) }
            }.takeIf { it.isNotEmpty() } ?: emptyMap(),
            connectTimeoutMs = timeoutSeconds * 1000,
            readTimeoutMs = timeoutSeconds * 1000,
            spkiPin = spkiPin,
        )
}

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
                val result = retryPolicy.execute {
                    val client = McpClient(transportFactory(config)).also { c ->
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
                    probes += ServerProbe.Ok(
                        serverId = config.id,
                        configName = config.name,
                        info = client.serverInfo ?: McpServerInfo(config.name, "", "", null),
                        toolCount = serverTools.size,
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