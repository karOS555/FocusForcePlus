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
import com.focusforceplus.app.data.db.dao.ListDao
import com.focusforceplus.app.data.db.dao.RoutineCompletionDao
import com.focusforceplus.app.data.db.dao.RoutineDao
import com.focusforceplus.app.data.db.dao.RoutineTaskDao
import com.focusforceplus.app.data.db.dao.TodoDao
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.data.db.entity.ListSuggestionEntity
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
        ListEntity::class,
        ListItemEntity::class,
        ListSuggestionEntity::class,
    ],
    version = 12,
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
    abstract fun listDao(): ListDao

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN windowStartMinutes INTEGER")
                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN windowEndMinutes INTEGER")
                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN windowDaysCsv TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable on purpose: null keeps each member app's own setting.
                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN blockDuringRoutines INTEGER")
                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN blockDuringFocus INTEGER")
            }
        }

        /**
         * 10 -> 11: icons for todos, focus sessions and blocker groups, the trash
         * (soft delete), the confirmation guards, the todo reminder state, and the
         * Lists module. Only adds columns and tables, so nothing existing is touched.
         */
        /** Sections and amounts on lists, and lists on routine steps. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lists ADD COLUMN sectionOrder TEXT")
                db.execSQL("ALTER TABLE list_items ADD COLUMN section TEXT")
                db.execSQL("ALTER TABLE list_items ADD COLUMN quantity TEXT")
                db.execSQL("ALTER TABLE list_suggestions ADD COLUMN section TEXT")
                db.execSQL("ALTER TABLE routine_tasks ADD COLUMN listId INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN preAlarmMinutes INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE routines ADD COLUMN enableDnd INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routines ADD COLUMN blockedGroupsCsv TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN iconKey TEXT")
                db.execSQL("ALTER TABLE todos ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE todos ADD COLUMN guards TEXT")
                db.execSQL("ALTER TABLE todos ADD COLUMN snoozedUntil INTEGER")
                db.execSQL("ALTER TABLE todos ADD COLUMN nagCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE todos ADD COLUMN lastAlarmAt INTEGER")
                db.execSQL("ALTER TABLE todos ADD COLUMN listId INTEGER")

                db.execSQL("ALTER TABLE routines ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN guards TEXT")

                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN iconKey TEXT")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN deletedAt INTEGER")

                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN iconKey TEXT")
                db.execSQL("ALTER TABLE blocker_groups ADD COLUMN alwaysBlock INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS lists (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "iconKey TEXT, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "deletedAt INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS list_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "listId INTEGER NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "note TEXT, " +
                        "isChecked INTEGER NOT NULL, " +
                        "checkedAt INTEGER, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(listId) REFERENCES lists(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_items_listId ON list_items (listId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS list_suggestions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "listId INTEGER NOT NULL, " +
                        "`key` TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "useCount INTEGER NOT NULL, " +
                        "lastUsedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_list_suggestions_listId_key " +
                        "ON list_suggestions (listId, `key`)"
                )
            }
        }
    }
}
