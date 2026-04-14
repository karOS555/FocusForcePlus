package com.focusforceplus.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusforceplus.app.data.db.dao.BlockedAppDao
import com.focusforceplus.app.data.db.dao.FocusSessionDao
import com.focusforceplus.app.data.db.dao.RoutineDao
import com.focusforceplus.app.data.db.dao.RoutineTaskDao
import com.focusforceplus.app.data.db.dao.TodoDao
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.db.entity.TodoEntity

@Database(
    entities = [
        RoutineEntity::class,
        RoutineTaskEntity::class,
        TodoEntity::class,
        BlockedAppEntity::class,
        FocusSessionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun routineTaskDao(): RoutineTaskDao
    abstract fun todoDao(): TodoDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun focusSessionDao(): FocusSessionDao

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
    }
}
