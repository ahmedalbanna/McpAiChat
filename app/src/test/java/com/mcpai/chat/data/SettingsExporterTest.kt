package com.mcpai.chat.data

import com.mcpai.chat.mcp.McpServerConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SettingsExporter] — JSON export/import of server settings.
 * Pure JVM test, no Android needed.
 */
class SettingsExporterTest {

    private val exporter = SettingsExporter()

    private val server1 = McpServerConfig(
        id = "srv-1",
        name = "Server One",
        url = "https://example.com/mcp",
        enabled = true,
        authHeaderName = "Authorization",
        authHeaderValue = "Bearer secret-123",
        timeoutSeconds = 60,
        customHeaders = mapOf("X-Custom" to "value"),
        isTrusted = true,
        spkiPin = "sha256:abcd1234",
    )

    private val server2 = McpServerConfig(
        id = "srv-2",
        name = "Server Two",
        url = "https://other.com/mcp",
        enabled = false,
        authHeaderName = null,
        authHeaderValue = null,
        timeoutSeconds = 30,
        customHeaders = emptyMap(),
        isTrusted = false,
        spkiPin = null,
    )

    // ---- export without sensitive values ------------------------------------

    @Test
    fun `export excludes auth header value by default`() {
        val json = exporter.export(listOf(server1), includeSensitive = false)
        val obj = JSONObject(json)
        val servers = obj.getJSONArray("servers")
        val s = servers.getJSONObject(0)
        assertFalse(s.has("auth_header_value"))
        assertEquals("Authorization", s.getString("auth_header_name"))
    }

    @Test
    fun `export excludes spki pin by default`() {
        val json = exporter.export(listOf(server1), includeSensitive = false)
        val obj = JSONObject(json)
        val servers = obj.getJSONArray("servers")
        val s = servers.getJSONObject(0)
        assertFalse(s.has("spki_pin"))
    }

    @Test
    fun `export includes non-sensitive fields`() {
        val json = exporter.export(listOf(server1), includeSensitive = false)
        val obj = JSONObject(json)
        val servers = obj.getJSONArray("servers")
        val s = servers.getJSONObject(0)
        assertEquals("srv-1", s.getString("id"))
        assertEquals("Server One", s.getString("name"))
        assertEquals("https://example.com/mcp", s.getString("url"))
        assertTrue(s.getBoolean("enabled"))
        assertEquals(60, s.getInt("timeout_seconds"))
        assertTrue(s.getBoolean("is_trusted"))
        val headers = s.getJSONObject("custom_headers")
        assertEquals("value", headers.getString("X-Custom"))
    }

    // ---- export with sensitive values ---------------------------------------

    @Test
    fun `export includes auth header value when sensitive requested`() {
        val json = exporter.export(listOf(server1), includeSensitive = true)
        val obj = JSONObject(json)
        val servers = obj.getJSONArray("servers")
        val s = servers.getJSONObject(0)
        assertEquals("Bearer secret-123", s.getString("auth_header_value"))
    }

    @Test
    fun `export includes spki pin when sensitive requested`() {
        val json = exporter.export(listOf(server1), includeSensitive = true)
        val obj = JSONObject(json)
        val servers = obj.getJSONArray("servers")
        val s = servers.getJSONObject(0)
        assertEquals("sha256:abcd1234", s.getString("spki_pin"))
    }

    // ---- export multiple servers -------------------------------------------

    @Test
    fun `export includes all servers`() {
        val json = exporter.export(listOf(server1, server2), includeSensitive = false)
        val obj = JSONObject(json)
        val servers = obj.getJSONArray("servers")
        assertEquals(2, servers.length())
        assertEquals("srv-1", servers.getJSONObject(0).getString("id"))
        assertEquals("srv-2", servers.getJSONObject(1).getString("id"))
    }

    @Test
    fun `export includes version field`() {
        val json = exporter.export(listOf(server1), includeSensitive = false)
        val obj = JSONObject(json)
        assertEquals(1, obj.getInt("version"))
    }

    // ---- import -----------------------------------------------------------

    @Test
    fun `import round-trips non-sensitive fields`() {
        val json = exporter.export(listOf(server1, server2), includeSensitive = false)
        val imported = exporter.import(json)
        assertEquals(2, imported.size)
        assertEquals("srv-1", imported[0].id)
        assertEquals("Server One", imported[0].name)
        assertEquals("https://example.com/mcp", imported[0].url)
        assertTrue(imported[0].enabled)
        assertEquals(60, imported[0].timeoutSeconds)
        assertTrue(imported[0].isTrusted)
        assertEquals(mapOf("X-Custom" to "value"), imported[0].customHeaders)
    }

    @Test
    fun `import sets auth header value to null when not present`() {
        val json = exporter.export(listOf(server1), includeSensitive = false)
        val imported = exporter.import(json)
        assertEquals("Authorization", imported[0].authHeaderName)
        assertNull(imported[0].authHeaderValue)
    }

    @Test
    fun `import preserves auth header value when present`() {
        val json = exporter.export(listOf(server1), includeSensitive = true)
        val imported = exporter.import(json)
        assertEquals("Bearer secret-123", imported[0].authHeaderValue)
    }

    @Test
    fun `import preserves spki pin when present`() {
        val json = exporter.export(listOf(server1), includeSensitive = true)
        val imported = exporter.import(json)
        assertEquals("sha256:abcd1234", imported[0].spkiPin)
    }

    @Test
    fun `import sets spki pin to null when not present`() {
        val json = exporter.export(listOf(server1), includeSensitive = false)
        val imported = exporter.import(json)
        assertNull(imported[0].spkiPin)
    }

    @Test
    fun `import handles empty server list`() {
        val json = exporter.export(emptyList(), includeSensitive = false)
        val imported = exporter.import(json)
        assertTrue(imported.isEmpty())
    }

    @Test
    fun `import handles malformed JSON gracefully`() {
        val imported = exporter.import("{not valid json")
        assertTrue(imported.isEmpty())
    }

    @Test
    fun `import handles missing servers array`() {
        val imported = exporter.import("""{"version":1}""")
        assertTrue(imported.isEmpty())
    }

    // ---- tool approvals export/import --------------------------------------

    @Test
    fun `exportToolApprovals produces JSON with approvals`() {
        val approvals = listOf(
            ToolApproval(serverId = "srv-1", toolName = "add", isAutoApproved = true),
            ToolApproval(serverId = "srv-1", toolName = "echo", isAutoApproved = false),
        )
        val json = exporter.exportToolApprovals(approvals)
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("tool_approvals")
        assertEquals(2, arr.length())
        assertEquals("srv-1", arr.getJSONObject(0).getString("server_id"))
        assertEquals("add", arr.getJSONObject(0).getString("tool_name"))
        assertTrue(arr.getJSONObject(0).getBoolean("is_auto_approved"))
    }

    @Test
    fun `importToolApprovals round-trips`() {
        val approvals = listOf(
            ToolApproval(serverId = "srv-1", toolName = "add", isAutoApproved = true),
        )
        val json = exporter.exportToolApprovals(approvals)
        val imported = exporter.importToolApprovals(json)
        assertEquals(1, imported.size)
        assertEquals("srv-1", imported[0].serverId)
        assertEquals("add", imported[0].toolName)
        assertTrue(imported[0].isAutoApproved)
    }

    @Test
    fun `importToolApprovals handles malformed JSON`() {
        val imported = exporter.importToolApprovals("{bad")
        assertTrue(imported.isEmpty())
    }
}