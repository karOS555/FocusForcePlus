package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * priority: 0 = low, 1 = medium, 2 = high
 * recurringPattern: e.g. "DAILY", "WEEKLY_MO,WE,FR", "MONTHLY"
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    /** null = no due date */
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
    /** Icon from the shared icon catalogue; null = the module's default icon. */
    val iconKey: String? = null,
    /** Set when the todo is in the trash; null = not deleted. */
    val deletedAt: Long? = null,
    /** This todo's own speed bumps (see GuardOverrides); null = the defaults for its priority. */
    val guards: String? = null,
    /** While in the future, the todo is snoozed and stays quiet until then. */
    val snoozedUntil: Long? = null,
    /**
     * How often this occurrence has re-alerted without an answer.
     * [NAG_DISMISSED] means the user dismissed it for now.
     */
    val nagCount: Int = 0,
    /** When this occurrence last alerted; drives the spacing of the next reminder. */
    val lastAlarmAt: Long? = null,
    /** A list from the Lists module that belongs to this todo ("Go shopping"). */
    val listId: Long? = null,
) {
    val isInTrash: Boolean get() = deletedAt != null

    companion object {
        const val NAG_DISMISSED = -1
    }
}
