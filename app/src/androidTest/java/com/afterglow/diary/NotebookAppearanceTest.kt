package com.afterglow.diary

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookAppearanceTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun ready() { app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() } }
    private fun settings() {
        app.onNodeWithContentDescription("Notebook options").performClick()
        app.onNodeWithText("Settings").performClick()
    }
    private fun click(tag: String) = app.onNodeWithTag(tag).performScrollTo().performClick()
    private fun back() = app.onNodeWithContentDescription("Back to settings").performClick()
    private fun capture(name: String, dialog: Boolean = true) {
        if (InstrumentationRegistry.getArguments().getString("captureAppearance") != "true") return
        app.waitForIdle()
        val image = (if (dialog) app.onNode(isDialog()) else app.onRoot()).captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("screenshots")!!.apply { mkdirs() }
        java.io.File(dir, "$name-1.7.0.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun rotate(requested: Int, expected: Int) {
        app.activityRule.scenario.onActivity { it.requestedOrientation = requested }
        val deadline = SystemClock.uptimeMillis() + 10_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            app.activityRule.scenario.onActivity { activity ->
                ready = activity.resources.configuration.orientation == expected && activity.window.decorView.isAttachedToWindow
                if (ready) activity.window.decorView.postInvalidateOnAnimation()
            }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue(ready); app.waitForIdle()
    }

    @Test fun choicesPersistResetOnlyAppearanceAndKeepTheDream() {
        ready()
        val body = "Čujem šum mora.\n\nThe water carried the stars,\nand I knew I was dreaming.\n\nEverything felt impossibly quiet."
        var original = NotebookPreferences()
        var dreamId = ""
        app.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
            original = model.state.value.preferences
            model.preferences(NotebookPreferences(dictationLanguage = "en-US", prompts = false))
            model.addDream(); model.editTitle("The sea of stars"); model.editBody(body)
            dreamId = model.state.value.current!!.id
        }
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            settings(); click("settings-font"); click("settings-font-Journal")
            capture("writing-fonts-portrait")
            click("settings-size-larger"); click("settings-size-larger")
            click("settings-spacing"); app.onNodeWithTag("settings-spacing-Airy").performClick()
            click("settings-match-title"); click("settings-ink-Plum")
            capture("writing-options-portrait")
            back(); click("settings-paper"); click("settings-tone-Rose"); click("settings-paper-Plain")
            capture("paper-options-portrait")
            back(); click("settings-marker"); app.onNodeWithTag("settings-marker-Lavender").performClick()
            app.onNodeWithContentDescription("Close").performClick()
            app.activityRule.scenario.recreate(); ready()
            app.activityRule.scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
                val p = model.state.value.preferences
                assertEquals(NotebookPreferences(dictationLanguage = "en-US", prompts = false, font = "Journal", textSize = 22,
                    spacing = "Airy", matchTitle = true, inkColor = "Plum", paperTone = "Rose", ruling = "Plain", cover = "Lavender"), p)
                assertEquals(p, NotebookPreferencesStore(activity).load())
                assertEquals(body, model.state.value.entries.first { it.id == dreamId }.body)
                assertEquals("The sea of stars", model.state.value.current!!.title)
            }
            settings(); click("settings-paper"); click("settings-paper-Lined"); back()
            click("settings-font"); click("settings-spacing"); app.onNodeWithTag("settings-spacing-Comfortable").performClick()
            app.onNodeWithTag("settings-paper-preview").performScrollTo()
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            capture("writing-fonts-landscape")
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            app.onNodeWithContentDescription("Close").performClick()
            capture("custom-notebook-portrait", dialog = false)
            app.onNodeWithTag("lucid-marker").performClick()
            app.waitForIdle()
            capture("custom-lucid-portrait", dialog = false)
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            capture("custom-lucid-landscape", dialog = false)
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            settings(); click("night-paper"); click("settings-font")
            capture("writing-night-portrait")
            back(); click("settings-reset")
            app.onNodeWithText("Keep my style").performClick()
            app.activityRule.scenario.onActivity { assertEquals("Journal", ViewModelProvider(it)[DiaryViewModel::class.java].state.value.preferences.font) }
            click("settings-reset")
            app.onAllNodesWithText("Reset appearance").onLast().performClick()
            app.activityRule.scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
                assertEquals(NotebookPreferences(dictationLanguage = "en-US", prompts = false), model.state.value.preferences)
                assertEquals(body, model.state.value.entries.first { it.id == dreamId }.body)
            }
            app.onNodeWithTag("settings-paper-preview").performScrollTo()
            capture("settings-portrait")
        } finally {
            app.activityRule.scenario.onActivity { ViewModelProvider(it)[DiaryViewModel::class.java].preferences(original) }
        }
    }
}
