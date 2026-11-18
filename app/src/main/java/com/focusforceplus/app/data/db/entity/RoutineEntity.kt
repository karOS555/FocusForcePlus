package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val startTimeHour: Int,
    val startTimeMinute: Int,
    /** Komma-separierte Wochentage, z.B. "MO,DI,MI" */
    val activeDays: String,
    val isActive: Boolean = true,
    /** Verhindert das Schließen/Überspringen der Routine */
    val invincibleMode: Boolean = false,
    val appBlockerEnabled: Boolean = true,
    val maxSnoozeCount: Int = 2,
    val maxRescheduleCount: Int = 1,
    val iconKey: String? = null,
    val createdAt: Long,
    /** Set when the routine is in the trash; null = not deleted. */
    val deletedAt: Long? = null,
    /** This routine's own speed bumps (see GuardOverrides); null = the defaults. */
    val guards: String? = null,
    /** Minutes before the start for the quiet heads-up; 0 = none. */
    val preAlarmMinutes: Int = 15,
    /** Do Not Disturb while the routine runs. */
    val enableDnd: Boolean = false,
    /** Only these block groups while it runs (comma-separated); null = all blocked apps. */
    val blockedGroupsCsv: String? = null,
) {
    val blockedGroups: Set<String>?
        get() = blockedGroupsCsv?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?.takeIf { it.isNotEmpty() }
}
