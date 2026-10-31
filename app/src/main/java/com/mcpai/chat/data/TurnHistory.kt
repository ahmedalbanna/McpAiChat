package com.mcpai.chat.data

/**
 * Trims a stored conversation down to the messages that are replayed to the
 * model.
 *
 * Replaying the whole conversation on every turn grows in cost and latency as
 * the chat gets long, so the history is capped. The cap has to respect one rule
 * the OpenAI wire format does not tolerate getting wrong: a `tool` message must
 * be preceded by the assistant turn that carries the matching `tool_calls`.
 * Dropping messages from the front therefore stops at a non-`tool` message.
 */
internal object TurnHistory {

    /**
     * Returns the tail of [messages] that fits in [limit], with any leading
     * `tool` messages removed so the result never starts mid-tool-call.
     */
    fun replay(messages: List<StoredMessage>, limit: Int): List<StoredMessage> {
        if (limit <= 0) return emptyList()
        if (messages.size <= limit) return messages
        return messages.takeLast(limit).dropWhile { it.role == StoredMessage.ROLE_TOOL }
    }
}
