package com.jotty.android.ui.checklists

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jotty.android.util.ChecklistEmojiMatcher

fun checklistCleanText(raw: String): String = raw.split(" | metadata:")[0].trim()

fun checklistDisplayText(
    raw: String,
    showEmojis: Boolean,
    dictionary: Map<String, String>,
): String {
    val clean = checklistCleanText(raw)
    if (!showEmojis) return clean
    val emoji = ChecklistEmojiMatcher.findMatchingEmoji(clean, dictionary)
    return if (emoji.isNotEmpty()) "$emoji  $clean" else clean
}

fun checklistDisplayText(
    raw: String,
    showEmojis: Boolean,
    context: Context,
): String {
    val clean = checklistCleanText(raw)
    if (!showEmojis) return clean
    val emoji = ChecklistEmojiMatcher.findMatchingEmoji(context, clean)
    return if (emoji.isNotEmpty()) "$emoji  $clean" else clean
}

@Composable
fun checklistDisplayText(
    raw: String,
    showEmojis: Boolean,
): String {
    val context = LocalContext.current
    return checklistDisplayText(raw, showEmojis, context)
}
