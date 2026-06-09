package com.jotty.android.util

import com.jotty.android.ui.checklists.checklistDisplayText
import org.junit.Assert.assertEquals
import org.junit.Test
import com.jotty.android.ui.checklists.checklistDisplayText as displayTextWithDict

class ChecklistEmojiMatcherTest {
    private val dictionary =
        mapOf(
            "watermelon" to "🍉",
            "milk" to "🥛",
            "egg" to "🥚",
            "pill" to "💊",
        )

    @Test
    fun findMatchingEmoji_watermelon() {
        assertEquals("🍉", ChecklistEmojiMatcher.findMatchingEmoji("Watermelon", dictionary))
    }

    @Test
    fun findMatchingEmoji_milk() {
        assertEquals("🥛", ChecklistEmojiMatcher.findMatchingEmoji("Milk", dictionary))
    }

    @Test
    fun findMatchingEmoji_egg() {
        assertEquals("🥚", ChecklistEmojiMatcher.findMatchingEmoji("Egg", dictionary))
    }

    @Test
    fun findMatchingEmoji_pluralEggs() {
        assertEquals("🥚", ChecklistEmojiMatcher.findMatchingEmoji("Eggs", dictionary))
    }

    @Test
    fun findMatchingEmoji_noMatch() {
        assertEquals("", ChecklistEmojiMatcher.findMatchingEmoji("Fruktmüsli", dictionary))
    }

    @Test
    fun displayText_manualLeadingEmoji_noDoublePrefix() {
        assertEquals(
            "💊 Paracet",
            displayTextWithDict("💊 Paracet", showEmojis = true, dictionary),
        )
    }

    @Test
    fun displayText_disabled_returnsCleanText() {
        assertEquals("Watermelon", displayTextWithDict("Watermelon", showEmojis = false, dictionary))
    }

    @Test
    fun displayText_enabled_prependsMatch() {
        assertEquals(
            "🍉  Watermelon",
            displayTextWithDict("Watermelon", showEmojis = true, dictionary),
        )
    }
}
