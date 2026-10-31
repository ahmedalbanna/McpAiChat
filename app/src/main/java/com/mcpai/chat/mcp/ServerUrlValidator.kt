package com.mcpai.chat.mcp

/** Why a user-supplied MCP server URL was rejected. */
enum class ServerUrlProblem {
    /** The URL is not an `http://` or `https://` URL. */
    NOT_HTTP,

    /** The URL has no host part. */
    MISSING_HOST,

    /** Plain HTTP was used for a host that is not loopback. */
    HTTPS_REQUIRED,
}

/**
 * Validates MCP server URLs before they are stored.
 *
 * The app speaks HTTPS to every remote server; cleartext HTTP is only
 * tolerated for loopback addresses, where local development servers live.
 */
object ServerUrlValidator {

    /** Returns the problem with [rawUrl], or null when the URL is acceptable. */
    fun validate(rawUrl: String): ServerUrlProblem? {
        val normalised = HttpMcpTransport.normaliseUrl(rawUrl)
        val scheme = normalised.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return ServerUrlProblem.NOT_HTTP

        val host = normalised.substringAfter("://").substringBefore('/').substringBefore(':')
        if (host.isBlank()) return ServerUrlProblem.MISSING_HOST

        if (scheme == "http" && !HttpMcpTransport.isLoopback(normalised)) {
            return ServerUrlProblem.HTTPS_REQUIRED
        }
        return null
    }
}
