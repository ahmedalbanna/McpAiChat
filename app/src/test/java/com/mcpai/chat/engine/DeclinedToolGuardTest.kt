package com.mcpai.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclinedToolGuardTest {

    @Test
    fun `an undeclined tool is not skipped`() {
        val guard = DeclinedToolGuard()
        assertFalse(guard.shouldSkip("firecrawl_search"))
        assertEquals(0, guard.size())
    }

    @Test
    fun `a declined tool is skipped for the rest of the turn`() {
        val guard = DeclinedToolGuard()
        guard.recordDecline("firecrawl_search")
        assertTrue(guard.shouldSkip("firecrawl_search"))
        assertEquals(1, guard.size())
    }

    @Test
    fun `other tools with the same prefix are not affected`() {
        val guard = DeclinedToolGuard()
        guard.recordDecline("firecrawl_search")
        assertFalse(guard.shouldSkip("firecrawl_scrape"))
        assertFalse(guard.shouldSkip("firecrawl"))
    }

    @Test
    fun `recordDecline is idempotent`() {
        val guard = DeclinedToolGuard()
        guard.recordDecline("ask_wiki_question")
        guard.recordDecline("ask_wiki_question")
        assertEquals(1, guard.size())
    }

    @Test
    fun `a fresh guard instance starts empty`() {
        assertFalse(DeclinedToolGuard().shouldSkip("anything"))
    }
}
