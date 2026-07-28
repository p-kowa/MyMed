package com.example.mymed

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MyMedication::class, Reminder::class, MedicationHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymed.db"
                )
                // Deletes DB when schema changes (useful during development)
                // Before release: implement real migrations
                .fallbackToDestructiveMigration(true)
                .build().also { INSTANCE = it }
            }
        }
    }
}
