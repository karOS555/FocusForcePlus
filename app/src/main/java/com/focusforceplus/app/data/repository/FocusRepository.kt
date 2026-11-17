package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.FocusCompletionDao
import com.focusforceplus.app.data.db.dao.FocusSessionDao
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusRepository @Inject constructor(
    private val focusSessionDao: FocusSessionDao,
    private val focusCompletionDao: FocusCompletionDao,
) {
    /** Every saved session that is not in the trash. */
    fun getAllSessions(): Flow<List<FocusSessionEntity>> =
        focusSessionDao.getAll()

    fun getSessionById(id: Long): Flow<FocusSessionEntity?> =
        focusSessionDao.getById(id)

    fun getActiveScheduledSessions(): Flow<List<FocusSessionEntity>> =
        focusSessionDao.getActiveScheduled()

    suspend fun insertSession(session: FocusSessionEntity): Long =
        focusSessionDao.insert(session)

    suspend fun updateSession(session: FocusSessionEntity) =
        focusSessionDao.update(session)

    // --- Trash ---

    fun getTrashedSessions(): Flow<List<FocusSessionEntity>> = focusSessionDao.getTrashed()

    suspend fun moveToTrash(id: Long) = focusSessionDao.moveToTrash(id, System.currentTimeMillis())

    suspend fun restoreFromTrash(id: Long) = focusSessionDao.restoreFromTrash(id)

    suspend fun deleteForever(id: Long) = focusSessionDao.deleteForever(id)

    suspend fun emptyTrash() = focusSessionDao.emptyTrash()

    suspend fun purgeTrashedBefore(before: Long) = focusSessionDao.purgeTrashedBefore(before)

    // --- Completion history ---

    suspend fun recordCompletion(completion: FocusCompletionEntity) =
        focusCompletionDao.insert(completion)

    fun getCompletionsSince(since: Long): Flow<List<FocusCompletionEntity>> =
        focusCompletionDao.getSince(since)
}
