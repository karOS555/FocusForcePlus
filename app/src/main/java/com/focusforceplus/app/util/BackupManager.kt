package com.focusforceplus.app.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.focusforceplus.app.data.db.AppDatabase
import com.focusforceplus.app.data.db.dao.BlockedAppDao
import com.focusforceplus.app.data.db.dao.BlockerGroupDao
import com.focusforceplus.app.data.db.dao.FocusCompletionDao
import com.focusforceplus.app.data.db.dao.FocusSessionDao
import com.focusforceplus.app.data.db.dao.RoutineCompletionDao
import com.focusforceplus.app.data.db.dao.RoutineDao
import com.focusforceplus.app.data.db.dao.RoutineTaskDao
import com.focusforceplus.app.data.db.dao.TodoDao
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.db.entity.RoutineCompletionEntity
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.data.repository.SleepSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full local backup as a single JSON document: routines + tasks, todos, blocker
 * rules and groups, focus sessions, completion history, **all app settings** and
 * the **sleep schedule**. Import replaces all current data and re-arms every alarm.
 *
 * Version 2 added the `settings` and `sleep` objects — before that a restored
 * backup silently dropped everything that lives in DataStore rather than Room
 * (reminder times, default priority, alarm spacing, blocker master switch, the
 * whole sleep schedule). Version 1 files still import; the missing sections are
 * simply left at their current values.
 *
 * Uses org.json for consistency with the existing checklist serialization — no new
 * dependency, nothing leaves the device except through the user's chosen file.
 */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val routineDao: RoutineDao,
    private val routineTaskDao: RoutineTaskDao,
    private val todoDao: TodoDao,
    private val blockedAppDao: BlockedAppDao,
    private val blockerGroupDao: BlockerGroupDao,
    private val focusSessionDao: FocusSessionDao,
    private val routineCompletionDao: RoutineCompletionDao,
    private val focusCompletionDao: FocusCompletionDao,
    private val settingsRepository: SettingsRepository,
    private val sleepRepository: SleepRepository,
    private val alarmHelper: AlarmHelper,
    private val todoAlarmHelper: TodoAlarmHelper,
    private val focusAlarmHelper: FocusAlarmHelper,
    private val sleepAlarmHelper: SleepAlarmHelper,
) {
    companion object {
        const val BACKUP_VERSION = 2
        const val SUGGESTED_FILE_NAME = "focusforceplus-backup.json"
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /** Writes the backup to [uri]; returns a summary or throws on I/O failure. */
    suspend fun exportTo(uri: Uri): String = withContext(Dispatchers.IO) {
        val routines = routineDao.getAllOnce()
        val tasks = routineTaskDao.getAllOnce()
        val todos = todoDao.getAllOnce()
        val blockedApps = blockedAppDao.getAllOnce()
        val focusSessions = focusSessionDao.getAllOnce()

        val root = JSONObject().apply {
            put("backupVersion", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("routines", JSONArray(routines.map { routineJson(it, tasks.filter { t -> t.routineId == it.id }) }))
            put("todos", JSONArray(todos.map { todoJson(it) }))
            put("blockedApps", JSONArray(blockedApps.map { blockedAppJson(it) }))
            put("blockerGroups", JSONArray(blockerGroupDao.getAllOnce().map { groupJson(it) }))
            put("focusSessions", JSONArray(focusSessions.map { focusSessionJson(it) }))
            put("routineCompletions", JSONArray(routineCompletionDao.getAllOnce().map { routineCompletionJson(it) }))
            put("focusCompletions", JSONArray(focusCompletionDao.getAllOnce().map { focusCompletionJson(it) }))
            put("settings", settingsJson())
            put("sleep", sleepJson(sleepRepository.schedule.first()))
        }

        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open the selected file for writing.")

        "Exported ${routines.size} routines, ${todos.size} todos, " +
            "${blockedApps.size} blocker rules, ${focusSessions.size} focus sessions, " +
            "plus your settings and sleep schedule."
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Replaces ALL current data with the backup content and re-arms every alarm.
     * Parses and validates completely before anything is deleted, so a broken file
     * never destroys existing data.
     */
    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Could not open the selected file.")

        val root = JSONObject(text)
        val version = root.optInt("backupVersion", -1)
        require(version in 1..BACKUP_VERSION) { "Unsupported backup version: $version" }

        // Parse everything first — fail before touching the database.
        val routines = mutableListOf<RoutineEntity>()
        val tasks = mutableListOf<RoutineTaskEntity>()
        root.getJSONArray("routines").forEachObject { obj ->
            routines += routineFromJson(obj)
            obj.getJSONArray("tasks").forEachObject { t -> tasks += taskFromJson(t) }
        }
        val todos = root.getJSONArray("todos").mapObjects { todoFromJson(it) }
        val blockedApps = root.getJSONArray("blockedApps").mapObjects { blockedAppFromJson(it) }
        val blockerGroups = root.optJSONArray("blockerGroups")
            ?.mapObjects { groupFromJson(it) } ?: emptyList()
        val focusSessions = root.getJSONArray("focusSessions").mapObjects { focusSessionFromJson(it) }
        val routineCompletions = root.optJSONArray("routineCompletions")
            ?.mapObjects { routineCompletionFromJson(it) } ?: emptyList()
        val focusCompletions = root.optJSONArray("focusCompletions")
            ?.mapObjects { focusCompletionFromJson(it) } ?: emptyList()
        // Version 1 files carry neither of these; leaving them null keeps the
        // current settings instead of resetting them to defaults.
        val settings = root.optJSONObject("settings")
        val sleep = root.optJSONObject("sleep")?.let { sleepFromJson(it) }

        // Cancel alarms of the data that is about to be replaced.
        routineDao.getAllOnce().forEach { alarmHelper.cancelRoutineAlarms(it.id) }
        todoDao.getAllOnce().forEach {
            todoAlarmHelper.cancelTodoAlarm(it.id)
            todoAlarmHelper.cancelTodoSnoozeAlarm(it.id)
        }
        focusSessionDao.getAllOnce().forEach { focusAlarmHelper.cancelSessionAlarms(it.id) }

        // Replace atomically — a failing insert rolls everything back.
        db.withTransaction {
            routineTaskDao.deleteAll()
            routineDao.deleteAll()
            todoDao.deleteAll()
            blockedAppDao.deleteAll()
            blockerGroupDao.deleteAll()
            focusSessionDao.deleteAll()
            routineCompletionDao.deleteAll()
            focusCompletionDao.deleteAll()

            routines.forEach { routineDao.insert(it) }
            routineTaskDao.insertAll(tasks)
            todos.forEach { todoDao.insert(it) }
            blockedApps.forEach { blockedAppDao.insert(it) }
            blockerGroups.forEach { blockerGroupDao.insert(it) }
            focusSessions.forEach { focusSessionDao.insert(it) }
            routineCompletions.forEach { routineCompletionDao.insert(it) }
            focusCompletions.forEach { focusCompletionDao.insert(it) }
        }

        // Settings live in DataStore, outside the Room transaction. They are applied
        // after the data landed so a failed import never leaves half-applied settings.
        settings?.let { applySettings(it) }
        sleep?.let { sleepRepository.saveSchedule(it) }

        // Re-arm alarms for the imported data — including the restored reminder
        // times and sleep schedule, not the ones that happened to be set before.
        alarmHelper.rescheduleAll(routines)
        todoAlarmHelper.rescheduleAll(todos, settingsRepository.digestTimes.first())
        focusAlarmHelper.rescheduleAll(focusSessions)
        sleepAlarmHelper.scheduleAll(sleepRepository.schedule.first())

        buildString {
            append("Imported ${routines.size} routines, ${todos.size} todos, ")
            append("${blockedApps.size} blocker rules, ${focusSessions.size} focus sessions")
            when {
                settings != null && sleep != null -> append(", settings and sleep schedule.")
                settings != null -> append(" and settings.")
                else -> append(".\nThis backup predates settings export, so your current settings were kept.")
            }
        }
    }

    // ── Entity <-> JSON mapping ───────────────────────────────────────────────

    private fun routineJson(r: RoutineEntity, routineTasks: List<RoutineTaskEntity>) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        putNullable("description", r.description)
        put("startTimeHour", r.startTimeHour)
        put("startTimeMinute", r.startTimeMinute)
        put("activeDays", r.activeDays)
        put("isActive", r.isActive)
        put("invincibleMode", r.invincibleMode)
        put("appBlockerEnabled", r.appBlockerEnabled)
        put("maxSnoozeCount", r.maxSnoozeCount)
        put("maxRescheduleCount", r.maxRescheduleCount)
        putNullable("iconKey", r.iconKey)
        put("createdAt", r.createdAt)
        put("tasks", JSONArray(routineTasks.map { taskJson(it) }))
    }

    private fun routineFromJson(o: JSONObject) = RoutineEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        description = o.optStringOrNull("description"),
        startTimeHour = o.getInt("startTimeHour"),
        startTimeMinute = o.getInt("startTimeMinute"),
        activeDays = o.getString("activeDays"),
        isActive = o.getBoolean("isActive"),
        invincibleMode = o.getBoolean("invincibleMode"),
        appBlockerEnabled = o.getBoolean("appBlockerEnabled"),
        maxSnoozeCount = o.getInt("maxSnoozeCount"),
        maxRescheduleCount = o.getInt("maxRescheduleCount"),
        iconKey = o.optStringOrNull("iconKey"),
        createdAt = o.getLong("createdAt"),
    )

    private fun taskJson(t: RoutineTaskEntity) = JSONObject().apply {
        put("id", t.id)
        put("routineId", t.routineId)
        put("name", t.name)
        putNullable("description", t.description)
        put("durationMinutes", t.durationMinutes)
        put("sortOrder", t.sortOrder)
        putNullable("iconKey", t.iconKey)
        put("reminderBeforeEndMinutes", t.reminderBeforeEndMinutes)
        put("overtimeReminderIntervalMinutes", t.overtimeReminderIntervalMinutes)
    }

    private fun taskFromJson(o: JSONObject) = RoutineTaskEntity(
        id = o.getLong("id"),
        routineId = o.getLong("routineId"),
        name = o.getString("name"),
        description = o.optStringOrNull("description"),
        durationMinutes = o.getInt("durationMinutes"),
        sortOrder = o.getInt("sortOrder"),
        iconKey = o.optStringOrNull("iconKey"),
        reminderBeforeEndMinutes = o.getInt("reminderBeforeEndMinutes"),
        overtimeReminderIntervalMinutes = o.getInt("overtimeReminderIntervalMinutes"),
    )

    private fun todoJson(t: TodoEntity) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        putNullable("description", t.description)
        putNullable("dueDateTime", t.dueDateTime)
        put("priority", t.priority)
        put("isCompleted", t.isCompleted)
        put("isRecurring", t.isRecurring)
        putNullable("recurringPattern", t.recurringPattern)
        put("snoozeCount", t.snoozeCount)
        put("maxSnoozeCount", t.maxSnoozeCount)
        put("rescheduleCount", t.rescheduleCount)
        put("maxRescheduleCount", t.maxRescheduleCount)
        put("createdAt", t.createdAt)
        putNullable("completedAt", t.completedAt)
        putNullable("postponedTo", t.postponedTo)
        putNullable("checklistJson", t.checklistJson)
    }

    private fun todoFromJson(o: JSONObject) = TodoEntity(
        id = o.getLong("id"),
        title = o.getString("title"),
        description = o.optStringOrNull("description"),
        dueDateTime = o.optLongOrNull("dueDateTime"),
        priority = o.getInt("priority"),
        isCompleted = o.getBoolean("isCompleted"),
        isRecurring = o.getBoolean("isRecurring"),
        recurringPattern = o.optStringOrNull("recurringPattern"),
        snoozeCount = o.getInt("snoozeCount"),
        maxSnoozeCount = o.getInt("maxSnoozeCount"),
        rescheduleCount = o.getInt("rescheduleCount"),
        maxRescheduleCount = o.getInt("maxRescheduleCount"),
        createdAt = o.getLong("createdAt"),
        completedAt = o.optLongOrNull("completedAt"),
        postponedTo = o.optLongOrNull("postponedTo"),
        checklistJson = o.optStringOrNull("checklistJson"),
    )

    private fun blockedAppJson(b: BlockedAppEntity) = JSONObject().apply {
        put("id", b.id)
        put("packageName", b.packageName)
        put("appName", b.appName)
        putNullable("dailyLimitMinutes", b.dailyLimitMinutes)
        put("usedTodayMinutes", b.usedTodayMinutes)
        put("isBlocked", b.isBlocked)
        put("blockDuringRoutines", b.blockDuringRoutines)
        put("blockDuringFocus", b.blockDuringFocus)
        put("invincibleMode", b.invincibleMode)
        putNullable("windowStartMinutes", b.windowStartMinutes)
        putNullable("windowEndMinutes", b.windowEndMinutes)
        putNullable("groupName", b.groupName)
    }

    private fun blockedAppFromJson(o: JSONObject) = BlockedAppEntity(
        id = o.getLong("id"),
        packageName = o.getString("packageName"),
        appName = o.getString("appName"),
        dailyLimitMinutes = o.optIntOrNull("dailyLimitMinutes"),
        usedTodayMinutes = o.optInt("usedTodayMinutes", 0),
        isBlocked = o.getBoolean("isBlocked"),
        blockDuringRoutines = o.getBoolean("blockDuringRoutines"),
        blockDuringFocus = o.getBoolean("blockDuringFocus"),
        invincibleMode = o.optBoolean("invincibleMode", false),
        windowStartMinutes = o.optIntOrNull("windowStartMinutes"),
        windowEndMinutes = o.optIntOrNull("windowEndMinutes"),
        // Exceptions are deliberately not restored — fresh day, fresh budget.
        exceptionUntilMillis = 0,
        exceptionsUsedToday = 0,
        groupName = o.optStringOrNull("groupName"),
    )

    private fun groupJson(g: BlockerGroupEntity) = JSONObject().apply {
        put("id", g.id)
        put("name", g.name)
        putNullable("sharedDailyLimitMinutes", g.sharedDailyLimitMinutes)
        put("invincibleMode", g.invincibleMode)
        putNullable("windowStartMinutes", g.windowStartMinutes)
        putNullable("windowEndMinutes", g.windowEndMinutes)
        g.windowDaysCsv?.let { put("windowDaysCsv", it) }
        g.blockDuringRoutines?.let { put("blockDuringRoutines", it) }
        g.blockDuringFocus?.let { put("blockDuringFocus", it) }
    }

    private fun groupFromJson(o: JSONObject) = BlockerGroupEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        sharedDailyLimitMinutes = o.optIntOrNull("sharedDailyLimitMinutes"),
        invincibleMode = o.optBoolean("invincibleMode", false),
        windowStartMinutes = o.optIntOrNull("windowStartMinutes"),
        windowEndMinutes = o.optIntOrNull("windowEndMinutes"),
        windowDaysCsv = o.optStringOrNull("windowDaysCsv"),
        blockDuringRoutines = if (o.has("blockDuringRoutines")) o.getBoolean("blockDuringRoutines") else null,
        blockDuringFocus = if (o.has("blockDuringFocus")) o.getBoolean("blockDuringFocus") else null,
    )

    private fun focusSessionJson(f: FocusSessionEntity) = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("type", f.type)
        put("durationMinutes", f.durationMinutes)
        put("enableDnd", f.enableDnd)
        put("blockNotifications", f.blockNotifications)
        put("appBlockerEnabled", f.appBlockerEnabled)
        put("invincibleMode", f.invincibleMode)
        putNullable("blockedGroupsCsv", f.blockedGroupsCsv)
        putNullable("scheduledDays", f.scheduledDays)
        putNullable("scheduledTimeHour", f.scheduledTimeHour)
        putNullable("scheduledTimeMinute", f.scheduledTimeMinute)
        put("isActive", f.isActive)
        put("createdAt", f.createdAt)
    }

    private fun focusSessionFromJson(o: JSONObject) = FocusSessionEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        type = o.getString("type"),
        durationMinutes = o.getInt("durationMinutes"),
        enableDnd = o.getBoolean("enableDnd"),
        blockNotifications = o.getBoolean("blockNotifications"),
        appBlockerEnabled = o.getBoolean("appBlockerEnabled"),
        invincibleMode = o.optBoolean("invincibleMode", false),
        blockedGroupsCsv = o.optStringOrNull("blockedGroupsCsv"),
        scheduledDays = o.optStringOrNull("scheduledDays"),
        scheduledTimeHour = o.optIntOrNull("scheduledTimeHour"),
        scheduledTimeMinute = o.optIntOrNull("scheduledTimeMinute"),
        isActive = o.getBoolean("isActive"),
        createdAt = o.getLong("createdAt"),
    )

    private fun routineCompletionJson(c: RoutineCompletionEntity) = JSONObject().apply {
        put("id", c.id)
        put("routineId", c.routineId)
        put("routineName", c.routineName)
        put("completedAt", c.completedAt)
        put("scheduledMinutes", c.scheduledMinutes)
        put("overtimeMinutes", c.overtimeMinutes)
        put("tasksCompleted", c.tasksCompleted)
    }

    private fun routineCompletionFromJson(o: JSONObject) = RoutineCompletionEntity(
        id = o.getLong("id"),
        routineId = o.getLong("routineId"),
        routineName = o.getString("routineName"),
        completedAt = o.getLong("completedAt"),
        scheduledMinutes = o.getInt("scheduledMinutes"),
        overtimeMinutes = o.getInt("overtimeMinutes"),
        tasksCompleted = o.getInt("tasksCompleted"),
    )

    private fun focusCompletionJson(c: FocusCompletionEntity) = JSONObject().apply {
        put("id", c.id)
        put("sessionId", c.sessionId)
        put("name", c.name)
        put("type", c.type)
        put("focusedMinutes", c.focusedMinutes)
        put("completedAt", c.completedAt)
        put("endedEarly", c.endedEarly)
    }

    private fun focusCompletionFromJson(o: JSONObject) = FocusCompletionEntity(
        id = o.getLong("id"),
        sessionId = o.getLong("sessionId"),
        name = o.getString("name"),
        type = o.getString("type"),
        focusedMinutes = o.getInt("focusedMinutes"),
        completedAt = o.getLong("completedAt"),
        endedEarly = o.getBoolean("endedEarly"),
    )

    // ── Settings (DataStore) ──────────────────────────────────────────────────
    //
    // Everything the user can configure, except state that is meaningless on
    // another device or after a restore: the onboarding flag, the seen-help-hint
    // keys, and the Tamper Protection clock anchor. Tamper Protection's own
    // enabled flag is deliberately NOT restored either — re-enabling a lock must
    // stay an explicit, in-window decision (compliance guide 2.2).

    private suspend fun settingsJson() = JSONObject().apply {
        put("digestTimes", JSONArray(
            settingsRepository.digestTimes.first().map {
                SettingsRepository.encodeTime(it.first, it.second)
            }
        ))
        put("defaultPriority", settingsRepository.defaultPriority.first())
        put("autoDeleteDays", settingsRepository.autoDeleteDays.first())
        put("undatedAlarmGapMinutes", settingsRepository.undatedAlarmGapMinutes.first())
        put("blockerEnabled", settingsRepository.blockerEnabled.first())
        put("blockDuringRoutines", settingsRepository.blockDuringRoutines.first())
        put("blockDuringFocus", settingsRepository.blockDuringFocus.first())
        put("alarmSoundEnabled", settingsRepository.alarmSoundEnabled.first())
        put("alarmVibrationEnabled", settingsRepository.alarmVibrationEnabled.first())
        put("tpWindowStartMinutes", settingsRepository.tpWindowStartMinutes.first())
        put("tpWindowDurationMinutes", settingsRepository.tpWindowDurationMinutes.first())
    }

    private suspend fun applySettings(o: JSONObject) {
        o.optJSONArray("digestTimes")?.let { array ->
            val times = (0 until array.length())
                .mapNotNull { SettingsRepository.decodeTime(array.getString(it)) }
                .sortedBy { it.first * 60 + it.second }
            if (times.isNotEmpty()) settingsRepository.saveDigestTimes(times)
        }
        if (o.has("defaultPriority")) settingsRepository.saveDefaultPriority(o.getInt("defaultPriority"))
        if (o.has("autoDeleteDays")) settingsRepository.saveAutoDeleteDays(o.getInt("autoDeleteDays"))
        if (o.has("undatedAlarmGapMinutes")) {
            settingsRepository.saveUndatedAlarmGapMinutes(o.getInt("undatedAlarmGapMinutes"))
        }
        if (o.has("blockerEnabled")) settingsRepository.saveBlockerEnabled(o.getBoolean("blockerEnabled"))
        if (o.has("blockDuringRoutines")) {
            settingsRepository.saveBlockDuringRoutines(o.getBoolean("blockDuringRoutines"))
        }
        if (o.has("blockDuringFocus")) {
            settingsRepository.saveBlockDuringFocus(o.getBoolean("blockDuringFocus"))
        }
        if (o.has("alarmSoundEnabled")) {
            settingsRepository.saveAlarmSoundEnabled(o.getBoolean("alarmSoundEnabled"))
        }
        if (o.has("alarmVibrationEnabled")) {
            settingsRepository.saveAlarmVibrationEnabled(o.getBoolean("alarmVibrationEnabled"))
        }
        if (o.has("tpWindowStartMinutes") && o.has("tpWindowDurationMinutes")) {
            settingsRepository.saveTpWindow(
                o.getInt("tpWindowStartMinutes"),
                o.getInt("tpWindowDurationMinutes"),
            )
        }
    }

    // ── Sleep schedule (DataStore) ────────────────────────────────────────────

    private fun sleepJson(s: SleepSchedule) = JSONObject().apply {
        put("enabled", s.enabled)
        put("bedtimeMinutes", s.bedtimeMinutes)
        put("wakeMinutes", s.wakeMinutes)
        putNullable("activeDaysCsv", s.activeDaysCsv)
        put("windDownMinutes", s.windDownMinutes)
        put("dndEnabled", s.dndEnabled)
        putNullable("blockedGroupsCsv", s.blockedGroupsCsv)
        put("invincibleMode", s.invincibleMode)
        put("bedtimeRoutineId", s.bedtimeRoutineId)
        put("morningRoutineId", s.morningRoutineId)
        put("nativeAlarmCount", s.nativeAlarmCount)
        put("nativeAlarmGapMinutes", s.nativeAlarmGapMinutes)
    }

    private fun sleepFromJson(o: JSONObject) = SleepSchedule(
        enabled = o.optBoolean("enabled", false),
        bedtimeMinutes = o.optInt("bedtimeMinutes", 23 * 60),
        wakeMinutes = o.optInt("wakeMinutes", 7 * 60),
        activeDaysCsv = o.optStringOrNull("activeDaysCsv"),
        windDownMinutes = o.optInt("windDownMinutes", 30),
        dndEnabled = o.optBoolean("dndEnabled", true),
        blockedGroupsCsv = o.optStringOrNull("blockedGroupsCsv"),
        invincibleMode = o.optBoolean("invincibleMode", false),
        bedtimeRoutineId = o.optLong("bedtimeRoutineId", 0L),
        morningRoutineId = o.optLong("morningRoutineId", 0L),
        nativeAlarmCount = o.optInt("nativeAlarmCount", 0),
        nativeAlarmGapMinutes = o.optInt("nativeAlarmGapMinutes", 10),
    )

    // ── org.json helpers ──────────────────────────────────────────────────────

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else getLong(key)

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) action(getJSONObject(i))
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
