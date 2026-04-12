package com.focusforceplus.app.data.db

import androidx.room.TypeConverter

class AppTypeConverters {

    // --- List<String> <-> String (für komma-separierte Felder wie activeDays) ---

    @TypeConverter
    fun fromStringList(list: List<String>?): String? =
        list?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.split(",")?.filter { it.isNotBlank() }

    // --- Set<String> <-> String ---

    @TypeConverter
    fun fromStringSet(set: Set<String>?): String? =
        set?.joinToString(",")

    @TypeConverter
    fun toStringSet(value: String?): Set<String>? =
        value?.split(",")?.filter { it.isNotBlank() }?.toSet()
}
