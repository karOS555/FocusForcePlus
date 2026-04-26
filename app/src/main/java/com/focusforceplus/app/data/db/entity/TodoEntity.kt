package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * priority: 0 = niedrig, 1 = mittel, 2 = hoch
 * recurringPattern: z.B. "DAILY", "WEEKLY_MO,MI,FR", "MONTHLY"
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    /** null = unterminiert */
    val dueDateTime: Long? = null,
    val priority: Int = 1,
    val isCompleted: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringPattern: String? = null,
    val snoozeCount: Int = 0,
    val maxSnoozeCount: Int = 2,
    val rescheduleCount: Int = 0,
    val maxRescheduleCount: Int = 1,
    val createdAt: Long,
    val completedAt: Long? = null,
    val postponedTo: Long? = null,
    val checklistJson: String? = null,
)
