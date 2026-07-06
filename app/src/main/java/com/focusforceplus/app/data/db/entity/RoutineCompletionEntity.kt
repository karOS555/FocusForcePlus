package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed routine run. The routine name is snapshotted so history (and any
 * future streak/stats features) survives the routine being edited or deleted.
 */
@Entity(
    tableName = "routine_completions",
    indices = [Index(value = ["completedAt"])]
)
data class RoutineCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long,
    val routineName: String,
    val completedAt: Long,
    val scheduledMinutes: Int,
    val overtimeMinutes: Int,
    val tasksCompleted: Int,
)
