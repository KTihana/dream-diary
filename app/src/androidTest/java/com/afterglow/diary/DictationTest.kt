package com.afterglow.diary

import android.Manifest
import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Before
import java.io.File

@RunWith(AndroidJUnit4::class)
class DictationTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private var originalService: String? = null
    @Before fun useTestSpeechService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        originalService = android.provider.Settings.Secure.getString(instrumentation.targetContext.contentResolver, "voice_recognition_service")
        val service = ComponentName(instrumentation.context, TestDictationService::class.java)
        setSpeechService(service.flattenToString())
    }
    @After fun restoreSpeechService() { setSpeechService(originalService) }
    private fun setSpeechService(value: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try { android.provider.Settings.Secure.putString(instrumentation.targetContext.contentResolver, "voice_recognition_service", value) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    @Test fun microphoneDictationCanBeReviewedEditedAndAddedWithoutReplacingTheDream() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.context.packageName, Manifest.permission.RECORD_AUDIO)
        app.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        app.waitForIdle()
        app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithContentDescription("A new dream").performClick()
        app.onNodeWithTag("dream-title").performTextReplacement("My own title")
        app.onNodeWithTag("dream-body").performTextReplacement("I remember the courtyard.")
        app.onNodeWithContentDescription("Speak your dream").performClick()
        app.onNodeWithTag("dictation-language-en-US").performClick()
        app.onNodeWithTag("dictate-dream").performClick()
        try {
            app.waitUntil(30_000) { app.onAllNodesWithText("I was walking").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            val status = app.onNodeWithTag("voice-status").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
            throw AssertionError("Speech service did not produce its partial result: $status", error)
        }
        app.onNodeWithTag("record-audio").assertIsNotEnabled()
        app.onNodeWithTag("dictate-dream").performClick()
        app.waitUntil(15_000) { app.onAllNodesWithTag("dictation-transcript").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("dictation-transcript").assertTextContains("I was walking through a violet city.")
        app.onNodeWithTag("dictation-waveform").assertDoesNotExist()
        app.onNodeWithTag("dictation-transcript").performTextReplacement("")
        app.onNodeWithText("Add to dream").assertIsNotEnabled()
        app.onNodeWithTag("dictation-transcript").performTextReplacement("I walked through a violet city.")
        app.onNodeWithText("Add to dream").performScrollTo().performClick()
        app.onNodeWithTag("dream-title").assertTextContains("My own title")
        app.onNodeWithTag("dream-body").assertTextContains("I remember the courtyard.\n\nI walked through a violet city.")
        app.activityRule.scenario.recreate()
        app.waitUntil(15_000) { app.onAllNodesWithTag("dream-body").fetchSemanticsNodes().isNotEmpty() }
        app.activityRule.scenario.onActivity { activity ->
            val dream = ViewModelProvider(activity)[DiaryViewModel::class.java].state.value.current!!
            assertEquals("My own title", dream.title)
            assertEquals("I remember the courtyard.\n\nI walked through a violet city.", dream.body)
        }
    }

    @Test fun croatianLanguageIsRememberedAndDiacriticsReachTheSavedDream() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.context.packageName, Manifest.permission.RECORD_AUDIO)
        app.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithContentDescription("A new dream").performClick()
        app.onNodeWithTag("dream-title").performTextReplacement("Šapat uz rijeku")
        app.onNodeWithContentDescription("Speak your dream").performClick()
        app.onNodeWithTag("dictation-language-hr-HR").performClick()
        app.activityRule.scenario.recreate()
        app.onNodeWithTag("dictation-language-hr-HR").assertIsSelected()
        assertEquals("hr-HR", NotebookPreferencesStore(instrumentation.targetContext).load().dictationLanguage)
        saveScreenshot("voice-croatian-1.4.3.png")
        app.onNodeWithTag("dictate-dream").performClick()
        app.waitUntil(30_000) { app.onAllNodesWithText("Šetala sam uz rijeku").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("dictation-language-en-US").assertIsNotEnabled()
        app.onNodeWithTag("dictate-dream").performClick()
        val croatian = "Šetala sam uz rijeku i čula šapat. Đurđica je nosila žuti ključ, a noć je bila tiha."
        app.waitUntil(15_000) { app.onAllNodesWithTag("dictation-transcript").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("dictation-transcript").assertTextContains(croatian)
        app.onNodeWithTag("dictation-waveform").assertDoesNotExist()
        saveScreenshot("dictation-croatian-1.4.3.png")
        app.onNodeWithText("Add to dream").performScrollTo().performClick()
        lateinit var savedModel: DiaryViewModel
        var dreamId = ""
        app.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
            savedModel = model
            dreamId = model.state.value.current!!.id
            assertEquals("Šapat uz rijeku", model.state.value.current!!.title)
            assertEquals(croatian, model.state.value.current!!.body)
        }
        app.waitUntil(10_000) { savedModel.state.value.saveStatus == SaveStatus.Saved }
        val saved = kotlinx.coroutines.runBlocking { JournalStore(instrumentation.targetContext.filesDir).load() }
        assertEquals(croatian, saved.first { it.id == dreamId }.body)
    }

    @Test fun soundMeterFollowsSpeechLevelsAndStopsWhenTheAppLeavesTheForeground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.context.packageName, Manifest.permission.RECORD_AUDIO)
        app.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithContentDescription("Speak your dream").performClick()
        app.onNodeWithTag("dictation-language-hr-HR").performClick()
        app.onNodeWithTag("dictate-dream").performClick()
        waitForMeter("Speak when you’re ready") // Providers without RMS callbacks still show an honest ready state.
        waitForMeter("Listening for your voice") // Negative/quiet RMS stays flat.
        saveScreenshot("dictation-quiet-1.4.3.png")
        waitForMeter("Sound detected")
        // Wait for some history, rather than capturing a single first bar.
        val screenshotAt = android.os.SystemClock.elapsedRealtime() + 2300
        app.waitUntil(6000) { android.os.SystemClock.elapsedRealtime() >= screenshotAt }
        waitForMeter("Sound detected")
        saveScreenshot("dictation-waveform-portrait-1.4.3.png")
        app.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        // Rotation destroys this microphone session. Restarted sessions must have no old levels.
        app.onNodeWithTag("dictation-waveform").assertDoesNotExist()
        app.onNodeWithTag("dictate-dream").performClick()
        waitForMeter("Speak when you’re ready")
        waitForMeter("Sound detected")
        val landscapeAt = android.os.SystemClock.elapsedRealtime() + 2300
        app.waitUntil(6000) { android.os.SystemClock.elapsedRealtime() >= landscapeAt }
        waitForMeter("Sound detected")
        app.onNodeWithTag("dictation-waveform").performScrollTo()
        saveScreenshot("dictation-waveform-landscape-1.4.3.png")
        app.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        app.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        app.onNodeWithTag("dictation-waveform").assertDoesNotExist()
        app.onNodeWithTag("record-audio").assertIsEnabled()
        app.onNodeWithTag("dictate-dream").performScrollTo().performClick()
        waitForMeter("Speak when you’re ready")
        app.onNodeWithTag("dictate-dream").performScrollTo().performClick()
        app.waitUntil(15_000) { app.onAllNodesWithTag("dictation-transcript").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("dictation-waveform").assertDoesNotExist()
    }

    private fun waitForMeter(status: String) {
        val meter = hasTestTag("dictation-waveform") and
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, status)
        app.waitUntil(15_000) { app.onAllNodes(meter).fetchSemanticsNodes().isNotEmpty() }
        app.onNode(meter).assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("screenshots")!!
        directory.mkdirs()
        val image = app.onNodeWithText("Voice notes").onParent().onParent().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
