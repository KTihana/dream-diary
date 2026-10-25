package com.afterglow.diary

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaperAlignmentTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun titleAndWritingStayBetweenRulesAcrossFontsSizesAndRotation() {
        app.waitUntil(15_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        var original = NotebookPreferences()
        app.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DiaryViewModel::class.java]
            original = model.state.value.preferences
            model.addDream()
            model.editTitle("Flying quietly above the rooftops")
            model.editBody("I was flying quietly through the city.\n\nČujem šum, osjećam lakoću. Želim još.\nThe sky was a soft blue.")
        }
        try {
            listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE).forEach { (requested, expected) ->
                rotate(requested, expected)
                val filter = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("alignmentFonts")
                WritingFonts.forEachIndexed { i, font ->
                    if (filter != null && font.name !in filter.split(',')) return@forEachIndexed
                    WritingSpacing.forEach { spacing ->
                        val size = if (i % 2 == 0) 16 else 26
                        app.activityRule.scenario.onActivity { activity ->
                            ViewModelProvider(activity)[DiaryViewModel::class.java].preferences(original.copy(ruling = "Lined", font = font.name,
                                textSize = size, night = false, paperTone = "Ivory", spacing = spacing, matchTitle = true))
                        }
                        app.waitForIdle()
                        val pixels = app.onRoot().captureToImage().toPixelMap()
                        assertTextBetweenVisibleRules("dream-title", pixels, "${font.name} $size $spacing, orientation $expected")
                        assertTextBetweenVisibleRules("dream-body", pixels, "${font.name} $size $spacing, orientation $expected")
                    }
                }
            }
        } finally {
            app.activityRule.scenario.onActivity { ViewModelProvider(it)[DiaryViewModel::class.java].preferences(original) }
        }
    }

    /** Inspect the actual rendered blue rules, independently of the drawing implementation. */
    private fun assertTextBetweenVisibleRules(tag: String, pixels: androidx.compose.ui.graphics.PixelMap, description: String) {
        val node = app.onNodeWithTag(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        val text = layouts.single()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        // This column is outside the ink but inside the full-width notebook paper.
        val spread = app.onAllNodesWithTag("notebook-spread").fetchSemanticsNodes().isNotEmpty()
        val x = if (spread && tag == "dream-title") app.onNodeWithTag("left-leaf").fetchSemanticsNode().boundsInRoot.left.toInt() + 12 else pixels.width - 12
        val rows = (0 until pixels.height).filter { y ->
            val color = pixels[x, y]
            // Thin, translucent rules blend into warm paper; compare channel losses
            // instead of requiring the blended pixel itself to look blue.
            val redLoss = 252f / 255f - color.red
            val greenLoss = 250f / 255f - color.green
            val blueLoss = 244f / 255f - color.blue
            redLoss > .015f && redLoss > blueLoss * 1.7f && greenLoss > blueLoss * 1.3f
        }
        assertTrue("No rendered paper rules: $description", rows.isNotEmpty())
        repeat(text.lineCount) { line ->
            val top = bounds.top + text.getLineTop(line)
            val bottom = bounds.top + text.getLineBottom(line)
            if (bottom <= bounds.bottom + 1f) {
                val height = bottom - top
                val baseline = bounds.top + text.getLineBaseline(line)
                val interior = rows.filter { it > top + height * .18f && it < bottom - height * .18f }
                assertTrue("A rule cuts through $tag row $line ($description): $interior", interior.isEmpty())
                val rule = rows.firstOrNull { it > baseline }
                assertNotNull("No rule under $tag row $line: $description", rule)
                assertTrue("$tag row $line drifts from its rule: $description", rule!! - baseline < height * .45f)
            }
        }
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
        assertTrue("Rotation did not finish", ready)
        app.waitForIdle()
    }
}
