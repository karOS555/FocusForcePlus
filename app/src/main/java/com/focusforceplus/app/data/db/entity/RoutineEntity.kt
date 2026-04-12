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
    val createdAt: Long
)
