package com.mcpai.chat.mcp

import com.mcpai.chat.util.text
import org.json.JSONObject

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

/**
 * A tool exposed by an MCP server.
 *
 * The JSON shape follows the MCP specification: a tool has a `name`, a `description`
 * and an `inputSchema` (JSON Schema object).
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject?,
    val serverName: String = "",
) {
    /** The OpenAI `tools[]` entry for this MCP tool. */
    fun toOpenAiTool(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", inputSchema ?: JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            })
        })
    }
}

/** A resource exposed by an MCP server. */
data class McpResource(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String,
)

/** A prompt template exposed by an MCP server. */
data class McpPrompt(
    val name: String,
    val description: String,
    val arguments: List<McpPromptArgument>,
)

data class McpPromptArgument(val name: String, val description: String, val required: Boolean)

/** The outcome of a `tools/call` invocation. */
data class McpToolResult(
    val content: String,
    val isError: Boolean,
    val structuredContent: JSONObject? = null,
) {
    /** Flattens the content blocks of an MCP tool result into plain text for the model. */
    companion object {
        fun fromJson(json: JSONObject): McpToolResult {
            val blocks = json.optJSONArray("content")
            val sb = StringBuilder()
            if (blocks != null) {
                for (i in 0 until blocks.length()) {
                    val block = blocks.optJSONObject(i) ?: continue
                    when (block.text("type").orEmpty()) {
                        "text" -> {
                            if (sb.isNotEmpty()) sb.append('\n')
                            sb.append(block.text("text").orEmpty())
                        }
                        "image" -> sb.append("[image: ").append(block.text("mimeType").orEmpty()).append(']')
                        "resource" -> sb.append(block.optJSONObject("resource")?.text("text").orEmpty().ifEmpty { "[resource]" })
                        else -> sb.append(block.toString())
                    }
                }
            }
            if (sb.isEmpty()) sb.append(json.text("content").orEmpty().ifEmpty { json.toString() })
            return McpToolResult(
                content = sb.toString(),
                isError = json.optBoolean("isError", false),
                structuredContent = json.optJSONObject("structuredContent"),
            )
        }
    }
}

/** Server identity + capabilities returned by `initialize`. */
data class McpServerInfo(
    val name: String,
    val version: String,
    val protocolVersion: String,
    val capabilities: JSONObject?,
)
