package com.mcpai.chat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayDividersTest {

    private val utc = ZoneId.of("UTC")
    private val dayNov12 = LocalDate.of(2025, 11, 12).toEpochDay()
    private val dayNov13 = LocalDate.of(2025, 11, 13).toEpochDay()

    @Test
    fun epochDayOfBucketsByLocalDay() {
        assertEquals(
            dayNov12,
            DayDividers.epochDayOf(Instant.parse("2025-11-12T23:59:59Z").toEpochMilli(), utc),
        )
        assertEquals(
            dayNov12 + 1,
            DayDividers.epochDayOf(Instant.parse("2025-11-13T00:00:01Z").toEpochMilli(), utc),
        )
        // A UTC+3 zone buckets 22:30Z on the following local day.
        val kuwait = ZoneId.of("Asia/Kuwait")
        assertEquals(
            dayNov12 + 1,
            DayDividers.epochDayOf(Instant.parse("2025-11-12T22:30:00Z").toEpochMilli(), kuwait),
        )
    }

    @Test
    fun needsDividerTrueOnFirstRowAndOnDayChange() {
        assertTrue(DayDividers.needsDivider(null, dayNov12))
        assertTrue(DayDividers.needsDivider(dayNov12, dayNov13))
    }

    @Test
    fun needsDividerFalseWithinSameDay() {
        assertFalse(DayDividers.needsDivider(dayNov12, dayNov12))
    }

    @Test
    fun formatDayLabelPrefersTodayAndYesterday() {
        assertEquals(
            "Today",
            DayDividers.formatDayLabel(dayNov12, dayNov12, "Today", "Yesterday") { "older:$it" },
        )
        assertEquals(
            "Yesterday",
            DayDividers.formatDayLabel(dayNov12 - 1, dayNov12, "Today", "Yesterday") { "older:$it" },
        )
        assertEquals(
            "older:20375",
            DayDividers.formatDayLabel(20375L, dayNov12, "Today", "Yesterday") { "older:$it" },
        )
    }
}
