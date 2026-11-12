package com.mcpai.chat.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThoughtSecondsTest {

    @Test
    fun subSecondReasoningShowsOneSecond() {
        assertEquals(1L, ThoughtSeconds.of(0L))
        assertEquals(1L, ThoughtSeconds.of(1L))
        assertEquals(1L, ThoughtSeconds.of(999L))
    }

    @Test
    fun wholeSecondsAreFloored() {
        assertEquals(1L, ThoughtSeconds.of(1000L))
        assertEquals(4L, ThoughtSeconds.of(4999L))
        assertEquals(11L, ThoughtSeconds.of(11000L))
        assertEquals(11L, ThoughtSeconds.of(11999L))
        assertEquals(60L, ThoughtSeconds.of(60_000L))
    }

    @Test
    fun largeValuesDoNotOverflow() {
        assertEquals(3600L, ThoughtSeconds.of(3_600_000L))
    }
}
