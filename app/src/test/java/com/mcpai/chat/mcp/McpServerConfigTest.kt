package com.mcpai.chat.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [McpServerConfig.transport] — headers, timeouts, and SPKI pin wiring.
 * Pure JVM test, no Android needed.
 */
class McpServerConfigTest {

    @Test
    fun `transport uses default headers when no auth or custom headers`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
        )
        val transport = config.transport as HttpMcpTransport
        assertTrue(transport.headers.isEmpty())
        assertEquals(30_000, transport.connectTimeoutMs) // McpServerConfig default: 30s
        assertEquals(30_000, transport.readTimeoutMs) // McpServerConfig default: 30s
        assertTrue(transport.spkiPin == null)
    }

    @Test
    fun `transport includes auth header when name and value set`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
            authHeaderName = "X-Api-Key", authHeaderValue = "secret-123",
        )
        val transport = config.transport as HttpMcpTransport
        assertEquals("secret-123", transport.headers["X-Api-Key"])
    }

    @Test
    fun `transport includes custom headers alongside auth header`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
            authHeaderName = "Authorization", authHeaderValue = "Bearer tok",
            customHeaders = mapOf("X-Custom" to "val", "X-Version" to "2"),
        )
        val transport = config.transport as HttpMcpTransport
        assertEquals("Bearer tok", transport.headers["Authorization"])
        assertEquals("val", transport.headers["X-Custom"])
        assertEquals("2", transport.headers["X-Version"])
    }

    @Test
    fun `transport uses configured timeout seconds converted to ms`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
            timeoutSeconds = 60,
        )
        val transport = config.transport as HttpMcpTransport
        assertEquals(60_000, transport.connectTimeoutMs)
        assertEquals(60_000, transport.readTimeoutMs)
    }

    @Test
    fun `transport carries spkiPin when isTrusted with pin`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
            isTrusted = true, spkiPin = "sha256:abcd1234",
        )
        val transport = config.transport as HttpMcpTransport
        assertEquals("sha256:abcd1234", transport.spkiPin)
    }

    @Test
    fun `authHeader convenience property builds name and value`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
            authHeaderName = "Authorization", authHeaderValue = "Bearer tok",
        )
        assertEquals("Authorization: Bearer tok", config.authHeader)
    }

    @Test
    fun `authHeader is null when no name set`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
        )
        val header = config.authHeader
        assertTrue(header == null || header.isBlank())
    }

    @Test
    fun `authHeader is null when value empty even with name set`() {
        val config = McpServerConfig(
            id = "srv-1", name = "Test", url = "https://example.com/mcp",
            authHeaderName = "Authorization", authHeaderValue = "",
        )
        assertNull(config.authHeader)
    }
}