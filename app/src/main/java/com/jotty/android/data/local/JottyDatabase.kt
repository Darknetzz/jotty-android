package com.jotty.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for offline note storage.
 * Database version: 1
 */
@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JottyDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: JottyDatabase? = null

        /**
         * Get or create the database instance.
         */
        fun getDatabase(context: Context): JottyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JottyDatabase::class.java,
                    "jotty_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
