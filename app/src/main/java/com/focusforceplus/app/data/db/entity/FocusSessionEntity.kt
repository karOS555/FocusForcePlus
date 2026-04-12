package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * type: "STUDY" | "WORK" | "CREATIVE" | "CUSTOM"
 * scheduledDays: optional, komma-separiert wie bei Routine, z.B. "MO,MI,FR"
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val durationMinutes: Int,
    val enableDnd: Boolean = true,
    val blockNotifications: Boolean = true,
    val appBlockerEnabled: Boolean = true,
    val scheduledDays: String? = null,
    val scheduledTimeHour: Int? = null,
    val scheduledTimeMinute: Int? = null,
    val isActive: Boolean = true,
    val createdAt: Long
)
