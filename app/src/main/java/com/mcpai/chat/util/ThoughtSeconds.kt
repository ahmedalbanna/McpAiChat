package com.mcpai.chat.util

/**
 * Elapsed seconds for a Thought/Thinking header, rounded to whole seconds.
 * A reasoning phase that produced any tokens always shows at least 1s,
 * matching the reference apps ("Thinking 4s", "Thought 11s").
 */
object ThoughtSeconds {

    fun of(ms: Long): Long = maxOf(1L, ms / 1000L)
}
