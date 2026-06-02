package com.jotty.android.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledChangelogTest {
  private val sample =
      """
      # Changelog

      ## [dev-latest](https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest)

      ### Changed

      - Dev item

      ---

      ## [1.3.6] - 2026-05-27

      ### Added

      - Stable item

      ---

      ## [1.3.5] - 2026-05-24

      ### Fixed

      - Older item
      """.trimIndent()

    @Test
    fun `parse extracts section bodies by header key`() {
        val changelog = BundledChangelog.parse(sample)
        assertTrue(changelog.section("dev-latest")!!.contains("Dev item"))
        assertTrue(changelog.section("1.3.6")!!.contains("Stable item"))
        assertTrue(changelog.section("1.3.5")!!.contains("Older item"))
    }

    @Test
    fun `firstDevSectionBody returns first dev section`() {
        val changelog = BundledChangelog.parse(sample)
        assertEquals(changelog.section("dev-latest"), changelog.firstDevSectionBody())
    }

    @Test
    fun `sectionKeyForInstalled maps dev and stable version names`() {
        assertEquals("dev-latest", BundledChangelog.sectionKeyForInstalled("1.3.6-dev+8ad551b"))
        assertEquals("1.3.6", BundledChangelog.sectionKeyForInstalled("1.3.6"))
    }

    @Test
    fun `sectionKeyForRemote uses semver for stable and null for dev`() {
        assertEquals("1.3.7", BundledChangelog.sectionKeyForRemote("1.3.7", UpdateChannel.Stable))
        assertNull(BundledChangelog.sectionKeyForRemote("Dev 8ad551b", UpdateChannel.Dev))
    }

    @Test
    fun `resolveMarkdown prefers bundled section then fallback`() {
        val changelog = BundledChangelog.parse(sample)
        assertEquals(
            changelog.section("1.3.6"),
            BundledChangelog.resolveMarkdown(changelog, "1.3.6", useDevRollingSection = false, fallbackMarkdown = "GitHub"),
        )
        assertEquals(
            changelog.firstDevSectionBody(),
            BundledChangelog.resolveMarkdown(changelog, null, useDevRollingSection = true, fallbackMarkdown = null),
        )
        assertEquals(
            "GitHub only",
            BundledChangelog.resolveMarkdown(changelog, "9.9.9", useDevRollingSection = false, fallbackMarkdown = "GitHub only"),
        )
    }
}
