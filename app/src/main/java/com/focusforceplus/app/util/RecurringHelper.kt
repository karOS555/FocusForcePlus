package com.focusforceplus.app.util

import java.util.Calendar

/**
 * Returns the next future trigger time for a recurring todo.
 * Advances from [dueDateTime] by the given [pattern] until the result is in the future.
 */
fun nextOccurrenceMillis(dueDateTime: Long, pattern: String): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dueDateTime
    val now = System.currentTimeMillis()

    when {
        pattern == "DAILY" -> {
            do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (cal.timeInMillis <= now)
        }
        pattern == "MONTHLY" -> {
            do { cal.add(Calendar.MONTH, 1) } while (cal.timeInMillis <= now)
        }
        pattern.startsWith("WEEKLY_") -> {
            val targetDows = pattern.removePrefix("WEEKLY_")
                .split(",")
                .mapNotNull { day ->
                    when (day.trim()) {
                        "MO" -> Calendar.MONDAY
                        "TU" -> Calendar.TUESDAY
                        "WE" -> Calendar.WEDNESDAY
                        "TH" -> Calendar.THURSDAY
                        "FR" -> Calendar.FRIDAY
                        "SA" -> Calendar.SATURDAY
                        "SU" -> Calendar.SUNDAY
                        else -> null
                    }
                }
                .toSet()
            if (targetDows.isEmpty()) {
                do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (cal.timeInMillis <= now)
            } else {
                do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (
                    cal.get(Calendar.DAY_OF_WEEK) !in targetDows || cal.timeInMillis <= now
                )
            }
        }
        else -> do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (cal.timeInMillis <= now)
    }
    return cal.timeInMillis
}
