package com.mcpai.chat.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SpkiPinVerifier] — pure pin comparison logic, no Android needed.
 */
class SpkiPinVerifierTest {

    @Test
    fun `no stored pin is first time - always accepts`() {
        val result = SpkiPinVerifier.verify(null, "sha256:abcd1234")
        assertTrue(result is SpkiPinResult.FirstTime)
    }

    @Test
    fun `empty stored pin is first time - always accepts`() {
        val result = SpkiPinVerifier.verify("", "sha256:abcd1234")
        assertTrue(result is SpkiPinResult.FirstTime)
    }

    @Test
    fun `matching pins return Match`() {
        val result = SpkiPinVerifier.verify("sha256:abcd1234", "sha256:abcd1234")
        assertTrue(result is SpkiPinResult.Match)
    }

    @Test
    fun `different pins return Mismatch with both values`() {
        val result = SpkiPinVerifier.verify("sha256:abcd1234", "sha256:wxyz5678")
        assertTrue(result is SpkiPinResult.Mismatch)
        val mismatch = result as SpkiPinResult.Mismatch
        assertEquals("sha256:abcd1234", mismatch.stored)
        assertEquals("sha256:wxyz5678", mismatch.current)
    }

    @Test
    fun `case-insensitive comparison`() {
        val result = SpkiPinVerifier.verify("SHA256:ABCD1234", "sha256:abcd1234")
        assertTrue(result is SpkiPinResult.Match)
    }

    @Test
    fun `whitespace is trimmed before comparison`() {
        val result = SpkiPinVerifier.verify("  sha256:abcd1234  ", "sha256:abcd1234")
        assertTrue(result is SpkiPinResult.Match)
    }
}