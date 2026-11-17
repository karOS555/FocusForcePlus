package com.focusforceplus.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How the app writes dates and times, in one place so every screen says "Today
 * 14:00" the same way.
 */
object TimeFormat {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    private fun local(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    fun clock(millis: Long): String = local(millis).format(clock)

    fun clock(minutesOfDay: Int): String = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

    /** "Today", "Tomorrow", "Yesterday", "Mon" within a week, otherwise "Nov 3". */
    fun day(millis: Long): String {
        val date = local(millis).toLocalDate()
        val today = LocalDate.now()
        return when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            in 2L..6L, in -6L..-2L -> date.format(weekday)
            else -> if (date.year == today.year) date.format(monthDay) else date.format(monthDayYear)
        }
    }

    /** "Today 14:00", "Mon 09:30", "Nov 3 18:00". */
    fun dayAndClock(millis: Long): String = "${day(millis)} ${clock(millis)}"

    /** "45 min", "1 h 20 min", "2 h". */
    fun duration(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    /** Whole days from now until [millis], never negative. */
    fun daysUntil(millis: Long): Int =
        ((millis - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
}
