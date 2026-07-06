package com.focusforceplus.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockerGroupDao {

    /** Returns -1 when a group with the same name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(group: BlockerGroupEntity): Long

    @Update
    suspend fun update(group: BlockerGroupEntity)

    @Delete
    suspend fun delete(group: BlockerGroupEntity)

    @Query("SELECT * FROM blocker_groups ORDER BY name")
    fun getAll(): Flow<List<BlockerGroupEntity>>

    @Query("SELECT * FROM blocker_groups ORDER BY name")
    suspend fun getAllOnce(): List<BlockerGroupEntity>

    @Query("SELECT * FROM blocker_groups WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): BlockerGroupEntity?

    /** Clears membership when a group is deleted. */
    @Query("UPDATE blocked_apps SET groupName = NULL WHERE groupName = :name")
    suspend fun clearMembership(name: String)

    // Backup / restore
    @Query("DELETE FROM blocker_groups")
    suspend fun deleteAll()
}
