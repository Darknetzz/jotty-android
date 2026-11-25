package com.jotty.android.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JottyDatabaseMigrationTest {
    @Test
    fun migration8To9_flagsAndLinksExistingLocalCopies() {
        val db = openV8Database()
        db.execSQL(
            """
            INSERT INTO notes (id, title, isDirty, isDeleted, isLocalOnly, instanceId) VALUES
              ('n1', 'Plan', 0, 0, 0, 'i1'),
              ('c1', 'Plan (Local copy)', 0, 0, 0, 'i1'),
              ('c2', 'Gone (Local copy)', 0, 0, 0, 'i1'),
              ('d1', 'Edited (Local copy)', 1, 0, 0, 'i1')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO checklists (id, title, isDirty, isDeleted, isLocalOnly, instanceId) VALUES
              ('l1', 'Groceries', 0, 0, 0, 'i1'),
              ('lc', 'Groceries (Local copy)', 0, 0, 0, 'i1')
            """.trimIndent(),
        )

        JottyDatabase.MIGRATION_8_9.migrate(db)

        assertEquals(
            mapOf(
                "n1" to (0 to null),
                "c1" to (1 to "n1"),
                "c2" to (1 to null),
                // Dirty rows are left alone: they may be real server notes with unsynced edits.
                "d1" to (0 to null),
            ),
            conflictColumns(db, "notes"),
        )
        assertEquals(
            mapOf("l1" to (0 to null), "lc" to (1 to "l1")),
            conflictColumns(db, "checklists"),
        )
        db.close()
    }

    private fun conflictColumns(
        db: SupportSQLiteDatabase,
        table: String,
    ): Map<String, Pair<Int, String?>> =
        buildMap {
            db.query("SELECT id, isConflictCopy, conflictOfId FROM $table").use { c ->
                while (c.moveToNext()) {
                    put(c.getString(0), c.getInt(1) to (if (c.isNull(2)) null else c.getString(2)))
                }
            }
        }

    /** Only the v8 columns the migration reads; the rest of the schema does not matter here. */
    private fun openV8Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            for (table in listOf("notes", "checklists")) {
                                db.execSQL(
                                    """
                                    CREATE TABLE $table (
                                        id TEXT NOT NULL PRIMARY KEY,
                                        title TEXT NOT NULL,
                                        isDirty INTEGER NOT NULL DEFAULT 0,
                                        isDeleted INTEGER NOT NULL DEFAULT 0,
                                        isLocalOnly INTEGER NOT NULL DEFAULT 0,
                                        instanceId TEXT NOT NULL
                                    )
                                    """.trimIndent(),
                                )
                            }
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }
}
