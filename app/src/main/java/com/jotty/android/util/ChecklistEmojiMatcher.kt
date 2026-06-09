package com.jotty.android.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ChecklistEmojiMatcher {
    private const val ASSET_NAME = "checklist_emojis.json"

    @Volatile
    private var dictionary: Map<String, String>? = null

    fun ensureLoaded(context: Context) {
        if (dictionary != null) return
        synchronized(this) {
            if (dictionary != null) return
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            dictionary = Gson().fromJson(json, type)
        }
    }

    fun findMatchingEmoji(
        context: Context,
        text: String,
    ): String {
        ensureLoaded(context)
        return findMatchingEmoji(text, dictionary.orEmpty())
    }

    fun findMatchingEmoji(
        text: String,
        dictionary: Map<String, String>,
    ): String {
        val words = text.split(Regex("\\s+"))
        for (word in words) {
            val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")
            if (cleanWord.isEmpty()) continue
            dictionary[cleanWord]?.let { return it }
            val singular = toSingular(cleanWord)
            if (singular != cleanWord) {
                dictionary[singular]?.let { return it }
            }
        }
        return ""
    }

    private fun toSingular(word: String): String =
        when {
            word.endsWith("ies") -> word.dropLast(3) + "y"
            word.endsWith("es") -> word.dropLast(2)
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }

    internal fun resetForTests() {
        dictionary = null
    }

    internal fun setDictionaryForTests(map: Map<String, String>?) {
        dictionary = map
    }
}
