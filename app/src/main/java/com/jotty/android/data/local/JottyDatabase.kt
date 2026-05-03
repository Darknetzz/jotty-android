package com.jotty.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for offline note storage.
 * Database version: 2
 *
 * v1 → v2: add isLocalOnly column (default 0 = false) to distinguish notes
 * created offline-only from notes that exist on the server.
 */
@Database(
    entities = [NoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class JottyDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: JottyDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN isLocalOnly INTEGER NOT NULL DEFAULT 0"
                )
                // Best-effort rescue of pre-existing local-only notes: if a dirty,
                // non-deleted note has createdAt == updatedAt it was almost certainly
                // created offline and never synced. Mark it so syncNote() calls
                // createNote instead of updateNote (which would 404 on an unknown ID).
                db.execSQL(
                    "UPDATE notes SET isLocalOnly = 1 WHERE isDirty = 1 AND isDeleted = 0 AND createdAt = updatedAt"
                )
            }
        }

        fun getDatabase(context: Context): JottyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JottyDatabase::class.java,
                    "jotty_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
