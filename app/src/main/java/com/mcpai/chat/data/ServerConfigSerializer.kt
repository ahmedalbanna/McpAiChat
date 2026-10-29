package com.mcpai.chat.data

import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.util.text

/**
 * Serialises [McpServerConfig] lists to and from JSON for persistence.
 *
 * The real implementation ([JsonServerConfigSerializer]) uses [org.json.JSONObject]
 * and is backed by [EncryptedStore]. Tests can use [FakeServerConfigSerializer]
 * to avoid Android framework classes on the JVM.
 */
interface ServerConfigSerializer {
    fun load(): List<McpServerConfig>
    fun save(servers: List<McpServerConfig>)
}

/** Production implementation: JSON serialisation via [org.json.JSONObject], encrypted via [EncryptedStore]. */
class JsonServerConfigSerializer(
    private val prefs: EncryptedStore,
) : ServerConfigSerializer {

    override fun load(): List<McpServerConfig> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
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
                    timeoutSeconds = o.optInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS),
                    customHeaders = readCustomHeaders(o),
                    isTrusted = o.optBoolean("is_trusted", false),
                    spkiPin = o.text("spki_pin"),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun save(servers: List<McpServerConfig>) {
        val arr = org.json.JSONArray()
        servers.forEach { s ->
            arr.put(org.json.JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("enabled", s.enabled)
                put("auth_header_name", s.authHeaderName.orEmpty())
                put("auth_header_value", s.authHeaderValue.orEmpty())
                put("timeout_seconds", s.timeoutSeconds)
                val headersObj = org.json.JSONObject()
                s.customHeaders.forEach { (k, v) -> headersObj.put(k, v) }
                put("custom_headers", headersObj)
                put("is_trusted", s.isTrusted)
                put("spki_pin", s.spkiPin.orEmpty())
            })
        }
        prefs.putString(KEY_SERVERS, arr.toString())
    }

    private fun readCustomHeaders(o: org.json.JSONObject): Map<String, String> {
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

    companion object {
        private const val KEY_SERVERS = "mcp_servers"
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}

/** In-memory fake for unit tests — no Android framework classes needed. */
class FakeServerConfigSerializer : ServerConfigSerializer {
    var servers: List<McpServerConfig> = emptyList()

    override fun load(): List<McpServerConfig> = servers
    override fun save(servers: List<McpServerConfig>) { this.servers = servers }
}