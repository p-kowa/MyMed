package com.example.mymed

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MyMedication::class, Reminder::class, MedicationHistory::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v2 -> v3: added `snoozeMinutes` to `reminders` (per-reminder snooze interval).
        // Default matches SnoozeManager.DEFAULT_SNOOZE_MINUTES so existing reminders
        // keep working with the previous global default.
        // Not private: covered by an instrumented MigrationTest (see androidTest).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN snoozeMinutes INTEGER NOT NULL DEFAULT ${SnoozeManager.DEFAULT_SNOOZE_MINUTES}"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymed.db"
                )
                    // Real migration: preserves all user data across version 2 -> 3.
                    .addMigrations(MIGRATION_2_3)
                    // Safety net ONLY for a hypothetical pre-repo version 1 DB we have
                    // no schema record of. Any known version (2, 3, ...) uses the
                    // explicit migrations above and never loses data.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
