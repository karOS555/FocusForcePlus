package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long,
    val name: String,
    val description: String? = null,
    val durationMinutes: Int,
    val sortOrder: Int,
    /** Erinnerung X Minuten vor Ende des Tasks */
    val reminderBeforeEndMinutes: Int = 3,
    /** Wie oft bei Überschreitung erinnert wird (in Minuten) */
    val overtimeReminderIntervalMinutes: Int = 5
)
