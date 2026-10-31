package com.mcpai.chat.mcp

/**
 * Decides which SPKI pin survives an edit of a server configuration.
 *
 * A pin belongs to a certificate, and a certificate belongs to a host. Editing
 * a server must not silently drop its pin — that would turn a pinned server
 * into an unpinned one without the user asking for it — but pointing the
 * server at another host must not carry the old pin along.
 */
object SpkiPinPolicy {

    /**
     * The pin to store after an edit.
     *
     * @param existing the configuration as it was stored, or null for a new server
     * @param editedUrl the URL after the edit
     * @param isTrusted whether the server is trusted after the edit
     * @return the pin to keep, or null when it must be captured again
     */
    fun pinAfterEdit(existing: McpServerConfig?, editedUrl: String, isTrusted: Boolean): String? {
        if (!isTrusted) return null
        val pin = existing?.spkiPin ?: return null
        if (hostOf(existing.url) != hostOf(editedUrl)) return null
        return pin
    }

    /** The host part of [url], lower-cased, without port or path. */
    fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
}
