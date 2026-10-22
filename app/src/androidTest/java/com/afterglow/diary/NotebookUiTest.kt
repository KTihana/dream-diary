package com.afterglow.diary

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookUiTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun editingLucidityNewPagesAndRotationKeepTheNotebookIntact() {
        app.waitUntil(10_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        // Start on a new page so the flow also works when the device already has dreams.
        app.onNodeWithContentDescription("A new dream").performClick()
        assertPaperFillsWindow()
        app.onNodeWithTag("dream-title").performTextReplacement("The starry sea")
        app.onNodeWithTag("dream-body").performTextReplacement("I knew I was dreaming. The water shone like stars.")
        app.onNodeWithContentDescription("Done writing").performClick()
        app.onNodeWithTag("lucid-marker").performClick().assertIsOn()
        assertPaperColor(Color(0xFFF0E8F8))
        app.onNodeWithTag("lucid-marker").performClick().assertIsOff()
        assertPaperColor(Color(0xFFFCFAF4))
        app.onNodeWithTag("lucid-marker").performClick().assertIsOn()
        app.onNodeWithContentDescription("A new dream").performClick()
        app.onNodeWithTag("dream-title").performTextReplacement("A second dream")
        app.onNodeWithContentDescription("Done writing").performClick()
        app.onNodeWithTag("lucid-marker").performClick().performClick().assertIsOff()
        assertPaperColor(Color(0xFFFCFAF4))
        app.onNodeWithContentDescription("Previous page").performClick()
        app.waitForIdle()
        app.onNodeWithTag("dream-title").assertTextEquals("The starry sea")
        app.onNodeWithTag("lucid-marker").assertIsOn()
        assertPaperColor(Color(0xFFF0E8F8))
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        app.onNodeWithTag("notebook-spread").assertExists()
        assertPaperColor(Color(0xFFF0E8F8))
        app.onNodeWithTag("dream-title").assertTextEquals("The starry sea")
        assertPaperFillsWindow()
        app.onNodeWithTag("dream-body").assertTextEquals("I knew I was dreaming. The water shone like stars.")
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        app.onNodeWithTag("notebook-portrait").assertExists()
        assertPaperFillsWindow()
        app.onNodeWithTag("dream-title").assertTextEquals("The starry sea")
        app.activityRule.scenario.recreate()
        app.onNodeWithTag("dream-title").assertTextEquals("The starry sea")
        app.onNodeWithTag("lucid-marker").assertIsOn()
        app.onNodeWithText("I WOKE UP FEELING").assertDoesNotExist()
        app.onNodeWithText("a new beginning").assertDoesNotExist()
        app.onNodeWithText("YOUR DREAM NOTEBOOK").assertDoesNotExist()
    }
    private fun assertPaperColor(expected: Color) {
        val pixels = app.onNodeWithTag("fullscreen-paper").captureToImage().toPixelMap()
        // The blank outer margin is part of the page, clear of the writing and control layer.
        val actual = pixels[10, pixels.height / 2]
        org.junit.Assert.assertEquals("Paper red", expected.red, actual.red, .015f)
        org.junit.Assert.assertEquals("Paper green", expected.green, actual.green, .015f)
        org.junit.Assert.assertEquals("Paper blue", expected.blue, actual.blue, .015f)
    }
    private fun rotate(requested: Int, expected: Int) {
        app.activityRule.scenario.onActivity { it.requestedOrientation = requested }
        val deadline = SystemClock.uptimeMillis() + 10_000
        var ready = false
        // Poll Android configuration without asking Compose to synchronize with a detached root.
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            app.activityRule.scenario.onActivity { activity ->
                ready = activity.resources.configuration.orientation == expected &&
                    activity.window.decorView.isAttachedToWindow
                if (ready) activity.window.decorView.postInvalidateOnAnimation()
            }
            if (!ready) SystemClock.sleep(50)
        }
        org.junit.Assert.assertTrue("Rotation did not finish", ready)
        app.waitForIdle()
    }
    private fun assertPaperFillsWindow() {
        val window = app.onRoot().fetchSemanticsNode().boundsInRoot
        val paper = app.onNodeWithTag("fullscreen-paper").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(window.width, paper.width, 1f)
        org.junit.Assert.assertEquals(window.height, paper.height, 1f)
        if (app.onAllNodesWithTag("notebook-spread").fetchSemanticsNodes().isNotEmpty()) {
            val left = app.onNodeWithTag("left-leaf").fetchSemanticsNode().boundsInRoot
            val right = app.onNodeWithTag("right-leaf").fetchSemanticsNode().boundsInRoot
            // Check the actual leaves, not just a full-width background behind inset content.
            org.junit.Assert.assertEquals(window.left, left.left, 1f)
            org.junit.Assert.assertEquals(window.center.x, left.right, 1f)
            org.junit.Assert.assertEquals(window.center.x, right.left, 1f)
            org.junit.Assert.assertEquals(window.right, right.right, 1f)
        } else {
            val title = app.onNodeWithTag("dream-title").fetchSemanticsNode().boundsInRoot
            val body = app.onNodeWithTag("dream-body").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Portrait title should use the upper page", title.top < window.height * .25f)
            org.junit.Assert.assertTrue("Portrait writing should use most of the width", body.width >= window.width * .82f)
        }
        app.onNodeWithContentDescription("Previous page").assertIsDisplayed()
        app.onNodeWithContentDescription("Next page").assertIsDisplayed()
    }
}
