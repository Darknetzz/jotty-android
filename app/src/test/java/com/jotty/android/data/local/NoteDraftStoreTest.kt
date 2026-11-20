package com.jotty.android.data.local

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDraftStoreTest {
    private lateinit var directory: File
    private lateinit var store: NoteDraftStore
    private val draft = NoteDraft("Title", "Body\nwith \"quotes\"", "Work", "u1", 42L)

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("note-drafts").toFile()
        store = NoteDraftStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun save_thenLoad_returnsDraft() =
        runTest {
            store.save("../n1", draft)

            assertEquals(draft, store.load("../n1"))
            assertEquals(directory, directory.listFiles()?.single()?.parentFile)
        }

    @Test
    fun load_withoutDraft_returnsNull() =
        runTest {
            assertNull(store.load("missing"))
        }

    @Test
    fun delete_removesDraft() =
        runTest {
            store.save("n1", draft)

            store.delete("n1")

            assertNull(store.load("n1"))
        }

    @Test
    fun move_keepsDraftWithNewId() =
        runTest {
            store.save("local", draft)

            store.move("local", "server")

            assertNull(store.load("local"))
            assertEquals(draft, store.load("server"))
        }

    @Test
    fun load_unreadableDraft_dropsIt() =
        runTest {
            store.save("n1", draft)
            directory.listFiles()?.single()?.writeText("not json")

            assertNull(store.load("n1"))
            assertEquals(0, directory.listFiles()?.size)
        }
}
