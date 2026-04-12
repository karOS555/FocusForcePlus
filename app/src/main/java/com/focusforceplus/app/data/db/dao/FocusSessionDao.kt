package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Delete
    suspend fun delete(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY name")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    fun getById(id: Long): Flow<FocusSessionEntity?>

    @Query("""
        SELECT * FROM focus_sessions
        WHERE isActive = 1
        AND scheduledDays IS NOT NULL
        AND scheduledTimeHour IS NOT NULL
        ORDER BY scheduledTimeHour, scheduledTimeMinute
    """)
    fun getActiveScheduled(): Flow<List<FocusSessionEntity>>
}
