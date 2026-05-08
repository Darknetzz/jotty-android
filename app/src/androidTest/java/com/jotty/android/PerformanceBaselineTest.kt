package com.jotty.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class PerformanceBaselineTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun baseline_coldStartToFirstFrame() {
        val elapsedMs = measureTimeMillis {
            composeRule.waitForIdle()
            composeRule.onRoot().assertIsDisplayed()
        }
        println("PERF_BASELINE startup_ms=$elapsedMs")
        // Loose guardrail to catch pathological regressions while avoiding flaky CI.
        assertTrue("Startup took too long: ${elapsedMs}ms", elapsedMs < 15000)
    }

    @Test
    fun baseline_openNotesTab() {
        val notesLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.nav_notes)
        if (!composeRule.hasNodeWithText(notesLabel)) {
            println("PERF_BASELINE notes_tab_open_ms=SKIPPED (notes tab not visible on initial screen)")
            composeRule.onRoot().assertIsDisplayed()
            return
        }

        val elapsedMs = measureTimeMillis {
            composeRule.onNodeWithText(notesLabel).assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(notesLabel).assertIsDisplayed()
        }
        println("PERF_BASELINE notes_tab_open_ms=$elapsedMs")
        assertTrue("Notes tab open took too long: ${elapsedMs}ms", elapsedMs < 15000)
    }

    private fun ComposeTestRule.hasNodeWithText(text: String): Boolean {
        waitForIdle()
        return onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
