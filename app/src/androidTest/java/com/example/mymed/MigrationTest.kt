package com.example.mymed

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that Room migrations actually preserve existing user data
 * instead of silently dropping tables (which happened before with
 * fallbackToDestructiveMigration and cost real users their medication list).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_preservesExistingReminders_andAddsDefaultSnoozeMinutes() {
        // Create a version-2 database and insert data as if a real user had it.
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                "INSERT INTO medications (id, name, dosage, notes, active) VALUES (1, 'Aspirin', '100mg', NULL, 1)"
            )
            execSQL(
                "INSERT INTO reminders (id, medicationId, hour, minute, enabled, daysOfWeek) " +
                    "VALUES (1, 1, 8, 0, 1, '1,2,3,4,5,6,7')"
            )
            close()
        }

        // Run the real migration path (2 -> 3).
        val db = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)

        // Existing medication must still be there, untouched.
        db.query("SELECT name, dosage FROM medications WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst()) { "Medication row was lost during migration!" }
            assert(cursor.getString(0) == "Aspirin")
            assert(cursor.getString(1) == "100mg")
        }

        // Existing reminder must still be there, with the new snoozeMinutes
        // column backfilled to the previous global default (10 min).
        db.query("SELECT hour, minute, snoozeMinutes FROM reminders WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst()) { "Reminder row was lost during migration!" }
            assert(cursor.getInt(0) == 8)
            assert(cursor.getInt(1) == 0)
            assert(cursor.getInt(2) == SnoozeManager.DEFAULT_SNOOZE_MINUTES) {
                "Expected default snoozeMinutes=${SnoozeManager.DEFAULT_SNOOZE_MINUTES}, got ${cursor.getInt(2)}"
            }
        }
    }
}

