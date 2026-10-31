package com.mcpai.chat.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [SpkiPinPolicy] — which pin survives an edit of a server configuration.
 */
class SpkiPinPolicyTest {

    private fun server(
        url: String = "https://example.com/mcp",
        isTrusted: Boolean = true,
        spkiPin: String? = "sha256:abcd1234",
    ) = McpServerConfig(
        id = "id-1",
        name = "Example",
        url = url,
        isTrusted = isTrusted,
        spkiPin = spkiPin,
    )

    @Test
    fun `pin survives an edit that keeps the same host`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(), "https://example.com/other", true)
        assertEquals("sha256:abcd1234", pin)
    }

    @Test
    fun `pin survives an edit that only changes the port`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(), "https://example.com:8443/mcp", true)
        assertEquals("sha256:abcd1234", pin)
    }

    @Test
    fun `pin is dropped when the host changes`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(), "https://other.example.com/mcp", true)
        assertNull(pin)
    }

    @Test
    fun `pin is dropped when the host changes only by case`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(url = "https://example.com/mcp"), "https://EXAMPLE.com/mcp", true)
        assertEquals("sha256:abcd1234", pin)
    }

    @Test
    fun `pin is dropped when the server is no longer trusted`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(), "https://example.com/mcp", false)
        assertNull(pin)
    }

    @Test
    fun `pin is null when there was no stored pin`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(spkiPin = null), "https://example.com/mcp", true)
        assertNull(pin)
    }

    @Test
    fun `pin is null for a new server`() {
        val pin = SpkiPinPolicy.pinAfterEdit(null, "https://example.com/mcp", true)
        assertNull(pin)
    }

    @Test
    fun `a trusted server without a stored pin stays without one`() {
        val pin = SpkiPinPolicy.pinAfterEdit(server(spkiPin = null), "https://example.com/mcp", true)
        assertNull(pin)
    }

    @Test
    fun `host ignores path port and case`() {
        assertEquals("example.com", SpkiPinPolicy.hostOf("https://Example.com:8443/mcp/path"))
        assertEquals("127.0.0.1", SpkiPinPolicy.hostOf("http://127.0.0.1:3000/mcp"))
    }
}
