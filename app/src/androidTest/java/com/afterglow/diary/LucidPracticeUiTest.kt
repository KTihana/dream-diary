package com.afterglow.diary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LucidPracticeUiTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun ready() = app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
    private fun openPractice(route: String) {
        if (app.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isNotEmpty()) {
            app.onNodeWithContentDescription("Close").performClick()
        }
        app.onNodeWithText("Practice").performClick()
        if (route != "home") app.onNodeWithTag("practice-open-$route").performScrollTo().performClick()
        app.waitForIdle()
    }

    @Test fun guidesLogOnceAndAnUnrecalledMorningStaysUnknown() {
        ready()
        val store = LucidPracticeStore(app.activity)
        val backup = store.load()
        try {
            app.runOnIdle { store.update { LucidPracticeData() } }
            openPractice("check")
            app.onNodeWithTag("start-practice-guide").performScrollTo().performClick()
            repeat(3) { app.onNodeWithText("Next step").performScrollTo().performClick() }
            app.onNodeWithText("Finish practice").performScrollTo().performClick()
            app.onNodeWithText("Practice complete").assertExists()
            assertEquals(1, store.load().events.count { it.method == PracticeMethod.RealityCheck })
            app.activityRule.scenario.recreate()
            app.waitForIdle()
            assertEquals(1, store.load().events.count { it.method == PracticeMethod.RealityCheck })
            openPractice("morning")
            app.onNodeWithText("No dream recalled").performScrollTo().performClick().assertIsSelected()
            app.onNodeWithText("Yes").assertIsNotEnabled()
            assertNull(store.load().night(LocalDate.now()).lucid)
            app.activityRule.scenario.recreate()
            app.onNodeWithText("No dream recalled").assertIsSelected()
        } finally {
            app.runOnIdle { store.update { backup } }
            PracticeAlarms.reschedule(app.activity)
        }
    }

    @Test fun practiceTabTipsSignsAndIntentionsAreReachable() {
        ready()
        val store = LucidPracticeStore(app.activity)
        val backup = store.load()
        try {
            app.runOnIdle { store.update { LucidPracticeData() } }
            app.onNodeWithContentDescription("All dreams").performClick()
            app.onNodeWithTag("library-tab-3").performClick()
            app.onNodeWithTag("lucid-home").assertExists()
            app.onNodeWithTag("practice-open-tips").performScrollTo().performClick()
            app.onNodeWithText("Tips & tricks").assertExists()
            app.onNodeWithTag("lucid-tips-search").performTextReplacement("MILD")
            app.onNodeWithText("MILD is more than repeating words").assertExists()
            openPractice("home")
            app.onNodeWithTag("practice-open-signs").performScrollTo().performClick()
            app.onNodeWithTag("lucid-custom-sign").performTextReplacement("paper birds")
            app.onNodeWithText("Keep this sign").performScrollTo().performClick()
            assertTrue("paper birds" in store.load().settings.signs)
            openPractice("home")
            app.onNodeWithTag("practice-open-intention").performScrollTo().performClick()
            app.onNodeWithTag("lucid-intention").performTextReplacement("Recognize the paper birds")
            app.onNodeWithTag("lucid-goal").performTextReplacement("Look at the sky")
            app.activityRule.scenario.onActivity { activity ->
                val data = ViewModelProvider(activity)[DiaryViewModel::class.java].lucid.state.value.data!!
                assertEquals("Recognize the paper birds", data.night(upcomingPracticeNight()).intention)
                assertEquals("Look at the sky", data.night(upcomingPracticeNight()).goal)
            }
        } finally {
            app.runOnIdle { store.update { backup } }
            PracticeAlarms.reschedule(app.activity)
        }
    }
}
