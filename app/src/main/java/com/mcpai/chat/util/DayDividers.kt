package com.mcpai.chat.util

import java.time.Instant
import java.time.ZoneId

/**
 * Day-boundary math for the message list's date separators.
 *
 * A divider is drawn before the first message of each calendar day. The
 * decision is kept pure so it stays JVM-testable; the UI only renders it.
 */
internal object DayDividers {

    /** The local calendar day of [createdAt] in [zone]. */
    fun epochDayOf(createdAt: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()

    /** True when [currentDay] differs from [previousDay] (null on the first row). */
    fun needsDivider(previousDay: Long?, currentDay: Long): Boolean =
        previousDay != currentDay

    /**
     * The divider label for [dayEpoch]: today/yesterday for the last two
     * days, otherwise a `MMM d, yyyy` date. Callers supply the localized
     * today/yesterday strings; anything older is formatted with [formatter].
     */
    fun formatDayLabel(
        dayEpoch: Long,
        todayEpoch: Long,
        todayLabel: String,
        yesterdayLabel: String,
        formatter: (Long) -> String,
    ): String = when (dayEpoch) {
        todayEpoch -> todayLabel
        todayEpoch - 1 -> yesterdayLabel
        else -> formatter(dayEpoch)
    }
}
