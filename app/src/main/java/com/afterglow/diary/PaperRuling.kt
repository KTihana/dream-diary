package com.afterglow.diary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Rules belong to the ink's line boxes, rather than an unrelated offset on the window. */
internal class PaperRuling {
    enum class Part { Title, Body }
    private class Ink {
        var coordinates: LayoutCoordinates? = null
        var text: TextLayoutResult? = null
        var baseline: Int? = null
    }
    private val title = Ink()
    private val body = Ink()
    private var page: LayoutCoordinates? = null
    private var revision by mutableIntStateOf(0)
    private fun ink(part: Part) = if (part == Part.Title) title else body
    private fun invalidateRules() = Snapshot.withoutReadObservation { revision++ }

    fun textLayout(part: Part, result: TextLayoutResult) {
        ink(part).text = result
        invalidateRules()
    }

    /** Reading the inherited baseline also follows the editor's internal keyboard scrolling. */
    fun textModifier(part: Part): Modifier = Modifier
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val baseline = placeable[FirstBaseline].takeUnless { it == androidx.compose.ui.layout.AlignmentLine.Unspecified }
            if (ink(part).baseline != baseline) {
                ink(part).baseline = baseline
                invalidateRules()
            }
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        .onGloballyPositioned {
            ink(part).coordinates = it
            invalidateRules()
        }

    fun pageModifier(enabled: Boolean, color: Color, continuationStep: Float, titlePage: Boolean = false): Modifier =
        Modifier.onGloballyPositioned { page = it; invalidateRules() }.drawBehind {
            @Suppress("UNUSED_VARIABLE") val currentPlacement = revision
            if (!enabled) return@drawBehind
            val pageCoordinates = page?.takeIf { it.isAttached } ?: return@drawBehind
            val clearance = 2.dp.toPx()
            val stroke = .5.dp.toPx()
            fun rule(y: Float) {
                if (y >= 0 && y < size.height) drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
            }
            fun drawInk(ink: Ink, fillTo: Float? = null) {
                val coordinates = ink.coordinates?.takeIf { it.isAttached } ?: return
                val text = ink.text ?: return
                if (text.lineCount == 0) return
                val top = pageCoordinates.localPositionOf(coordinates, Offset.Zero).y
                val bottom = top + coordinates.size.height
                val scroll = (ink.baseline ?: text.firstBaseline.roundToInt()) - text.firstBaseline.roundToInt()
                val origin = top + scroll
                val first = origin + text.getLineTop(0) - clearance
                val boundaries = (0 until text.lineCount).map { origin + text.getLineBottom(it) - clearance }
                if (first >= top - clearance && first <= bottom) rule(first)
                val visible = boundaries.filter { it >= top - clearance && it <= bottom }
                visible.forEach(::rule)
                val last = visible.lastOrNull() ?: (bottom - clearance)
                // Empty rows use the same physical advance as the measured writing font.
                val step = if (text.lineCount > 1) text.getLineBottom(text.lineCount - 1) - text.getLineBottom(text.lineCount - 2)
                    else text.getLineBottom(0) - text.getLineTop(0)
                var y = last + if (fillTo != null) continuationStep else step
                val end = fillTo ?: (bottom - clearance)
                while (y <= end && step > 0) {
                    rule(y)
                    y += if (fillTo != null) continuationStep else step
                }
            }
            drawInk(title, if (titlePage) size.height - 42.dp.toPx() else null)
            drawInk(body)
        }
}
