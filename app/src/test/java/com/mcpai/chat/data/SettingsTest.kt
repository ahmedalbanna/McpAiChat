package com.mcpai.chat.data

import com.mcpai.chat.mcp.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [Settings] server-config CRUD and active-server tracking.
 *
 * Uses [FakeEncryptedStore] and [FakeServerConfigSerializer] so no Android
 * Context, Keystore, or org.json classes are needed on the JVM.
 */
class SettingsTest {

    private lateinit var store: FakeEncryptedStore
    private lateinit var serializer: FakeServerConfigSerializer
    private lateinit var settings: Settings

    @Before
    fun setUp() {
        store = FakeEncryptedStore()
        serializer = FakeServerConfigSerializer()
        settings = Settings(store, serializer)
    }

    // ---- servers() / saveServers() round trip ---------------------------------

    @Test
    fun `servers returns empty list when nothing saved`() {
        assertTrue(settings.servers().isEmpty())
    }

    @Test
    fun `addServer then servers returns the saved config`() {
        val config = McpServerConfig(
            id = "srv-1",
            name = "My Server",
            url = "https://example.com/mcp",
            enabled = true,
            authHeaderName = "Authorization",
            authHeaderValue = "Bearer secret-123",
            timeoutSeconds = 60,
            customHeaders = mapOf("X-Custom" to "value"),
            isTrusted = true,
            spkiPin = "sha256:abcd1234",
        )
        settings.addServer(config)

        val saved = settings.servers()
        assertEquals(1, saved.size)
        assertEquals(config, saved.first())
    }

    @Test
    fun `addServer with duplicate id replaces existing`() {
        settings.addServer(
            McpServerConfig(id = "srv-1", name = "First", url = "https://a.com/mcp"),
        )
        settings.addServer(
            McpServerConfig(id = "srv-1", name = "Second", url = "https://b.com/mcp"),
        )

        val saved = settings.servers()
        assertEquals(1, saved.size)
        assertEquals("Second", saved.first().name)
        assertEquals("https://b.com/mcp", saved.first().url)
    }

    @Test
    fun `removeServer deletes the matching id`() {
        settings.addServer(McpServerConfig(id = "srv-1", name = "One", url = "https://a.com/mcp"))
        settings.addServer(McpServerConfig(id = "srv-2", name = "Two", url = "https://b.com/mcp"))

        settings.removeServer("srv-1")

        val saved = settings.servers()
        assertEquals(1, saved.size)
        assertEquals("srv-2", saved.first().id)
    }

    @Test
    fun `default values are applied to a new server config`() {
        val config = settings.newServerConfig(url = "https://example.com/mcp")
        assertEquals("Authorization", config.authHeaderName)
        assertEquals("Bearer <api_key>", config.authHeaderValue)
        assertEquals(30, config.timeoutSeconds)
        assertTrue(config.customHeaders.isEmpty())
        assertFalse(config.isTrusted)
        assertNull(config.spkiPin)
    }

    // ---- active server ---------------------------------------------------------

    @Test
    fun `activeServerId is null by default`() {
        assertNull(settings.activeServerId)
    }

    @Test
    fun `setActiveServerId stores and returns the id`() {
        settings.activeServerId = "srv-42"
        assertEquals("srv-42", settings.activeServerId)
    }

    @Test
    fun `activeServerId persists across Settings instances (same store)`() {
        settings.activeServerId = "srv-99"
        val settings2 = Settings(store, serializer)
        assertEquals("srv-99", settings2.activeServerId)
    }

    // ---- active server clearing on delete -------------------------------------

    @Test
    fun `removeServer clears activeServerId when removing the active server`() {
        settings.addServer(McpServerConfig(id = "srv-1", name = "One", url = "https://a.com/mcp"))
        settings.addServer(McpServerConfig(id = "srv-2", name = "Two", url = "https://b.com/mcp"))
        settings.activeServerId = "srv-1"

        settings.removeServer("srv-1")

        assertNull(settings.activeServerId)
    }

    @Test
    fun `removeServer does not clear activeServerId when removing a different server`() {
        settings.addServer(McpServerConfig(id = "srv-1", name = "One", url = "https://a.com/mcp"))
        settings.addServer(McpServerConfig(id = "srv-2", name = "Two", url = "https://b.com/mcp"))
        settings.activeServerId = "srv-1"

        settings.removeServer("srv-2")

        assertEquals("srv-1", settings.activeServerId)
    }

    @Test
    fun `removeServer does not clear activeServerId when the active server was never set`() {
        settings.addServer(McpServerConfig(id = "srv-1", name = "One", url = "https://a.com/mcp"))
        assertNull(settings.activeServerId)

        settings.removeServer("srv-1")

        assertNull(settings.activeServerId)
    }
}

/** In-memory fake of [EncryptedPreferences] for unit tests. */
class FakeEncryptedStore : EncryptedStore {
    private val data = mutableMapOf<String, String>()

    override fun getString(key: String, default: String?): String? = data[key] ?: default
    override fun putString(key: String, value: String) { data[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key, null)?.let { it == "1" } ?: default
    override fun putBoolean(key: String, value: Boolean) { putString(key, if (value) "1" else "0") }
    override fun getInt(key: String, default: Int): Int =
        getString(key, null)?.let { it.toIntOrNull() } ?: default
    override fun putInt(key: String, value: Int) { putString(key, value.toString()) }
}