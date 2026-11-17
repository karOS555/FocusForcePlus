package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/** Deleting a session moves it to the trash; see [TodoDao] for the convention. */
@Dao
interface FocusSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NULL ORDER BY name")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id AND deletedAt IS NULL")
    fun getById(id: Long): Flow<FocusSessionEntity?>

    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE isActive = 1
        AND deletedAt IS NULL
        AND scheduledDays IS NOT NULL
        AND scheduledTimeHour IS NOT NULL
        ORDER BY scheduledTimeHour, scheduledTimeMinute
    """
    )
    fun getActiveScheduled(): Flow<List<FocusSessionEntity>>

    // ── Trash ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashed(): Flow<List<FocusSessionEntity>>

    @Query("UPDATE focus_sessions SET deletedAt = :at WHERE id = :id")
    suspend fun moveToTrash(id: Long, at: Long)

    @Query("UPDATE focus_sessions SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("DELETE FROM focus_sessions WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM focus_sessions WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrashedBefore(before: Long)

    // ── Backup / restore ──────────────────────────────────────────────────────

    /** Everything, including the trash. */
    @Query("SELECT * FROM focus_sessions")
    suspend fun getAllOnce(): List<FocusSessionEntity>

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}
