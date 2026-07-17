package com.jotty.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncBackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: JottyDatabase
    private lateinit var repository: SyncBackupRepository
    private val instanceId = "test-instance"
    private val itemId = "note-1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, JottyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = SyncBackupRepository(database, instanceId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun save_keepsNewestBackupsAndPrunesOldest() =
        runTest {
            repeat(SyncBackupRepository.MAX_BACKUPS_PER_ITEM + 3) { i ->
                repository.save(
                    kind = SyncBackupKind.NOTE,
                    itemId = itemId,
                    direction = SyncBackupDirection.BEFORE_PULL_SERVER,
                    title = "Title $i",
                    payloadJson = """{"id":"$itemId","title":"Title $i","content":"body-$i"}""",
                )
                // Ensure createdAtEpochMs ordering is stable across rapid inserts.
                Thread.sleep(2)
            }

            val listed = repository.listForItem(SyncBackupKind.NOTE, itemId)
            assertEquals(SyncBackupRepository.MAX_BACKUPS_PER_ITEM, listed.size)
            assertTrue(listed.all { it.itemId == itemId })
            // Newest first
            assertEquals("Title ${SyncBackupRepository.MAX_BACKUPS_PER_ITEM + 2}", listed.first().title)
            assertEquals("Title 3", listed.last().title)
        }

    @Test
    fun save_doesNotPruneOtherItems() =
        runTest {
            repository.save(
                kind = SyncBackupKind.NOTE,
                itemId = "other",
                direction = SyncBackupDirection.BEFORE_PUSH_LOCAL,
                title = "Other",
                payloadJson = """{"id":"other","title":"Other","content":"x"}""",
            )
            repeat(SyncBackupRepository.MAX_BACKUPS_PER_ITEM + 1) { i ->
                repository.save(
                    kind = SyncBackupKind.NOTE,
                    itemId = itemId,
                    direction = SyncBackupDirection.BEFORE_PULL_SERVER,
                    title = "T$i",
                    payloadJson = """{"id":"$itemId","title":"T$i","content":"$i"}""",
                )
                Thread.sleep(2)
            }

            assertEquals(1, repository.listForItem(SyncBackupKind.NOTE, "other").size)
            assertEquals(
                SyncBackupRepository.MAX_BACKUPS_PER_ITEM,
                repository.listForItem(SyncBackupKind.NOTE, itemId).size,
            )
        }
}
