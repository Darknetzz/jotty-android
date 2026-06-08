package com.jotty.android.util

import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.Note

/** Category name Jotty uses for archived notes and checklists (see `ARCHIVED_DIR_NAME` upstream). */
const val JOTTY_ARCHIVE_CATEGORY = "Archive"

fun isArchivedCategory(category: String?): Boolean =
    category?.trim()?.equals(JOTTY_ARCHIVE_CATEGORY, ignoreCase = true) == true

fun Note.isArchived(): Boolean = isArchivedCategory(category)

fun Checklist.isArchived(): Boolean = isArchivedCategory(category)

/** Category to restore when unarchiving if the prior category is unknown. */
fun defaultUnarchiveCategory(): String = API_CATEGORY_UNCATEGORIZED
