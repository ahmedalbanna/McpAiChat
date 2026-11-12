package com.mcpai.chat.ui

/**
 * One row in the conversation (Compose-ready).
 *
 * The row kinds mirror the reference apps: a user bubble, an assistant bubble,
 * one compact group row for consecutive tool calls and a temporary
 * waiting-for-the-model marker.
 */
sealed interface ChatRow {

    data class User(
        val id: Long,
        val text: String,
        val attachmentPath: String? = null,
        val attachmentName: String? = null,
    ) : ChatRow

    data class Assistant(
        val id: Long,
        val text: String,
        val streaming: Boolean,
        val reasoning: String = "",
        /** Elapsed reasoning time frozen when the answer phase starts. Live turns only; history rows have none. */
        val reasoningMs: Long? = null,
        /** When the reasoning phase started, for the live ticker. */
        val reasoningStartedAtMs: Long? = null,
    ) : ChatRow

    data class Tool(
        val id: Long,
        val name: String,
        val args: String,
        val result: String?,
        val isError: Boolean,
        val running: Boolean,
        val declined: Boolean = false,
    ) : ChatRow

    /** A temporary row shown while the app is waiting for the next model turn. */
    data class Thinking(val id: Long) : ChatRow

    /**
     * Consecutive tool calls grouped behind one compact marker:
     * `Tools used: N · Calls: M`, collapsed by default. The [id] is the first
     * call's row id and is what the expanded-set keys on.
     */
    data class ToolGroup(
        val id: Long,
        val tools: List<Tool>,
        val expanded: Boolean,
    ) : ChatRow
}
