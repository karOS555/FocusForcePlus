package com.focusforceplus.app.data.repository

import com.focusforceplus.app.data.db.dao.FocusSessionDao
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusRepository @Inject constructor(
    private val focusSessionDao: FocusSessionDao
) {

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

    suspend fun deleteSession(session: FocusSessionEntity) =
        focusSessionDao.delete(session)
}
