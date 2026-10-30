package com.mcpai.chat.data

import android.content.Context
import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.util.text

/**
 * All user configuration, backed by an [EncryptedStore] and a [ServerConfigSerializer].
 *
 * Values are encrypted with an AES-256-GCM key stored in the Android Keystore, so
 * the OpenAI API key, MCP server URLs and authorization headers never sit in
 * plain text on disk. The key is device-bound: clearing app data or restoring
 * from a backup will make the encrypted values unreadable, and the user will
 * need to re-enter their API key — which is the intended security behaviour.
 */
class Settings internal constructor(
    private val prefs: EncryptedStore,
    private val serverSerializer: ServerConfigSerializer,
) {

    // ---- AI provider -------------------------------------------------------

    var openAiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.putString(KEY_API_KEY, value.trim())

    var openAiBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.putString(KEY_BASE_URL, value.trim().ifEmpty { DEFAULT_BASE_URL })

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.putString(KEY_MODEL, value.trim().ifEmpty { DEFAULT_MODEL })

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.putString(KEY_SYSTEM_PROMPT, value)

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.putFloat(KEY_TEMPERATURE, value.coerceIn(0f, 2f))

    /** When true the model may call MCP tools; when false it only chats. */
    var toolsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOOLS_ENABLED, true)
        set(value) = prefs.putBoolean(KEY_TOOLS_ENABLED, value)

    /**
     * When true every tool call is confirmed before it runs.
     *
     * Defaults to on: an MCP server is third-party code, so a tool call must
     * not happen without the user seeing which tool and which arguments.
     */
    var confirmToolCalls: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_TOOLS, true)
        set(value) = prefs.putBoolean(KEY_CONFIRM_TOOLS, value)

    /**
     * How many past messages are replayed to the provider on each turn.
     *
     * The turn grows in cost and latency with the history length, so it is
     * capped rather than sending the whole conversation. A tool call counts as
     * two messages (the assistant turn plus each tool result), which the cap
     * accounts for — see `ChatRepository.turnHistory`.
     */
    var historyLimit: Int
        get() = prefs.getInt(KEY_HISTORY_LIMIT, DEFAULT_HISTORY_LIMIT)
        set(value) = prefs.putInt(KEY_HISTORY_LIMIT, value.coerceIn(4, 200))

    // ---- MCP servers -------------------------------------------------------

    /** Returns all configured MCP servers. */
    fun servers(): List<McpServerConfig> = serverSerializer.load()

    /** Persists the full server list. */
    fun saveServers(servers: List<McpServerConfig>) = serverSerializer.save(servers)

    /** Adds or replaces a server config by id. */
    fun addServer(config: McpServerConfig) {
        val list = servers().toMutableList()
        val index = list.indexOfFirst { it.id == config.id }
        if (index >= 0) list[index] = config else list.add(config)
        saveServers(list)
    }

    /** Removes the server with the given id, clearing the active selection if it was active. */
    fun removeServer(id: String) {
        val wasActive = activeServerId == id
        saveServers(servers().filterNot { it.id == id })
        if (wasActive) activeServerId = null
    }

    /**
     * Builds a new [McpServerConfig] with sensible defaults for the fields a user
     * has not yet filled in.
     */
    fun newServerConfig(url: String): McpServerConfig = McpServerConfig(
        id = java.util.UUID.randomUUID().toString(),
        name = url.substringAfterLast('/').ifEmpty { "MCP server" },
        url = url,
        enabled = true,
        authHeaderName = DEFAULT_AUTH_HEADER_NAME,
        authHeaderValue = DEFAULT_AUTH_HEADER_VALUE,
        timeoutSeconds = DEFAULT_TIMEOUT_SECONDS,
        customHeaders = emptyMap(),
        isTrusted = false,
        spkiPin = null,
    )

    // ---- active server -----------------------------------------------------

    /** The id of the last-used server, or null when none has been selected. */
    var activeServerId: String?
        get() = prefs.getString(KEY_ACTIVE_SERVER_ID, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.putString(KEY_ACTIVE_SERVER_ID, value.orEmpty())

    fun hasApiKey(): Boolean = openAiApiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant. You have access to MCP tools; use them when they help answer the user."

        const val DEFAULT_AUTH_HEADER_NAME = "Authorization"
        const val DEFAULT_AUTH_HEADER_VALUE = "Bearer <api_key>"
        const val DEFAULT_TIMEOUT_SECONDS = 30

        private const val KEY_API_KEY = "openai_api_key"
        private const val KEY_BASE_URL = "openai_base_url"
        private const val KEY_MODEL = "openai_model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOOLS_ENABLED = "tools_enabled"
        private const val KEY_CONFIRM_TOOLS = "confirm_tool_calls"
        private const val KEY_HISTORY_LIMIT = "history_limit"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"

        /** 40 messages: ~10 exchanges, including the tool calls inside them. */
        const val DEFAULT_HISTORY_LIMIT = 40

        @Volatile
        private var instance: Settings? = null

        /** Creates a [Settings] backed by real [EncryptedPreferences] and [JsonServerConfigSerializer]. */
        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(
                    EncryptedPreferences.create(context),
                    JsonServerConfigSerializer(EncryptedPreferences.create(context)),
                ).also { instance = it }
            }
    }
}

// ---- Float helpers used by Settings (EncryptedStore has no getFloat/putFloat) ----

private fun EncryptedStore.getFloat(key: String, default: Float): Float =
    getString(key, null)?.let { it.toFloatOrNull() } ?: default

private fun EncryptedStore.putFloat(key: String, value: Float) {
    putString(key, value.toString())
}