package com.jotty.android.util

import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.local.OfflineNotesRepository

/** Title suffix used by the Jotty web app when cloning notes and checklists. */
const val CLONE_TITLE_SUFFIX = " (Copy)"

fun cloneTitle(title: String): String = "$title$CLONE_TITLE_SUFFIX"

fun normalizedCloneCategory(category: String): String =
    category.ifBlank { API_CATEGORY_UNCATEGORIZED }

suspend fun cloneNote(
    api: JottyApi,
    note: Note,
    targetCategory: String,
): Result<Note> =
    runCatching {
        val response =
            api.createNote(
                CreateNoteRequest(
                    title = cloneTitle(note.title),
                    content = note.content,
                    category = normalizedCloneCategory(targetCategory),
                ),
            )
        if (!response.success) {
            error("Create note failed")
        }
        response.data
    }

suspend fun cloneChecklist(
    api: JottyApi,
    checklist: Checklist,
    targetCategory: String,
): Result<Checklist> =
    runCatching {
        val created =
            api.createChecklist(
                CreateChecklistRequest(
                    title = cloneTitle(checklist.title),
                    category = normalizedCloneCategory(targetCategory),
                    type = apiChecklistTypeFrom(checklist.type),
                ),
            )
        if (!created.success) {
            error("Create checklist failed")
        }
        replayChecklistItemsToServer(api, created.data.id, checklist.items)
        created.data.copy(items = checklist.items)
    }

suspend fun cloneNoteOffline(
    repository: OfflineNotesRepository,
    note: Note,
    targetCategory: String,
): Result<Note> =
    repository.createNote(
        title = cloneTitle(note.title),
        content = note.content,
        category = normalizedCloneCategory(targetCategory),
    )

suspend fun cloneChecklistOffline(
    repository: OfflineChecklistsRepository,
    checklist: Checklist,
    targetCategory: String,
): Result<Checklist> =
    repository.recreateChecklistWithItems(
        checklist.copy(
            title = cloneTitle(checklist.title),
            category = normalizedCloneCategory(targetCategory),
        ),
    )
