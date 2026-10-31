package com.mcpai.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TurnHistory], the rule that decides which stored messages are
 * replayed to the model.
 *
 * The invariant being protected is this: the replayed history must never begin
 * with a `tool` message. OpenAI rejects a tool result that is not preceded by
 * the assistant turn carrying the matching `tool_calls`, and when that happens
 * the provider returns an error the user only sees as "no answer".
 */
class TurnHistoryTest {

    private fun message(id: Long, role: String) = StoredMessage(
        id = id,
        conversationId = 1,
        role = role,
        content = "$role$id",
        toolName = if (role == StoredMessage.ROLE_TOOL) "echo" else null,
        toolCallId = if (role == StoredMessage.ROLE_TOOL) "call_$id" else null,
        toolArgs = if (role == StoredMessage.ROLE_TOOL) "{}" else null,
        toolResult = if (role == StoredMessage.ROLE_TOOL) "r$id" else null,
    )

    private fun user(id: Long) = message(id, StoredMessage.ROLE_USER)
    private fun assistant(id: Long) = message(id, StoredMessage.ROLE_ASSISTANT)
    private fun tool(id: Long) = message(id, StoredMessage.ROLE_TOOL)

    private fun roles(messages: List<StoredMessage>) =
        messages.joinToString { it.role.first() + it.id.toString() }

    /** A cut must never leave a tool message at the front. */
    private fun assertDoesNotStartWithTool(history: List<StoredMessage>) {
        if (history.isNotEmpty()) {
            assertTrue(
                "history starts with '${history.first().role}', not '${StoredMessage.ROLE_TOOL}': ${roles(history)}",
                history.first().role != StoredMessage.ROLE_TOOL,
            )
        }
    }

    @Test
    fun `keeps every message below the limit`() {
        val messages = listOf(user(1), assistant(2), user(3))
        assertEquals(messages, TurnHistory.replay(messages, limit = 50))
    }

    @Test
    fun `keeps an empty history empty`() {
        assertTrue(TurnHistory.replay(emptyList(), limit = 10).isEmpty())
    }

    @Test
    fun `keeps the newest messages when trimming`() {
        val messages = (1..12).map { user(it.toLong()) }
        val replayed = TurnHistory.replay(messages, limit = 6)

        assertEquals(6, replayed.size)
        assertEquals(12L, replayed.last().id)
        assertEquals(7L, replayed.first().id)
    }

    @Test
    fun `truncation keeps the last limit messages when none is a tool result`() {
        // 9 messages, limit 6 -> positions 3..8, which begins on an assistant turn.
        val messages = listOf(
            user(1), assistant(2), tool(3),
            assistant(4), tool(5), assistant(6),
            tool(7), assistant(8), tool(9),
        )
        val replayed = TurnHistory.replay(messages, limit = 6)

        assertEquals(6, replayed.size)
        assertEquals(listOf(4L, 5L, 6L, 7L, 8L, 9L), replayed.map { it.id })
        assertDoesNotStartWithTool(replayed)
    }

    @Test
    fun `a leading tool message is dropped so an assistant turn leads`() {
        // 10 messages, limit 8 -> positions 2..9, which begins on tool(3).
        val messages = listOf(
            user(1), assistant(2), tool(3),
            user(4), assistant(5), tool(6),
            user(7), assistant(8), tool(9), user(10),
        )
        val replayed = TurnHistory.replay(messages, limit = 8)

        assertEquals(7, replayed.size)
        assertEquals(listOf(4L, 5L, 6L, 7L, 8L, 9L, 10L), replayed.map { it.id })
        assertDoesNotStartWithTool(replayed)
    }

    @Test
    fun `every leading tool message is dropped, not just the first`() {
        val messages = listOf(tool(1), tool(2), tool(3), user(4))
        val replayed = TurnHistory.replay(messages, limit = 3)

        assertEquals(1, replayed.size)
        assertEquals(4L, replayed.first().id)
        assertDoesNotStartWithTool(replayed)
    }

    @Test
    fun `a history that is all tool messages replays as empty`() {
        val messages = listOf(tool(1), tool(2), tool(3))
        assertTrue(TurnHistory.replay(messages, limit = 2).isEmpty())
    }

    @Test
    fun `the limit never empties a usable history`() {
        val messages = listOf(user(1), assistant(2), user(3), assistant(4))
        val replayed = TurnHistory.replay(messages, limit = 2)

        assertEquals(2, replayed.size)
        assertDoesNotStartWithTool(replayed)
    }

    @Test
    fun `a limit of zero or less replays nothing`() {
        val messages = listOf(user(1), assistant(2))
        assertTrue(TurnHistory.replay(messages, limit = 0).isEmpty())
        assertTrue(TurnHistory.replay(messages, limit = -5).isEmpty())
    }

    @Test
    fun `trimming is stable when called repeatedly`() {
        val messages = (
            (1..20).map { user(it.toLong()) } +
                listOf(assistant(21), tool(22), assistant(23))
            )
        val once = TurnHistory.replay(messages, limit = 8)
        val twice = TurnHistory.replay(once, limit = 8)

        assertEquals(once, twice)
        assertDoesNotStartWithTool(twice)
    }
}
