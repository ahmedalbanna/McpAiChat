package com.mcpai.chat.mcp

import org.json.JSONObject

/**
 * Transport abstraction for the Model Context Protocol.
 *
 * Two shapes are supported:
 *  - request/response  (a single JSON-RPC reply per call)
 *  - streaming         (a reply delivered as SSE `data:` frames)
 *
 * Implementations are responsible only for moving one JSON-RPC *message*;
 * the framing (JSON-RPC envelope, ids, notifications) lives in [McpClient].
 */
interface McpTransport {

    /** Human readable name, shown in the server list. */
    val displayName: String

    /**
     * Sends one JSON-RPC message and returns the server's reply.
     *
     * For a transport that replies with SSE frames the implementation must join the
     * frames and return the single JSON-RPC response object it contains.
     */
    suspend fun send(message: JSONObject): JSONObject

    /** Releases any underlying resources (sockets, processes, sessions). */
    fun close()
}
