package com.mcpai.chat.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MCP server URL normalisation and validation.
 * Pure JVM tests — no Android or network needed.
 */
class ServerUrlValidatorTest {

    // ---- normaliseUrl -------------------------------------------------------

    @Test
    fun `missing scheme defaults to https`() {
        assertEquals("https://mcp.example.com/mcp", HttpMcpTransport.normaliseUrl("mcp.example.com/mcp"))
    }

    @Test
    fun `explicit https is preserved`() {
        assertEquals(
            "https://mcp.example.com/mcp",
            HttpMcpTransport.normaliseUrl("https://mcp.example.com/mcp"),
        )
    }

    @Test
    fun `explicit http is preserved`() {
        assertEquals(
            "http://mcp.example.com/mcp",
            HttpMcpTransport.normaliseUrl("http://mcp.example.com/mcp"),
        )
    }

    @Test
    fun `loopback defaults to http for local development`() {
        assertEquals("http://127.0.0.1:8099/mcp", HttpMcpTransport.normaliseUrl("127.0.0.1:8099/mcp"))
        assertEquals("http://localhost:8099/mcp", HttpMcpTransport.normaliseUrl("localhost:8099/mcp"))
    }

    @Test
    fun `trailing slash is removed`() {
        assertEquals("https://mcp.example.com/mcp", HttpMcpTransport.normaliseUrl("https://mcp.example.com/mcp/"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("https://mcp.example.com/mcp", HttpMcpTransport.normaliseUrl("  https://mcp.example.com/mcp  "))
    }

    // ---- isLoopback ---------------------------------------------------------

    @Test
    fun `loopback hosts are recognised`() {
        assertTrue(HttpMcpTransport.isLoopback("127.0.0.1:8099"))
        assertTrue(HttpMcpTransport.isLoopback("http://localhost/mcp"))
        assertTrue(HttpMcpTransport.isLoopback("10.0.2.2"))
    }

    @Test
    fun `remote hosts are not loopback`() {
        assertFalse(HttpMcpTransport.isLoopback("mcp.example.com"))
        assertFalse(HttpMcpTransport.isLoopback("https://192.168.1.10/mcp"))
    }

    // ---- validation ---------------------------------------------------------

    @Test
    fun `https url is accepted`() {
        assertNull(ServerUrlValidator.validate("https://mcp.example.com/mcp"))
    }

    @Test
    fun `url without scheme is accepted and treated as https`() {
        assertNull(ServerUrlValidator.validate("mcp.example.com/mcp"))
    }

    @Test
    fun `http url for a remote host is rejected`() {
        assertEquals(
            ServerUrlProblem.HTTPS_REQUIRED,
            ServerUrlValidator.validate("http://mcp.example.com/mcp"),
        )
    }

    @Test
    fun `http url for loopback is accepted`() {
        assertNull(ServerUrlValidator.validate("http://127.0.0.1:8099/mcp"))
        assertNull(ServerUrlValidator.validate("localhost:8099/mcp"))
    }

    @Test
    fun `non http scheme is rejected`() {
        assertEquals(
            ServerUrlProblem.NOT_HTTP,
            ServerUrlValidator.validate("ftp://mcp.example.com/mcp"),
        )
    }

    @Test
    fun `url without host is rejected`() {
        assertEquals(ServerUrlProblem.MISSING_HOST, ServerUrlValidator.validate("https://"))
    }
}
