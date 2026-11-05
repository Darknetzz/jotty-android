package com.jotty.android.data.local

import android.content.Context
import com.jotty.android.util.AppLog
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Unsaved edits to a note, kept apart from the note itself until the user saves. */
data class NoteDraft(
    val title: String,
    val content: String,
    val category: String,
    /** The stored note's `updatedAt` when editing began; used to warn when the note has since changed. */
    val baseUpdatedAt: String,
    val savedAtEpochMs: Long,
)

/**
 * Autosaved note drafts on this device, one file per note. Drafts never reach the server or the
 * notes database, and are excluded from Android backups. Never pass plaintext of encrypted notes.
 */
class NoteDraftStore(
    private val directory: File,
) {
    suspend fun save(
        noteId: String,
        draft: NoteDraft,
    ) = locked {
        directory.mkdirs()
        val target = fileFor(noteId)
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(draft.toJson().toString())
        if (!temp.renameTo(target)) {
            temp.delete()
            AppLog.e(TAG, "Could not write note draft")
        }
    }

    suspend fun load(noteId: String): NoteDraft? =
        locked {
            val file = fileFor(noteId)
            if (!file.exists()) return@locked null
            try {
                file.readText().toNoteDraft()
            } catch (e: JSONException) {
                AppLog.e(TAG, "Dropping unreadable note draft", e)
                file.delete()
                null
            }
        }

    suspend fun delete(noteId: String) {
        locked { fileFor(noteId).delete() }
    }

    /** Keeps a draft attached to its note when a local-only note receives its server id. */
    suspend fun move(
        fromNoteId: String,
        toNoteId: String,
    ) {
        locked {
            val source = fileFor(fromNoteId)
            if (source.exists()) source.renameTo(fileFor(toNoteId))
        }
    }

    private fun fileFor(noteId: String) = File(directory, "${sha256(noteId)}.json")

    private suspend fun <T> locked(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            ioLock.withLock { block() }
        }

    companion object {
        private const val TAG = "NoteDraftStore"
        private val ioLock = Mutex()

        fun forInstance(
            context: Context,
            instanceId: String,
        ) = NoteDraftStore(File(File(context.noBackupFilesDir, "note_drafts"), sha256(instanceId)))

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        private fun NoteDraft.toJson() =
            JSONObject()
                .put("title", title)
                .put("content", content)
                .put("category", category)
                .put("baseUpdatedAt", baseUpdatedAt)
                .put("savedAtEpochMs", savedAtEpochMs)

        private fun String.toNoteDraft(): NoteDraft {
            val json = JSONObject(this)
            return NoteDraft(
                title = json.getString("title"),
                content = json.getString("content"),
                category = json.getString("category"),
                baseUpdatedAt = json.getString("baseUpdatedAt"),
                savedAtEpochMs = json.getLong("savedAtEpochMs"),
            )
        }
    }
}
