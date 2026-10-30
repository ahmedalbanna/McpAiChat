package com.mcpai.chat.data

import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.util.text
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports MCP server settings (and tool approvals) as JSON.
 *
 * Sensitive values (auth header values, SPKI pins) are excluded by default
 * and only included when [includeSensitive] is true.
 */
class SettingsExporter {

    /**
     * Serialises [servers] to a JSON string.
     *
     * @param includeSensitive when true, auth header values and SPKI pins are
     *   included; when false (default), they are omitted.
     */
    fun export(servers: List<McpServerConfig>, includeSensitive: Boolean = false): String {
        val arr = JSONArray()
        for (s in servers) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("enabled", s.enabled)
                put("auth_header_name", s.authHeaderName.orEmpty())
                if (includeSensitive) {
                    put("auth_header_value", s.authHeaderValue.orEmpty())
                }
                put("timeout_seconds", s.timeoutSeconds)
                val headersObj = JSONObject()
                s.customHeaders.forEach { (k, v) -> headersObj.put(k, v) }
                put("custom_headers", headersObj)
                put("is_trusted", s.isTrusted)
                if (includeSensitive) {
                    put("spki_pin", s.spkiPin.orEmpty())
                }
            }
            arr.put(obj)
        }
        return JSONObject().apply {
            put("version", 1)
            put("servers", arr)
        }.toString()
    }

    /**
     * Parses a JSON string produced by [export] and returns the list of
     * [McpServerConfig] instances. Returns an empty list on parse failure.
     */
    fun import(json: String): List<McpServerConfig> {
        return runCatching {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("servers") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.text("id") ?: return@mapNotNull null
                val name = o.text("name").orEmpty().ifEmpty { "Server ${i + 1}" }
                val url = o.text("url").orEmpty()
                if (url.isBlank()) return@mapNotNull null
                McpServerConfig(
                    id = id,
                    name = name,
                    url = url,
                    enabled = o.optBoolean("enabled", true),
                    authHeaderName = o.text("auth_header_name"),
                    authHeaderValue = o.text("auth_header_value"),
                    timeoutSeconds = o.optInt("timeout_seconds", 30),
                    customHeaders = readCustomHeaders(o),
                    isTrusted = o.optBoolean("is_trusted", false),
                    spkiPin = o.text("spki_pin"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Serialises [approvals] to a JSON string. */
    fun exportToolApprovals(approvals: List<ToolApproval>): String {
        val arr = JSONArray()
        for (a in approvals) {
            arr.put(JSONObject().apply {
                put("server_id", a.serverId)
                put("tool_name", a.toolName)
                put("is_auto_approved", a.isAutoApproved)
            })
        }
        return JSONObject().apply {
            put("version", 1)
            put("tool_approvals", arr)
        }.toString()
    }

    /** Parses a JSON string produced by [exportToolApprovals]. */
    fun importToolApprovals(json: String): List<ToolApproval> {
        return runCatching {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("tool_approvals") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val serverId = o.text("server_id") ?: return@mapNotNull null
                val toolName = o.text("tool_name") ?: return@mapNotNull null
                ToolApproval(
                    serverId = serverId,
                    toolName = toolName,
                    isAutoApproved = o.optBoolean("is_auto_approved", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun readCustomHeaders(o: JSONObject): Map<String, String> {
        val headers = o.optJSONObject("custom_headers") ?: return emptyMap()
        val names = headers.names() ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until names.length()) {
            val key = names.getString(i)
            val value = headers.optString(key)
            if (value.isNotBlank()) map[key] = value
        }
        return map.toMap()
    }
}