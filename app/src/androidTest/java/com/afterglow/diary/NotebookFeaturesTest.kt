package com.afterglow.diary

import android.Manifest
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookFeaturesTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun ready() {
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, android.content.res.Configuration.ORIENTATION_PORTRAIT)
        app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
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
        assertTrue(ready)
        app.waitForIdle()
    }
    private fun option(text: String) {
        app.onNodeWithContentDescription("Notebook options").performClick()
        app.onNodeWithText(text).performClick()
    }
    @Test fun searchDictionaryBookmarksContinuationAndSketchSurviveRecreation() {
        ready()
        captureLibraryIfRequested()
        app.onNodeWithContentDescription("A new dream").performClick()
        val title = "Paper ship " + java.util.UUID.randomUUID().toString().take(8)
        app.onNodeWithTag("dream-title").performTextReplacement(title)
        app.onNodeWithTag("dream-body").performTextReplacement("A little paper ship sailed through the blue door.")
        app.onNodeWithContentDescription("Done writing").performClick()
        app.onNodeWithTag("lucid-marker").performClick()
        option("Dream details")
        app.onNodeWithTag("detail-symbols").performTextReplacement("river, door")
        app.onNodeWithText("Keep notes").performScrollTo().performClick()
        option("Bookmark this dream")
        app.onNodeWithContentDescription("All dreams").performClick()
        app.onNodeWithTag("dream-search").performTextReplacement("\"blue door\" river")
        app.onNodeWithTag("dream-search").performImeAction()
        app.waitUntil(10_000) { app.onAllNodesWithText("Symbols").fetchSemanticsNodes().isNotEmpty() }
        SystemClock.sleep(350)
        app.waitForIdle()
        app.onNodeWithText("Bookmarks").performScrollTo().performClick()
        app.onNodeWithTag("filter-Lucid").performScrollTo().performClick()
        app.onNode(hasText(title) and hasAnyAncestor(hasTestTag("dream-index"))).assertIsDisplayed()
        app.onNodeWithTag("filter-Non-lucid").performScrollTo().performClick()
        app.onNode(hasText(title) and hasAnyAncestor(hasTestTag("dream-index"))).assertDoesNotExist()
        app.onNodeWithText("Symbols", useUnmergedTree = true).performClick()
        app.onNodeWithText("+ Symbol").performClick()
        app.onNodeWithTag("symbol-name").performTextReplacement("river")
        app.onNodeWithTag("symbol-meaning").performTextReplacement("Finding my own way home")
        app.onNodeWithText("Keep meaning").performClick()
        app.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Finding my own way home"))
        app.onNodeWithText("Finding my own way home").assertIsDisplayed()
        app.onNodeWithContentDescription("Close").performClick()
        option("Continue on a new page")
        app.onNodeWithTag("dream-body").performTextReplacement("The second sheet is still the same dream.")
        app.onNodeWithContentDescription("Done writing").performClick()
        app.onNodeWithContentDescription("Previous page").performClick()
        app.onNodeWithTag("dream-body").assertTextEquals("A little paper ship sailed through the blue door.")
        app.onNodeWithContentDescription("Next page").performClick()
        app.onNodeWithTag("dream-body").assertTextEquals("The second sheet is still the same dream.")
        app.onNodeWithTag("draw-mode").performClick()
        app.onNodeWithTag("page-drawing-canvas").performTouchInput { swipe(centerLeft, centerRight, 400) }
        app.onNodeWithTag("write-mode").performClick()
        app.activityRule.scenario.recreate()
        ready()
        app.activityRule.scenario.onActivity {
            val current = ViewModelProvider(it)[DiaryViewModel::class.java].state.value.current!!
            assertTrue(current.bookmarked)
            assertEquals(listOf("river", "door"), current.symbols)
            assertEquals("A little paper ship sailed through the blue door.\u000CThe second sheet is still the same dream.", current.body)
            assertEquals(1, current.drawings.single().strokes.size)
        }
    }

    /** Optional native captures, using the existing navigation flow and emulator-only sample entries. */
    private fun captureLibraryIfRequested() {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureLibrary") != "true") return
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("screenshots")!!.apply { mkdirs() }
        fun capture(name: String) {
            app.waitForIdle()
            val image = app.onNode(isDialog()).captureToImage()
            val bitmap = image.asAndroidBitmap()
            java.io.File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        app.onNodeWithContentDescription("All dreams").performClick()
        capture("your-dreams-portrait-1.5.1.png")
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        capture("your-dreams-landscape-1.5.1.png")
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, android.content.res.Configuration.ORIENTATION_PORTRAIT)
        app.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
            model.preferences(model.state.value.preferences.copy(night = true))
        }
        capture("your-dreams-night-1.5.1.png")
        app.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
            model.preferences(model.state.value.preferences.copy(night = false))
        }
        app.onNodeWithContentDescription("Close").performClick()
    }

    @Test fun quickCaptureIntentStartsABlankDream() {
        ready()
        app.onNodeWithTag("dream-body").performTextReplacement("Remember this before a quick capture.")
        app.onNodeWithContentDescription("Done writing").performClick()
        var count = 0
        app.activityRule.scenario.onActivity { count = ViewModelProvider(it)[DiaryViewModel::class.java].state.value.entries.size }
        app.activityRule.scenario.onActivity { activity ->
            activity.startActivity(android.content.Intent(activity.intent)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("capture", "write"))
        }
        app.waitUntil(10_000) {
            ViewModelProvider(app.activity)[DiaryViewModel::class.java].state.value.entries.size == count + 1
        }
        app.waitUntil(10_000) {
            app.onAllNodesWithTag("dream-body").fetchSemanticsNodes().singleOrNull()?.config
                ?.get(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text == ""
        }
        app.onNodeWithTag("dream-body").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        app.onNodeWithTag("dream-title").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun automaticSheetsKeepAllTextInOrder() {
        ready()
        app.onNodeWithContentDescription("A new dream").performClick()
        val text = (1..28).joinToString("\n") { "Line $it: I followed the river beneath the stars." }
        app.onNodeWithTag("dream-body").performTextReplacement(text)
        val visibleText = app.onNodeWithTag("dream-body").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        val insertionAt = text.indexOf(visibleText) + visibleText.length
        val expected = text.substring(0, insertionAt) + " And then I woke up." + text.substring(insertionAt)
        app.onNodeWithTag("dream-body").performTextInputSelection(androidx.compose.ui.text.TextRange(visibleText.length))
        app.onNodeWithTag("dream-body").performTextInput(" And then I woke up.")
        app.onNodeWithContentDescription("Done writing").performClick()
        waitForKeyboardToClose()
        assertSheetsEqual(expected)
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        assertSheetsEqual(expected)
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, android.content.res.Configuration.ORIENTATION_PORTRAIT)
    }
    private fun assertSheetsEqual(expected: String) {
        while (app.onNodeWithTag("sheet-counter").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .none { it.contains("sheet 1 of") }) app.onNodeWithContentDescription("Previous page").performClick()
        val pages = mutableListOf<String>()
        repeat(30) {
            val field = app.onNodeWithTag("dream-body").fetchSemanticsNode()
            pages += field.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            val next = app.onNodeWithContentDescription("Next page").fetchSemanticsNode()
            if (next.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)) {
                assertTrue(pages.size > 1)
                assertEquals(expected, pages.joinToString(""))
                return
            }
            app.onNodeWithContentDescription("Next page").performClick()
        }
        fail("The dream should fit within thirty sheets")
    }

    private fun waitForKeyboardToClose() {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var visible = true
        while (visible && SystemClock.uptimeMillis() < deadline) {
            app.activityRule.scenario.onActivity { activity ->
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                visible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true ||
                    (insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0) > 0
                activity.window.decorView.postInvalidateOnAnimation()
            }
            if (visible) SystemClock.sleep(50)
        }
        app.waitForIdle()
    }

    @Test fun audioAppearanceAndRecallSettingsAreFunctional() {
        ready()
        captureSettingsIfRequested()
        app.onNodeWithContentDescription("A new dream").performClick()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        app.onNodeWithContentDescription("Speak your dream").performClick()
        app.onNodeWithTag("record-audio").performClick()
        SystemClock.sleep(1500)
        app.onNodeWithTag("record-audio").performClick()
        app.onNodeWithText("Recording saved with this dream.").assertExists()
        app.onNodeWithText("Play").performClick()
        app.onNodeWithContentDescription("Close").performClick()
        app.activityRule.scenario.onActivity {
            val clips = ViewModelProvider(it)[DiaryViewModel::class.java].state.value.current!!.audio
            assertEquals(1, clips.size)
            val file = java.io.File(it.filesDir, "audio/${clips.single().file}")
            assertTrue(file.length() > 0)
            val media = android.media.MediaMetadataRetriever()
            try { media.setDataSource(file.absolutePath); assertTrue(media.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() > 0) }
            finally { media.release() }
        }
        option("Settings")
        chooseSetting("paper", "Dotted")
        chooseSetting("font", "Handwritten")
        chooseSetting("marker", "Rose")
        app.onNodeWithTag("settings-size-larger").performScrollTo().performClick()
        app.onNodeWithTag("night-paper").performScrollTo().performClick().assertIsOn()
        app.onNodeWithContentDescription("Close").performClick()
        app.activityRule.scenario.recreate()
        ready()
        option("Settings")
        app.onNodeWithTag("settings-paper").assertTextContains("Dotted")
        app.onNodeWithTag("settings-font").assertTextContains("Handwritten")
        app.onNodeWithTag("settings-marker").assertTextContains("Rose")
        app.activityRule.scenario.onActivity { assertEquals(20, ViewModelProvider(it)[DiaryViewModel::class.java].state.value.preferences.textSize) }
        app.onNodeWithTag("night-paper").performScrollTo().assertIsOn().performClick()
        chooseSetting("paper", "Lined")
        chooseSetting("font", "Book")
        chooseSetting("marker", "Linen")
        app.onNodeWithTag("settings-size-smaller").performScrollTo().performClick()
        app.onNodeWithTag("settings-morning").performScrollTo().performClick()
        app.onNodeWithText("Another prompt").performClick()
        app.onNodeWithText(RecallPrompts[1]).assertExists()
        app.onNodeWithContentDescription("Back to settings").performClick()
        app.onNodeWithTag("notebook-settings").assertExists()
        app.onNodeWithTag("settings-reality").performScrollTo().performClick()
        app.onNodeWithTag("practice-reminders").assertExists()
        app.onNodeWithContentDescription("Back to settings").performClick()
        app.onNodeWithTag("notebook-settings").assertExists()
        app.onNodeWithContentDescription("Close").performClick()
    }

    private fun chooseSetting(name: String, value: String) {
        app.onNodeWithTag("settings-$name").performScrollTo().performClick()
        app.onNodeWithTag("settings-$name-$value").performClick()
    }

    private fun captureSettingsIfRequested() {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureSettings") != "true") return
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("screenshots")!!.apply { mkdirs() }
        fun capture(name: String) {
            app.waitForIdle()
            val bitmap = app.onNode(isDialog()).captureToImage().asAndroidBitmap()
            java.io.File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        app.onNodeWithContentDescription("Notebook options").performClick()
        app.onNodeWithText("Settings").performClick()
        capture("settings-portrait-1.5.2.png")
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        capture("settings-landscape-1.5.2.png")
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, android.content.res.Configuration.ORIENTATION_PORTRAIT)
        app.onNodeWithTag("night-paper").performScrollTo().performClick()
        app.onNodeWithTag("settings-paper-preview").performScrollTo()
        capture("settings-night-1.5.2.png")
        app.onNodeWithTag("night-paper").performScrollTo().performClick()
        app.onNodeWithContentDescription("Close").performClick()
    }
}
