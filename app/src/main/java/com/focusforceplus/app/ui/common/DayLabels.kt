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
