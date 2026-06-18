package com.jotty.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for offline storage.
 * Database version: 7
 *
 * v1 → v2: add isLocalOnly column to notes.
 * v2 → v3: add checklists table for offline checklist support.
 * v3 → v4: add index on notes.instanceId for list queries.
 * v4 → v5: add originalCategory column to notes (for category moves on sync).
 * v5 → v6: encrypted_note_snapshots table for local note backups before save.
 * v6 → v7: rename encrypted_note_snapshots → note_snapshots (all note types).
 */
@Database(
    entities = [NoteEntity::class, ChecklistEntity::class, NoteSnapshotEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class JottyDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun checklistDao(): ChecklistDao

    abstract fun noteSnapshotDao(): NoteSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: JottyDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE notes ADD COLUMN isLocalOnly INTEGER NOT NULL DEFAULT 0",
                    )
                    // Best-effort rescue of pre-existing local-only notes: if a dirty,
                    // non-deleted note has createdAt == updatedAt it was almost certainly
                    // created offline and never synced. Mark it so syncNote() calls
                    // createNote instead of updateNote (which would 404 on an unknown ID).
                    db.execSQL(
                        "UPDATE notes SET isLocalOnly = 1 WHERE isDirty = 1 AND isDeleted = 0 AND createdAt = updatedAt",
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS checklists (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            category TEXT NOT NULL,
                            type TEXT NOT NULL,
                            itemsJson TEXT NOT NULL DEFAULT '[]',
                            pendingOpsJson TEXT NOT NULL DEFAULT '[]',
                            createdAt TEXT NOT NULL,
                            updatedAt TEXT NOT NULL,
                            isDirty INTEGER NOT NULL DEFAULT 0,
                            isDeleted INTEGER NOT NULL DEFAULT 0,
                            instanceId TEXT NOT NULL,
                            isLocalOnly INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                    // Index mirrors @Entity(indices=[Index("instanceId")]) for DAO filter queries.
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_checklists_instanceId ON checklists (instanceId)")
                }
            }

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_instanceId ON notes (instanceId)")
                }
            }

        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN originalCategory TEXT DEFAULT NULL")
                }
            }

        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS encrypted_note_snapshots (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            noteId TEXT NOT NULL,
                            instanceId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            content TEXT NOT NULL,
                            savedAtEpochMs INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_encrypted_note_snapshots_noteId ON encrypted_note_snapshots (noteId)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_encrypted_note_snapshots_instanceId ON encrypted_note_snapshots (instanceId)",
                    )
                }
            }

        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE encrypted_note_snapshots RENAME TO note_snapshots",
                    )
                }
            }

        fun getDatabase(context: Context): JottyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        JottyDatabase::class.java,
                        "jotty_database",
                    )
                        .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                        )
                        .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
