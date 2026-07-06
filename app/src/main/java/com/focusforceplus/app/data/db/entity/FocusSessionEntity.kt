package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * type: "STUDY" | "WORK" | "CREATIVE" | "CUSTOM"
 * scheduledDays: optional, comma-separated English day keys, e.g. "MO,TU,FR"
 * (see FocusAlarmHelper.DAY_MAP — unlike routines, focus uses English keys)
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
    /** While a session with this flag runs it cannot be paused or ended early —
     *  it releases at the natural end (timer expiry). Togglable only while IDLE. */
    val invincibleMode: Boolean = false,
    /** Comma-separated blocker group names this session blocks; null = all configured apps. */
    val blockedGroupsCsv: String? = null,
    val scheduledDays: String? = null,
    val scheduledTimeHour: Int? = null,
    val scheduledTimeMinute: Int? = null,
    val isActive: Boolean = true,
    val createdAt: Long
)
