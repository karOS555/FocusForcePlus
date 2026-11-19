package com.focusforceplus.app.util

import com.focusforceplus.app.data.db.dao.FocusSessionDao
import com.focusforceplus.app.data.db.dao.RoutineDao
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.FocusSessionRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything outside the blocker that names a block: sleep mode, focus sessions,
 * routines and the calendar setting each keep a list of block names to block.
 * A block is known by its name, so renaming one has to reach all of these lists,
 * or they quietly stop blocking it.
 */
@Singleton
class BlockGroupReferences @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val focusSessionDao: FocusSessionDao,
    private val routineDao: RoutineDao,
    private val settingsRepository: SettingsRepository,
    private val routineRegistry: ActiveRoutineRegistry,
    private val focusRegistry: FocusSessionRegistry,
) {
    suspend fun rename(from: String, to: String) {
        if (from == to) return
        fun Set<String>.renamed(): Set<String> = map { if (it == from) to else it }.toSet()

        runCatching {
            val schedule = sleepRepository.schedule.first()
            if (from in schedule.blockedGroups) {
                sleepRepository.saveSchedule(
                    schedule.copy(blockedGroupsCsv = schedule.blockedGroups.renamed().joinToString("|"))
                )
            }
        }
        runCatching {
            focusSessionDao.getAllOnce().forEach { session ->
                val names = session.blockedGroupsCsv.names()
                if (from in names) {
                    focusSessionDao.update(session.copy(blockedGroupsCsv = names.renamed().joinToString(",")))
                }
            }
        }
        runCatching {
            routineDao.getAllOnce().forEach { routine ->
                val names = routine.blockedGroupsCsv.names()
                if (from in names) {
                    routineDao.update(routine.copy(blockedGroupsCsv = names.renamed().joinToString(",")))
                }
            }
        }
        runCatching {
            val groups = settingsRepository.calendarBlockGroups.first()
            if (from in groups) settingsRepository.saveCalendarBlockGroups(groups.renamed())
        }
        // A session that is running right now keeps blocking the renamed block too.
        routineRegistry.activeSession.value?.let { session ->
            if (session.blockedGroups?.contains(from) == true) {
                routineRegistry.markActive(
                    session.routineId, session.blocksApps, session.invincible, session.blockedGroups.renamed(),
                )
            }
        }
        focusRegistry.activeSession.value?.let { session ->
            if (session.blockedGroups?.contains(from) == true) {
                focusRegistry.markActive(session.copy(blockedGroups = session.blockedGroups.renamed()))
            }
        }
    }

    private fun String?.names(): Set<String> =
        this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
}
