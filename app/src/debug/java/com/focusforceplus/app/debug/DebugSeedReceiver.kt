package com.focusforceplus.app.debug

import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.db.entity.RoutineCompletionEntity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import com.focusforceplus.app.data.db.AppDatabase
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.repository.ListRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.AlarmRearmer
import com.focusforceplus.app.util.FocusAlarmHelper
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Sample data over adb, debug builds only.
 *
 *   adb shell am broadcast -n com.focusforceplus.app/.debug.DebugSeedReceiver \
 *       -a com.focusforceplus.app.DEBUG_SEED
 *   adb shell am broadcast -n com.focusforceplus.app/.debug.DebugSeedReceiver \
 *       -a com.focusforceplus.app.DEBUG_DUE --ei priority 2 --ei seconds 20
 *
 * SEED replaces everything in the database with a believable day. DUE adds one
 * todo that becomes due in a few seconds, to watch an alarm from start to end.
 */
class DebugSeedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SeedEntryPoint {
        fun database(): AppDatabase
        fun settings(): SettingsRepository
        fun todoEngine(): TodoReminderEngine
        fun lists(): ListRepository
        fun routineAlarms(): AlarmHelper
        fun focusAlarms(): FocusAlarmHelper
        fun rearmer(): AlarmRearmer
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, SeedEntryPoint::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_SEED -> seed(context.applicationContext, ep)
                    ACTION_DUE -> due(
                        ep,
                        priority = intent.getIntExtra("priority", 2),
                        seconds = intent.getIntExtra("seconds", 20),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "seeding failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun due(ep: SeedEntryPoint, priority: Int, seconds: Int) {
        val now = System.currentTimeMillis()
        val id = ep.todoEngine().save(
            TodoEntity(
                title = when (priority) {
                    2 -> "Call the dentist back"
                    1 -> "Send the meter reading"
                    else -> "Water the plants"
                },
                description = if (priority == 2) "Ask about the Thursday slot" else null,
                iconKey = if (priority == 2) "phone" else null,
                priority = priority,
                dueDateTime = now + seconds * 1_000L,
                maxSnoozeCount = 2,
                maxRescheduleCount = 1,
                createdAt = now,
            )
        )
        Log.i(TAG, "todo $id due in $seconds s, priority $priority")
    }

    private suspend fun seed(context: Context, ep: SeedEntryPoint) {
        val db = ep.database()
        val engine = ep.todoEngine()
        val settings = ep.settings()
        val now = System.currentTimeMillis()

        // Whatever is in the database now is about to go; so do its alarms and
        // notifications, or they would fire later for rows that no longer exist.
        engine.pauseAll()
        db.routineDao().getAllOnce().forEach { ep.routineAlarms().cancelRoutineAlarms(it.id) }
        db.focusSessionDao().getAllOnce().forEach { ep.focusAlarms().cancelSessionAlarms(it.id) }
        NotificationManagerCompat.from(context).cancelAll()
        settings.saveOnboardingCompleted(true)
        listOf("routines", "todos", "blocker", "focus", "sleep", "lists", "settings_entry")
            .forEach { settings.markHelpHintSeen(it) }

        db.withTransaction {
            db.routineTaskDao().deleteAll()
            db.routineDao().deleteAll()
            db.todoDao().deleteAll()
            db.blockedAppDao().deleteAll()
            db.blockerGroupDao().deleteAll()
            db.focusSessionDao().deleteAll()
            db.listDao().deleteAllItems()
            db.listDao().deleteAllSuggestions()
            db.listDao().deleteAllLists()

            val workBag = db.listDao().insertList(
                ListEntity(name = "Work bag", iconKey = "work", sortOrder = 2, createdAt = now)
            )
            listOf("Laptop", "Charger", "Keys", "Lunch box", "Water bottle").forEachIndexed { i, text ->
                db.listDao().insertItem(ListItemEntity(listId = workBag, text = text, sortOrder = i, createdAt = now))
            }

            val everyDay = "MO,DI,MI,DO,FR,SA,SO"
            val morning = db.routineDao().insert(
                RoutineEntity(
                    name = "Morning routine", description = "Out of bed and out of the door",
                    startTimeHour = 7, startTimeMinute = 0, activeDays = everyDay,
                    iconKey = "wb_sunny", invincibleMode = true, createdAt = now,
                )
            )
            db.routineTaskDao().insertAll(
                listOf(
                    RoutineTaskEntity(routineId = morning, name = "Make the bed", durationMinutes = 3, sortOrder = 0, iconKey = "bed"),
                    RoutineTaskEntity(routineId = morning, name = "Shower", durationMinutes = 10, sortOrder = 1, iconKey = "shower"),
                    RoutineTaskEntity(routineId = morning, name = "Breakfast", durationMinutes = 15, sortOrder = 2, iconKey = "breakfast_dining"),
                    RoutineTaskEntity(routineId = morning, name = "Pack the bag", durationMinutes = 5, sortOrder = 3, iconKey = "work", listId = workBag),
                )
            )
            val evening = db.routineDao().insert(
                RoutineEntity(
                    name = "Evening routine", description = null,
                    startTimeHour = 21, startTimeMinute = 30, activeDays = everyDay,
                    iconKey = "bedtime", createdAt = now,
                )
            )
            db.routineTaskDao().insertAll(
                listOf(
                    RoutineTaskEntity(routineId = evening, name = "Tidy the desk", durationMinutes = 5, sortOrder = 0, iconKey = "cleaning_home"),
                    RoutineTaskEntity(routineId = evening, name = "Lay out clothes", durationMinutes = 5, sortOrder = 1, iconKey = "checkroom"),
                    RoutineTaskEntity(routineId = evening, name = "Read", durationMinutes = 20, sortOrder = 2, iconKey = "menu_book"),
                )
            )
            db.routineDao().insert(
                RoutineEntity(
                    name = "Workout", description = null,
                    startTimeHour = 18, startTimeMinute = 0, activeDays = "MO,MI,FR",
                    iconKey = "fitness_center", isActive = true, createdAt = now,
                )
            ).also { workout ->
                db.routineTaskDao().insertAll(
                    listOf(
                        RoutineTaskEntity(routineId = workout, name = "Warm up", durationMinutes = 5, sortOrder = 0, iconKey = "accessibility"),
                        RoutineTaskEntity(routineId = workout, name = "Strength", durationMinutes = 30, sortOrder = 1, iconKey = "fitness_center"),
                        RoutineTaskEntity(routineId = workout, name = "Stretch", durationMinutes = 10, sortOrder = 2, iconKey = "self_improvement"),
                    )
                )
            }

            db.focusSessionDao().insert(
                FocusSessionEntity(name = "Deep work", type = "WORK", durationMinutes = 50, invincibleMode = true, createdAt = now)
            )
            db.focusSessionDao().insert(
                FocusSessionEntity(
                    name = "Study", type = "STUDY", durationMinutes = 45,
                    scheduledDays = "MO,TU,WE,TH,FR", scheduledTimeHour = 16, scheduledTimeMinute = 0, createdAt = now,
                )
            )

            db.blockerGroupDao().insert(
                BlockerGroupEntity(name = "Social media", sharedDailyLimitMinutes = 60, invincibleMode = true)
            )
            listOf(
                Triple("com.instagram.android", "Instagram", 34),
                Triple("com.zhiliaoapp.musically", "TikTok", 21),
                Triple("com.reddit.frontpage", "Reddit", 5),
            ).forEach { (pkg, name, used) ->
                db.blockedAppDao().insert(
                    BlockedAppEntity(packageName = pkg, appName = name, groupName = "Social media", usedTodayMinutes = used)
                )
            }
            db.blockedAppDao().insert(
                BlockedAppEntity(
                    packageName = "com.google.android.youtube", appName = "YouTube",
                    windowStartMinutes = 9 * 60, windowEndMinutes = 17 * 60,
                )
            )
        }

        fun today(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24L * 60L * 60L * 1000L
        fun later(time: Long) = if (time > now + 60_000L) time else time + day

        engine.save(
            TodoEntity(
                title = "Call the dentist back", description = "Ask about the Thursday slot",
                iconKey = "phone", priority = 2, dueDateTime = later(today(14, 0)),
                maxSnoozeCount = 2, maxRescheduleCount = 1, createdAt = now,
            )
        )
        engine.save(
            TodoEntity(
                title = "Send the meter reading", iconKey = "description", priority = 1,
                dueDateTime = later(today(19, 0)), createdAt = now,
                checklistJson = listOf(
                    ChecklistItem("Electricity", done = true),
                    ChecklistItem("Gas"),
                    ChecklistItem("Water"),
                ).toChecklistJson(),
            )
        )
        engine.save(
            TodoEntity(
                title = "Take vitamin D", iconKey = "medication", priority = 1,
                dueDateTime = later(today(21, 30)), isRecurring = true, recurringPattern = "DAILY",
                createdAt = now,
            )
        )
        engine.save(TodoEntity(title = "Water the plants", iconKey = "yard", priority = 0, createdAt = now))
        engine.save(
            TodoEntity(
                title = "Renew the library card", iconKey = "menu_book", priority = 1,
                dueDateTime = later(today(10, 0)) + 2 * day, createdAt = now,
            )
        )
        val doneId = engine.save(TodoEntity(title = "Book the eye test", iconKey = "medical_services", priority = 1, createdAt = now))
        engine.complete(doneId)
        val binId = engine.save(TodoEntity(title = "Old shopping note", priority = 0, createdAt = now))
        engine.moveToTrash(binId)

        // A week of history, so the home screen and the statistics have something to show.
        db.routineCompletionDao().deleteAll()
        db.focusCompletionDao().deleteAll()
        val morningId = db.routineDao().getAllOnce().first { it.name == "Morning routine" }.id
        val eveningId = db.routineDao().getAllOnce().first { it.name == "Evening routine" }.id
        val focusMinutes = listOf(50, 95, 0, 45, 140, 25, 70)
        for (daysAgo in 6 downTo 0) {
            fun at(hour: Int, minute: Int) = today(hour, minute) - daysAgo * day
            if (daysAgo != 2) {
                db.routineCompletionDao().insert(
                    RoutineCompletionEntity(
                        routineId = morningId, routineName = "Morning routine", completedAt = at(7, 38),
                        scheduledMinutes = 33, overtimeMinutes = if (daysAgo == 4) 6 else 0, tasksCompleted = 4,
                    )
                )
            }
            if (daysAgo in 1..5) {
                db.routineCompletionDao().insert(
                    RoutineCompletionEntity(
                        routineId = eveningId, routineName = "Evening routine", completedAt = at(22, 5),
                        scheduledMinutes = 30, overtimeMinutes = 0, tasksCompleted = 3,
                    )
                )
            }
            focusMinutes[6 - daysAgo].takeIf { it > 0 }?.let { minutes ->
                db.focusCompletionDao().insert(
                    FocusCompletionEntity(
                        sessionId = 0L, name = "Deep work", type = "WORK", focusedMinutes = minutes,
                        completedAt = at(15, 0), endedEarly = false,
                    )
                )
            }
        }

        val lists = ep.lists()
        val groceries = lists.createList("Groceries", "shopping_cart")
        listOf(
            "Tomatoes 500 g" to "Produce", "Bananas" to "Produce", "2 Milk" to "Dairy", "6 Eggs" to "Dairy",
            "Bread" to "Bakery", "Coffee" to "Pantry", "Oat flakes" to "Pantry",
        ).forEach { (text, section) ->
            lists.addItem(groceries, text)
            lists.getItemsOnce(groceries).last().let { lists.setSection(it, section) }
        }
        lists.getItemsOnce(groceries).filter { it.text == "Bananas" }.forEach { lists.setChecked(it.id, true) }
        engine.save(
            TodoEntity(
                title = "Do the weekly shop", iconKey = "shopping_cart", priority = 1,
                dueDateTime = later(today(17, 30)), listId = groceries, createdAt = now,
            )
        )
        val packing = lists.createList("Weekend trip", "train")
        listOf("Charger", "Toothbrush", "Rain jacket", "Tickets").forEach { lists.addItem(packing, it) }

        ep.rearmer().rearmAll()
        Log.i(TAG, "sample data written")
    }

    private companion object {
        const val TAG = "FfpDebugSeed"
        const val ACTION_SEED = "com.focusforceplus.app.DEBUG_SEED"
        const val ACTION_DUE = "com.focusforceplus.app.DEBUG_DUE"
    }
}
