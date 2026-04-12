package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.BlockedAppDao
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockerRepository @Inject constructor(
    private val blockedAppDao: BlockedAppDao
) {

    fun getAllApps(): Flow<List<BlockedAppEntity>> =
        blockedAppDao.getAll()

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
}
