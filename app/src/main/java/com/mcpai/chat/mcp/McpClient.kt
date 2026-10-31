package com.mcpai.chat.mcp

import com.mcpai.chat.util.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * A minimal, dependency-free MCP client speaking JSON-RPC 2.0.
 *
 * Implements the client half of the Model Context Protocol:
 *  - `initialize` / `notifications/initialized` handshake
 *  - `tools/list`, `tools/call`
 *  - `resources/list`, `resources/read`
 *  - `prompts/list`, `prompts/get`
 *  - `ping`
 *
 * Server-initiated requests (for example `sampling/createMessage` or
 * `roots/list`) are answered with a JSON-RPC error so the conversation can
 * continue; callers can inspect them through [onServerRequest].
 */
class McpClient(
    private val transport: McpTransport,
    private val clientName: String = "mcp-ai-chat",
    private val clientVersion: String = "1.0.0",
) {
    private val ids = AtomicLong(0)

    @Volatile
    var serverInfo: McpServerInfo? = null
        private set

    @Volatile
    var initialized: Boolean = false
        private set

    /** Invoked when the server sends a request to the client. */
    var onServerRequest: ((JSONObject) -> Unit)? = null

    private suspend fun request(method: String, params: JSONObject?): JSONObject {
        val id = ids.incrementAndGet()
        val message = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val reply = transport.send(message)
        if (reply.has("error")) {
            val err = reply.getJSONObject("error")
            throw McpException(
                method = method,
                code = err.optInt("code", -1),
                message = err.text("message") ?: "unknown MCP error",
            )
        }
        return reply.optJSONObject("result") ?: JSONObject()
    }

    private suspend fun notify(method: String, params: JSONObject? = null) {
        val message = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) put("params", params)
        }
        transport.send(message)
    }

    /** Performs the MCP handshake. Safe to call once per connection. */
    suspend fun initialize(): McpServerInfo = withContext(Dispatchers.IO) {
        val result = request(
            "initialize",
            JSONObject().apply {
                put("protocolVersion", PROTOCOL_VERSION)
                put("capabilities", JSONObject().apply {
                    put("roots", JSONObject().apply { put("listChanged", true) })
                    put("sampling", JSONObject())
                })
                put("clientInfo", JSONObject().apply {
                    put("name", clientName)
                    put("version", clientVersion)
                })
            },
        )
        notify("notifications/initialized")
        initialized = true
        val info = result.optJSONObject("serverInfo")
        McpServerInfo(
            name = info?.text("name") ?: transport.displayName,
            version = info?.text("version") ?: "",
            protocolVersion = result.text("protocolVersion") ?: PROTOCOL_VERSION,
            capabilities = result.optJSONObject("capabilities"),
        ).also { serverInfo = it }
    }

    suspend fun listTools(): List<McpTool> {
        val result = request("tools/list", JSONObject())
        val arr = result.optJSONArray("tools") ?: return emptyList()
        val serverName = serverInfo?.name ?: transport.displayName
        return (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONObject(i) ?: return@mapNotNull null
            McpTool(
                name = t.text("name").orEmpty(),
                description = t.text("description").orEmpty(),
                inputSchema = t.optJSONObject("inputSchema"),
                serverName = serverName,
            )
        }
    }

    suspend fun callTool(name: String, arguments: JSONObject): McpToolResult {
        val result = request(
            "tools/call",
            JSONObject().apply {
                put("name", name)
                put("arguments", arguments)
            },
        )
        return McpToolResult.fromJson(result)
    }

    suspend fun listResources(): List<McpResource> {
        val result = request("resources/list", JSONObject())
        val arr = result.optJSONArray("resources") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            McpResource(
                uri = r.text("uri").orEmpty(),
                name = r.text("name").orEmpty(),
                description = r.text("description").orEmpty(),
                mimeType = r.text("mimeType").orEmpty(),
            )
        }
    }

    suspend fun readResource(uri: String): String {
        val result = request("resources/read", JSONObject().apply { put("uri", uri) })
        val contents = result.optJSONArray("contents") ?: return result.toString()
        val sb = StringBuilder()
        for (i in 0 until contents.length()) {
            val c = contents.optJSONObject(i) ?: continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(c.text("text").orEmpty().ifEmpty { c.toString() })
        }
        return sb.toString()
    }

    suspend fun listPrompts(): List<McpPrompt> {
        val result = request("prompts/list", JSONObject())
        val arr = result.optJSONArray("prompts") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val args = p.optJSONArray("arguments")
            McpPrompt(
                name = p.text("name").orEmpty(),
                description = p.text("description").orEmpty(),
                arguments = (0 until (args?.length() ?: 0)).map { j ->
                    val a = args!!.optJSONObject(j)!!
                    McpPromptArgument(
                        name = a.text("name").orEmpty(),
                        description = a.text("description").orEmpty(),
                        required = a.optBoolean("required", false),
                    )
                },
            )
        }
    }

    suspend fun getPrompt(name: String, args: Map<String, String> = emptyMap()): String {
        val result = request(
            "prompts/get",
            JSONObject().apply {
                put("name", name)
                put("arguments", JSONObject().apply { args.forEach { (k, v) -> put(k, v) } })
            },
        )
        val messages = result.optJSONArray("messages") ?: return result.toString()
        val sb = StringBuilder()
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(m.optJSONObject("content")?.text("text").orEmpty())
        }
        return sb.toString()
    }

    suspend fun ping(): Boolean = runCatching { request("ping", JSONObject()) }.isSuccess

    fun close() = transport.close()

    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
    }
}

/** Error raised when an MCP server answers with a JSON-RPC error object. */
class McpException(
    val method: String,
    val code: Int,
    message: String,
) : Exception("$method failed ($code): $message")
