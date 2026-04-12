package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedAppEntity): Long

    @Update
    suspend fun update(app: BlockedAppEntity)

    @Delete
    suspend fun delete(app: BlockedAppEntity)

    @Query("SELECT * FROM blocked_apps ORDER BY appName")
    fun getAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1 ORDER BY appName")
    fun getBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): BlockedAppEntity?

    @Query("UPDATE blocked_apps SET usedTodayMinutes = 0")
    suspend fun resetDailyUsage()

    @Query("UPDATE blocked_apps SET usedTodayMinutes = :minutes WHERE id = :id")
    suspend fun updateUsedTime(id: Long, minutes: Int)
}
