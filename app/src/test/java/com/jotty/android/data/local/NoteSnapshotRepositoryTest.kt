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
class NoteSnapshotRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: JottyDatabase
    private lateinit var repository: NoteSnapshotRepository
    private val instanceId = "test-instance"
    private val noteId = "note-1"

    private val encryptedV1 =
        """
        |---
        |uuid: abc-123
        |title: Secret Note
        |encrypted: true
        |encryptionMethod: xchacha
        |---
        |{"alg":"xchacha20","salt":"dGVzdA==","nonce":"dGVzdA==","data":"dGVzdA=="}
        """.trimMargin()

    private val encryptedV2 =
        """
        |---
        |uuid: abc-123
        |title: Secret Note
        |encrypted: true
        |encryptionMethod: xchacha
        |---
        |{"alg":"xchacha20","salt":"dGVzdA==","nonce":"dGVzdA==","data":"dGVzdDI="}
        """.trimMargin()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, JottyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = NoteSnapshotRepository(database, instanceId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveBeforeUpdate_skipsWhenDisabled() =
        runTest {
            repository.saveBeforeUpdate(noteId, "Title", "Hello world", enabled = false)
            assertTrue(repository.listForNote(noteId).isEmpty())
        }

    @Test
    fun saveBeforeUpdate_storesPlaintext() =
        runTest {
            repository.saveBeforeUpdate(noteId, "Title", "Hello world")
            assertEquals(1, repository.listForNote(noteId).size)
            assertEquals("Hello world", repository.listForNote(noteId).first().content)
        }

    @Test
    fun saveBeforeUpdate_skipsDuplicateConsecutiveContent() =
        runTest {
            repository.saveBeforeUpdate(noteId, "Title", encryptedV1)
            repository.saveBeforeUpdate(noteId, "Title", encryptedV1)
            assertEquals(1, repository.listForNote(noteId).size)
        }

    @Test
    fun saveBeforeUpdate_keepsDistinctVersionsAndPrunesOldest() =
        runTest {
            repeat(NoteSnapshotRepository.MAX_SNAPSHOTS_PER_NOTE + 2) { i ->
                val body =
                    """{"alg":"xchacha20","salt":"dGVzdA==","nonce":"dGVzdA==","data":"data$i"}"""
                val content =
                    """
                    |---
                    |encrypted: true
                    |encryptionMethod: xchacha
                    |---
                    |$body
                    """.trimMargin()
                repository.saveBeforeUpdate(noteId, "Title", content)
            }
            val snapshots = repository.listForNote(noteId)
            assertEquals(NoteSnapshotRepository.MAX_SNAPSHOTS_PER_NOTE, snapshots.size)
        }

    @Test
    fun saveBeforeUpdate_storesDistinctCiphertextVersions() =
        runTest {
            repository.saveBeforeUpdate(noteId, "Title", encryptedV1)
            repository.saveBeforeUpdate(noteId, "Title", encryptedV2)
            val snapshots = repository.listForNote(noteId)
            assertEquals(2, snapshots.size)
            assertEquals(encryptedV2, snapshots.first().content)
            assertEquals(encryptedV1, snapshots.last().content)
        }
}
