package com.mcpai.chat.ui

import com.mcpai.chat.mcp.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDraftTest {

    private val existing = McpServerConfig(
        id = "edit-screen-test",
        name = "Original name",
        url = "https://example.com/mcp",
        authHeaderName = "X-Token",
        authHeaderValue = "secret-token",
        timeoutSeconds = 45,
        customHeaders = mapOf("X-Trace" to "on"),
        isTrusted = true,
        spkiPin = "sha256:stored-pin",
    )

    @Test
    fun `saving an edit keeps the pin and the fields the form does not show`() {
        val draft = ServerEditForm(
            name = "Renamed",
            url = "https://example.com/mcp",
            authHeaderName = "X-Token",
            authHeaderValue = "secret-token",
            timeout = "45",
            trusted = true,
            isExisting = true,
        )

        val saved = ServerDraft.build(existing, draft)
        assertEquals("Renamed", saved.name)
        assertEquals("https://example.com/mcp", saved.url)
        assertEquals("X-Token", saved.authHeaderName)
        assertEquals("secret-token", saved.authHeaderValue)
        assertEquals(45, saved.timeoutSeconds)
        assertEquals(mapOf("X-Trace" to "on"), saved.customHeaders)
        assertEquals("the server must stay trusted", true, saved.isTrusted)
        assertEquals("the stored pin must survive the edit", "sha256:stored-pin", saved.spkiPin)
    }

    @Test
    fun `changing the host drops the pin`() {
        val draft = ServerEditForm(
            url = "https://other.example.com/mcp",
            timeout = "30",
            trusted = true,
            isExisting = true,
        )

        val saved = ServerDraft.build(existing, draft)
        assertEquals("https://other.example.com/mcp", saved.url)
        assertNull(saved.spkiPin)
    }
}
