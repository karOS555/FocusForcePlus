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

private val WEEK = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")
private val SHORT = mapOf(
    "MO" to "Mon", "DI" to "Tue", "MI" to "Wed", "DO" to "Thu",
    "FR" to "Fri", "SA" to "Sat", "SO" to "Sun",
)

/** "Every day", "Mon to Fri", "Weekends", or "Mon, Wed, Fri". */
fun routineDaysSummary(activeDaysCsv: String): String {
    val days = activeDaysCsv.split(",").filter { it in WEEK }.toSet()
    return when {
        days.size == 7 -> "Every day"
        days == setOf("MO", "DI", "MI", "DO", "FR") -> "Mon to Fri"
        days == setOf("SA", "SO") -> "Weekends"
        days.isEmpty() -> "No days"
        else -> WEEK.filter { it in days }.joinToString(", ") { SHORT.getValue(it) }
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
