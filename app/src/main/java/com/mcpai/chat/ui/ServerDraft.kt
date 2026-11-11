package com.mcpai.chat.ui

import com.mcpai.chat.mcp.HttpMcpTransport
import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.mcp.SpkiPinPolicy
import java.util.UUID

/** Form state for the server editor. */
data class ServerEditForm(
    val name: String = "",
    val url: String = "",
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val timeout: String = "30",
    val trusted: Boolean = false,
    val isExisting: Boolean = false,
)

/**
 * Builds the [McpServerConfig] the form currently describes.
 *
 * Fields the form does not show — the SPKI pin, custom headers, the enabled
 * flag — are carried over from the stored configuration, so editing a server
 * never silently drops them. The pin is kept only while the server stays
 * trusted and points at the same host.
 */
object ServerDraft {

    fun build(
        existing: McpServerConfig?,
        form: ServerEditForm,
    ): McpServerConfig {
        val url = HttpMcpTransport.normaliseUrl(form.url.trim())
        return McpServerConfig(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = form.name.trim().ifEmpty { "MCP server" },
            url = url,
            enabled = existing?.enabled ?: true,
            authHeaderName = form.authHeaderName.trim().takeIf { it.isNotEmpty() },
            authHeaderValue = form.authHeaderValue.trim().takeIf { it.isNotEmpty() },
            timeoutSeconds = form.timeout.trim().toIntOrNull() ?: 30,
            customHeaders = existing?.customHeaders ?: emptyMap(),
            isTrusted = form.trusted,
            spkiPin = SpkiPinPolicy.pinAfterEdit(existing, url, form.trusted),
        )
    }
}
