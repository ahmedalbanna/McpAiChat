package com.mcpai.chat.util

import com.mcpai.chat.mcp.SpkiPinMismatchException
import com.mcpai.chat.mcp.McpTransportException

/**
 * Typed error categories for chat/MCP failures.
 *
 * Each variant carries the information the UI needs to show a specific
 * error dialog with the appropriate action (re-trust, retry, etc.).
 */
sealed class ChatError {
    /** The server's certificate changed (SPKI pin mismatch). */
    data class SpkiMismatch(
        val serverName: String = "",
        val stored: String,
        val current: String,
    ) : ChatError()

    /** The server is unreachable or returned a transport-level error. */
    data class ServerUnavailable(
        val serverName: String = "",
        val message: String,
    ) : ChatError()

    /** A generic, unclassified error. */
    data class Unknown(
        val message: String,
    ) : ChatError()
}

/**
 * Classifies a [Throwable] into a [ChatError] variant.
 *
 * [SpkiPinMismatchException] is checked first (it extends [McpTransportException]),
 * so it is never misclassified as [ChatError.ServerUnavailable].
 */
object ErrorClassifier {
    fun classify(throwable: Throwable, serverName: String = ""): ChatError {
        return when (throwable) {
            is SpkiPinMismatchException -> ChatError.SpkiMismatch(
                serverName = serverName,
                stored = throwable.stored,
                current = throwable.current,
            )
            is McpTransportException -> ChatError.ServerUnavailable(
                serverName = serverName,
                message = throwable.message ?: "Unknown transport error",
            )
            else -> ChatError.Unknown(
                message = throwable.message ?: "Unknown error",
            )
        }
    }
}