package com.focusforceplus.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Database(
    entities = [
        RoutineEntity::class,
        RoutineTaskEntity::class,
        TodoEntity::class,
        BlockedAppEntity::class,
        BlockerGroupEntity::class,
        FocusSessionEntity::class,
        RoutineCompletionEntity::class,
        FocusCompletionEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun routineTaskDao(): RoutineTaskDao
    abstract fun todoDao(): TodoDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockerGroupDao(): BlockerGroupDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun routineCompletionDao(): RoutineCompletionDao
    abstract fun focusCompletionDao(): FocusCompletionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE routines ADD COLUMN maxRescheduleCount INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routines ADD COLUMN iconKey TEXT")
                db.execSQL("ALTER TABLE routine_tasks ADD COLUMN iconKey TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN rescheduleCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE todos ADD COLUMN maxRescheduleCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN checklistJson TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN invincibleMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN windowStartMinutes INTEGER")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN windowEndMinutes INTEGER")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN exceptionUntilMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN exceptionsUsedToday INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN groupName TEXT")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN invincibleMode INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS routine_completions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "routineId INTEGER NOT NULL, " +
                        "routineName TEXT NOT NULL, " +
                        "completedAt INTEGER NOT NULL, " +
                        "scheduledMinutes INTEGER NOT NULL, " +
                        "overtimeMinutes INTEGER NOT NULL, " +
                        "tasksCompleted INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_completions_completedAt " +
                        "ON routine_completions (completedAt)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS focus_completions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "focusedMinutes INTEGER NOT NULL, " +
                        "completedAt INTEGER NOT NULL, " +
                        "endedEarly INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_focus_completions_completedAt " +
                        "ON focus_completions (completedAt)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blocker_groups (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "sharedDailyLimitMinutes INTEGER, " +
                        "invincibleMode INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_blocker_groups_name " +
                        "ON blocker_groups (name)"
                )
                // Promote existing free-text group labels to real groups.
                db.execSQL(
                    "INSERT OR IGNORE INTO blocker_groups (name) " +
                        "SELECT DISTINCT groupName FROM blocked_apps WHERE groupName IS NOT NULL"
                )
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN blockedGroupsCsv TEXT")
            }
        }
    }
}
