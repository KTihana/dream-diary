package com.afterglow.diary

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PageDrawingTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(app.activity)[DiaryViewModel::class.java]
    private fun entry() = model().state.value.current!!

    @Test fun drawEraseTransformAndSaveOnTheActualDiaryPage() {
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        app.waitUntil(15000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        app.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
            model.preferences(model.state.value.preferences.copy(night = false, font = "Book", textSize = 18, ruling = "Lined"))
        }
        app.onNodeWithContentDescription("A new dream").performClick()
        screenshot("blank-notebook-1.5.0.png")
        app.onNodeWithTag("dream-title").performTextReplacement("The moonlit courtyard")
        val words = "I found an archway floating above the clouds.\n\nThe moon felt close enough to touch. That was when I knew I was dreaming."
        app.onNodeWithTag("dream-body").performTextReplacement(words)
        app.onNodeWithContentDescription("Done writing").performClick()
        app.onNodeWithTag("lucid-marker").performClick()
        app.onNodeWithTag("draw-mode").performClick()
        val canvas = app.onNodeWithTag("page-drawing-canvas")
        app.onNodeWithTag("drawing-color-rose").performClick()
        app.onNodeWithTag("drawing-brush-size").performClick()
        app.onNodeWithText("10", substring = false).performClick()
        app.onNode(hasText("Done") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        val before = canvas.captureToImage().toPixelMap()
        path(listOf(Offset(.2f,.36f), Offset(.8f,.36f)))
        val ink = canvas.captureToImage().toPixelMap()
        val px = (ink.width * .5).toInt(); val py = (ink.height * .36).toInt()
        assertTrue("The stroke is rendered on the page", abs(ink[px,py].red - before[px,py].red) > .1f)
        app.onNodeWithTag("drawing-tool-eraser").performClick()
        path(listOf(Offset(.5f,.31f), Offset(.5f,.41f)))
        val erased = canvas.captureToImage().toPixelMap()
        assertEquals("Eraser reveals the page underneath", before[px,py].red, erased[px,py].red, .03f)
        assertTrue(entry().drawings.single().strokes.last().eraser)
        app.onNodeWithTag("drawing-undo").performClick()
        assertFalse(entry().drawings.single().strokes.last().eraser)
        app.onNodeWithTag("drawing-redo").performClick()
        assertTrue(entry().drawings.single().strokes.last().eraser)
        app.onNodeWithTag("drawing-undo").performClick(); app.onNodeWithTag("drawing-undo").performClick()
        assertTrue(entry().drawings.isEmpty())

        // Draw an example dream through the real page tools, not a separate mockup.
        app.onNodeWithTag("drawing-tool-pen").performClick()
        app.onNodeWithTag("drawing-color-purple").performClick()
        app.onNodeWithTag("drawing-brush-size").performClick()
        app.onNodeWithText("2", substring = false).performClick()
        app.onNode(hasText("Done") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        path(listOf(Offset(.28f,.76f),Offset(.28f,.61f),Offset(.30f,.55f),Offset(.36f,.51f),Offset(.44f,.49f),Offset(.52f,.50f),Offset(.59f,.54f),Offset(.63f,.60f),Offset(.63f,.76f)))
        path(listOf(Offset(.33f,.76f),Offset(.33f,.62f),Offset(.36f,.57f),Offset(.43f,.54f),Offset(.50f,.55f),Offset(.56f,.59f),Offset(.58f,.64f),Offset(.58f,.76f)))
        path(listOf(Offset(.46f,.54f),Offset(.46f,.76f)))
        path(listOf(Offset(.33f,.65f),Offset(.58f,.65f)))
        path(listOf(Offset(.16f,.78f),Offset(.25f,.77f),Offset(.34f,.78f),Offset(.43f,.77f),Offset(.55f,.78f),Offset(.70f,.77f),Offset(.80f,.78f)))
        path(listOf(Offset(.16f,.48f),Offset(.20f,.46f),Offset(.25f,.46f),Offset(.28f,.43f),Offset(.34f,.42f),Offset(.38f,.44f),Offset(.44f,.44f),Offset(.48f,.47f)))
        app.onNodeWithTag("drawing-color-gold").performScrollTo().performClick()
        path(listOf(Offset(.71f,.38f),Offset(.65f,.39f),Offset(.61f,.42f),Offset(.61f,.46f),Offset(.65f,.49f),Offset(.71f,.50f),Offset(.77f,.48f),Offset(.71f,.48f),Offset(.67f,.45f),Offset(.67f,.42f),Offset(.71f,.38f)))
        path(listOf(Offset(.48f,.39f),Offset(.48f,.42f))); path(listOf(Offset(.45f,.405f),Offset(.51f,.405f)))
        path(listOf(Offset(.80f,.56f),Offset(.80f,.59f))); path(listOf(Offset(.77f,.575f),Offset(.83f,.575f)))
        assertTrue(entry().drawings.single().strokes.map { it.color }.distinct().size >= 2)
        assertTrue(entry().drawings.single().strokes.all { it.width < .02f })
        screenshot("drawing-tools-portrait-1.5.0.png")

        app.onNodeWithTag("drawing-tool-move").performClick()
        val previousX = entry().drawings.single().x
        path(listOf(Offset(.45f,.6f),Offset(.5f,.57f)))
        assertTrue(entry().drawings.single().x > previousX)
        app.onNodeWithTag("arrange-drawing").performClick()
        progress("drawing-scale", .85f); progress("drawing-rotation", -12f); progress("drawing-opacity", .8f)
        app.onNodeWithTag("drawing-transparent").performScrollTo().assertIsOn().performClick().assertIsOff()
        app.onNodeWithTag("drawing-transparent").performClick().assertIsOn()
        app.onNodeWithTag("drawing-behind-text").performScrollTo().performClick().assertIsOn()
        app.onNode(hasText("Done") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        val placed = entry().drawings.single()
        assertEquals(.85f, placed.scale, .01f); assertEquals(-12f, placed.rotation, .1f)
        assertEquals(.8f, placed.opacity, .01f); assertNull(placed.background); assertTrue(placed.behindText)
        app.onNodeWithTag("write-mode").performClick()
        app.onNodeWithTag("dream-body").assertTextContains(words)
        app.waitUntil(10000) { model().state.value.saveStatus == SaveStatus.Saved }
        val disk = runBlocking { JournalStore(app.activity.filesDir).load() }.first { it.id == entry().id }
        assertEquals(entry(), disk)
        app.activityRule.scenario.recreate()
        assertEquals(placed, entry().drawings.single())
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        app.onNodeWithTag("draw-mode").performClick()
        screenshot("drawing-tools-landscape-1.5.0.png")
        app.onNodeWithTag("drawing-undo").performClick()
        assertFalse(entry().drawings.single().behindText)
        app.onNodeWithTag("drawing-redo").performClick()
        assertEquals(placed, entry().drawings.single())
        app.onNodeWithTag("write-mode").performClick()
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        screenshot("drawing-in-diary-1.5.0.png")
    }
    private fun path(points: List<Offset>) {
        app.onNodeWithTag("page-drawing-canvas").performTouchInput {
            fun scaled(point: Offset) = Offset(point.x * width, point.y * height)
            down(scaled(points.first()))
            points.drop(1).forEach { advanceEventTime(25); moveTo(scaled(it)) }
            up()
        }
        app.waitForIdle()
    }
    private fun progress(tag: String, value: Float) {
        app.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }
    private fun rotate(orientation: Int, expected: Int) {
        app.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        app.waitUntil(15000) { app.activity.resources.configuration.orientation == expected }
        app.waitForIdle()
    }
    private fun screenshot(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("screenshots")!!
        dir.mkdirs()
        val image = app.onNodeWithTag("fullscreen-paper").captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
