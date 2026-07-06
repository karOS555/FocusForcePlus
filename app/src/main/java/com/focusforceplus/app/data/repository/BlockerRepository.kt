package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.BlockedAppDao
import com.focusforceplus.app.data.db.dao.BlockerGroupDao
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockerRepository @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
    private val blockerGroupDao: BlockerGroupDao,
) {

    fun getAllApps(): Flow<List<BlockedAppEntity>> =
        blockedAppDao.getAll()

    suspend fun getAllAppsOnce(): List<BlockedAppEntity> =
        blockedAppDao.getAllOnce()

    fun getBlockedApps(): Flow<List<BlockedAppEntity>> =
        blockedAppDao.getBlockedApps()

    suspend fun getByPackageName(packageName: String): BlockedAppEntity? =
        blockedAppDao.getByPackageName(packageName)

    suspend fun insertApp(app: BlockedAppEntity): Long =
        blockedAppDao.insert(app)

    suspend fun updateApp(app: BlockedAppEntity) =
        blockedAppDao.update(app)

    suspend fun deleteApp(app: BlockedAppEntity) =
        blockedAppDao.delete(app)

    suspend fun resetDailyUsage() =
        blockedAppDao.resetDailyUsage()

    suspend fun updateUsedTime(id: Long, minutes: Int) =
        blockedAppDao.updateUsedTime(id, minutes)

    suspend fun updateUsedTimeByPackage(packageName: String, minutes: Int) =
        blockedAppDao.updateUsedTimeByPackage(packageName, minutes)

    suspend fun grantException(id: Long, untilMillis: Long, usedToday: Int) =
        blockedAppDao.updateException(id, untilMillis, usedToday)

    // ── Groups ────────────────────────────────────────────────────────────────

    fun getAllGroups(): Flow<List<BlockerGroupEntity>> = blockerGroupDao.getAll()

    suspend fun getAllGroupsOnce(): List<BlockerGroupEntity> = blockerGroupDao.getAllOnce()

    suspend fun getGroupByName(name: String): BlockerGroupEntity? = blockerGroupDao.getByName(name)

    /** Returns -1 when the name is already taken. */
    suspend fun insertGroup(group: BlockerGroupEntity): Long = blockerGroupDao.insert(group)

    suspend fun updateGroup(group: BlockerGroupEntity) = blockerGroupDao.update(group)

    suspend fun deleteGroup(group: BlockerGroupEntity) {
        blockerGroupDao.clearMembership(group.name)
        blockerGroupDao.delete(group)
    }

    suspend fun setAppGroup(packageName: String, groupName: String?) {
        blockedAppDao.getByPackageName(packageName)?.let {
            blockedAppDao.update(it.copy(groupName = groupName))
        }
    }
}
