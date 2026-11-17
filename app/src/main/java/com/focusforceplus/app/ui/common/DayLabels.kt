package com.focusforceplus.app.ui.common

/**
 * Maps the routine module's persisted day keys (German abbreviations, see
 * `AlarmHelper.DAY_MAP`) to the English labels shown in the UI. The storage format
 * predates the English-only UI rule and is kept to avoid a data migration.
 */
fun routineDayLabel(key: String): String = when (key) {
    "MO" -> "MO"
    "DI" -> "TU"
    "MI" -> "WE"
    "DO" -> "TH"
    "FR" -> "FR"
    "SA" -> "SA"
    "SO" -> "SU"
    else -> key
}

// Routines store German day keys, focus sessions English ones; both map to 0..6.
private val DAY_INDEX = mapOf(
    "MO" to 0, "DI" to 1, "TU" to 1, "MI" to 2, "WE" to 2, "DO" to 3, "TH" to 3,
    "FR" to 4, "SA" to 5, "SO" to 6, "SU" to 6,
)
private val SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Every day", "Mon to Fri", "Weekends", or "Mon, Wed, Fri", for either key set. */
fun routineDaysSummary(activeDaysCsv: String): String {
    val days = activeDaysCsv.split(",").mapNotNull { DAY_INDEX[it.trim()] }.toSet()
    return when {
        days.size == 7 -> "Every day"
        days == setOf(0, 1, 2, 3, 4) -> "Mon to Fri"
        days == setOf(5, 6) -> "Weekends"
        days.isEmpty() -> "No days"
        else -> days.sorted().joinToString(", ") { SHORT[it] }
    }
}

/** The stored key of today, matching `RoutineEntity.activeDays`. */
fun routineTodayKey(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
    java.util.Calendar.MONDAY -> "MO"
    java.util.Calendar.TUESDAY -> "DI"
    java.util.Calendar.WEDNESDAY -> "MI"
    java.util.Calendar.THURSDAY -> "DO"
    java.util.Calendar.FRIDAY -> "FR"
    java.util.Calendar.SATURDAY -> "SA"
    else -> "SO"
}
