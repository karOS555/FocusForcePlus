package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_apps",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class BlockedAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    /** null = komplett blockiert, Wert = tägliches Limit in Minuten */
    val dailyLimitMinutes: Int? = null,
    val usedTodayMinutes: Int = 0,
    val isBlocked: Boolean = true,
    val blockDuringRoutines: Boolean = true,
    val blockDuringFocus: Boolean = true
)
