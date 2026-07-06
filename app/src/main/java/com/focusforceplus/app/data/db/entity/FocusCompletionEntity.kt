package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One finished focus session (natural end or ended early). Name/type are
 * snapshotted so history survives session edits and deletions; quick-start
 * sessions record sessionId 0.
 */
@Entity(
    tableName = "focus_completions",
    indices = [Index(value = ["completedAt"])]
)
data class FocusCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val name: String,
    val type: String,
    /** Minutes actually focused (elapsed time, not the planned duration). */
    val focusedMinutes: Int,
    val completedAt: Long,
    val endedEarly: Boolean,
)
