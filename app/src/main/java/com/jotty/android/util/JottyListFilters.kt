package com.jotty.android.util

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.Note

fun filterNotesForCategory(
    notes: List<Note>,
    category: String?,
): List<Note> =
    when {
        category == null -> notes.filterNot { it.isArchived() }
        category.equals(JOTTY_ARCHIVE_CATEGORY, ignoreCase = true) -> notes.filter { it.isArchived() }
        else -> notes.filter { it.category == category }
    }

fun filterChecklistsForCategory(
    lists: List<Checklist>,
    category: String?,
): List<Checklist> =
    when {
        category == null -> lists.filterNot { it.isArchived() }
        category.equals(JOTTY_ARCHIVE_CATEGORY, ignoreCase = true) -> lists.filter { it.isArchived() }
        else -> lists.filter { it.category == category }
    }
