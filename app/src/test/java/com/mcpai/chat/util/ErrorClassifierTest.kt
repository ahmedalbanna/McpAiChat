package com.mcpai.chat.util

import com.mcpai.chat.mcp.SpkiPinMismatchException
import com.mcpai.chat.mcp.McpTransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ErrorClassifier] — maps throwables to typed error categories.
 * Pure JVM test, no Android needed.
 */
class ErrorClassifierTest {

    @Test
    fun `SpkiPinMismatchException classifies as SPKI_MISMATCH`() {
        val ex = SpkiPinMismatchException(stored = "sha256:old", current = "sha256:new")
        val error = ErrorClassifier.classify(ex)
        assertTrue(error is ChatError.SpkiMismatch)
        val mismatch = error as ChatError.SpkiMismatch
        assertEquals("sha256:old", mismatch.stored)
        assertEquals("sha256:new", mismatch.current)
    }

    @Test
    fun `McpTransportException classifies as SERVER_UNAVAILABLE`() {
        val ex = McpTransportException("Cannot reach server")
        val error = ErrorClassifier.classify(ex)
        assertTrue(error is ChatError.ServerUnavailable)
        assertEquals("Cannot reach server", (error as ChatError.ServerUnavailable).message)
    }

    @Test
    fun `generic Exception classifies as UNKNOWN`() {
        val ex = RuntimeException("something broke")
        val error = ErrorClassifier.classify(ex)
        assertTrue(error is ChatError.Unknown)
        assertEquals("something broke", (error as ChatError.Unknown).message)
    }

    @Test
    fun `SpkiPinMismatchException is not SERVER_UNAVAILABLE`() {
        val ex = SpkiPinMismatchException(stored = "a", current = "b")
        val error = ErrorClassifier.classify(ex)
        assertTrue(error !is ChatError.ServerUnavailable)
    }

    @Test
    fun `McpTransportException subclass SpkiPinMismatchException classifies as SPKI_MISMATCH not SERVER_UNAVAILABLE`() {
        // SpkiPinMismatchException extends McpTransportException, but should
        // be classified as SPKI_MISMATCH, not SERVER_UNAVAILABLE.
        val ex = SpkiPinMismatchException(stored = "a", current = "b")
        val error = ErrorClassifier.classify(ex)
        assertTrue(error is ChatError.SpkiMismatch)
    }

    @Test
    fun `error message includes server name when provided`() {
        val ex = McpTransportException("Cannot reach server")
        val error = ErrorClassifier.classify(ex, serverName = "My Server")
        assertTrue(error is ChatError.ServerUnavailable)
        assertEquals("My Server", (error as ChatError.ServerUnavailable).serverName)
    }

    @Test
    fun `SpkiMismatch error includes server name when provided`() {
        val ex = SpkiPinMismatchException(stored = "a", current = "b")
        val error = ErrorClassifier.classify(ex, serverName = "Trusted Server")
        assertTrue(error is ChatError.SpkiMismatch)
        assertEquals("Trusted Server", (error as ChatError.SpkiMismatch).serverName)
    }
}