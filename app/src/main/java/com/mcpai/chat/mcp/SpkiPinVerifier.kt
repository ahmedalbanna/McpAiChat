package com.mcpai.chat.mcp

/**
 * Result of comparing a stored SPKI pin against the pin observed from a server's
 * certificate during a TLS handshake.
 */
sealed class SpkiPinResult {
    /** First time seeing this domain — no stored pin to compare against. */
    object FirstTime : SpkiPinResult()
    /** Stored pin matches the observed pin. */
    object Match : SpkiPinResult()
    /** Pins differ — connection should be rejected. */
    data class Mismatch(val stored: String, val current: String) : SpkiPinResult()
}

/**
 * Compares a stored SPKI pin against the pin observed from a server certificate.
 *
 * Comparison is case-insensitive and trims surrounding whitespace, since SPKI
 * hashes are typically base64-encoded SHA-256 values and encoding variants
 * shouldn't cause spurious mismatches.
 *
 * Contains no Android framework classes — fully testable on the JVM.
 */
object SpkiPinVerifier {

    /**
     * Compares [storedPin] (may be null for first-time trust) against [currentPin]
     * observed from the server's certificate during TLS.
     */
    fun verify(storedPin: String?, currentPin: String): SpkiPinResult {
        val stored = storedPin?.trim()
        if (stored == null || stored.isBlank()) return SpkiPinResult.FirstTime
        val current = currentPin.trim()
        return if (stored.equals(current, ignoreCase = true)) SpkiPinResult.Match else SpkiPinResult.Mismatch(stored, current)
    }
}