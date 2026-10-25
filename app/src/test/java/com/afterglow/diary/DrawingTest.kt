package com.afterglow.diary

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class DrawingTest {
    @Test fun drawingsRoundTripWithErasuresTransformsTransparencyAndLayerOrder() {
        val drawing = DiaryDrawing(id = "moon", page = 3, x = .3f, y = .7f, scale = .8f, rotation = -33f,
            aspect = .6f, opacity = .45f, behindText = true, strokes = listOf(
                InkStroke(listOf(InkPoint(-.2f, -.4f), InkPoint(.4f, .1f)), 0xFF8066A1, .02f, .5f),
                InkStroke(listOf(InkPoint(.1f, .1f)), width = .05f, eraser = true)))
        val dream = DreamEntry(body = "Čuvaj moj san", drawings = listOf(drawing, drawing.copy(id = "cloud", background = 0xFFFCFAF4)))
        assertEquals(listOf(dream), JournalCodec.decode(JournalCodec.encode(listOf(dream))))
    }
    @Test fun oldSketchBecomesAnEditableTransparentLayerWithoutLosingItsPoints() {
        val old = """{"version":2,"entries":[{"id":"old","date":"2026-10-21","title":"Moon","body":"My words","lucid":true,"strokes":[[[0.1,0.2],[0.8,0.9]]]}]}"""
        val dream = JournalCodec.decode(old).single()
        assertEquals("My words", dream.body)
        assertTrue(dream.hasDrawing)
        val layer = dream.pageDrawings.single()
        assertNull(layer.background)
        assertEquals(-.4f, layer.strokes.single().points.first().x, .00001f)
        assertEquals((.9f - .5f) / .75f, layer.strokes.single().points.last().y, .00001f)
        val edited = dream.copy(strokes = emptyList(), drawings = listOf(layer.copy(rotation = 40f)))
        assertEquals(edited, JournalCodec.decode(JournalCodec.encode(listOf(edited))).single())
    }
    @Test fun inverseTransformMapsBackToTheSameInkAndPinchKeepsItsPivot() {
        val size = Size(400f, 600f)
        val layer = DiaryDrawing(x = .5f, y = .5f, aspect = 2f / 3, scale = 1f, rotation = 90f)
        val point = pointInDrawing(Offset(160f, 380f), layer, size)
        assertEquals(.2f, point.x, .0001f); assertEquals(.1f, point.y, .0001f)
        val pivot = Offset(160f, 380f)
        val changed = transformDrawing(layer, size, pivot, Offset.Zero, 1.5f, 30f)
        val after = pointInDrawing(pivot, changed, size)
        assertEquals(point.x, after.x, .0001f); assertEquals(point.y, after.y, .0001f)
        assertEquals(1.5f, changed.scale)
    }
    @Test fun arrangeKeepsTheArtworkCenterStillAndCenterBringsItBackOnPage() {
        val size = Size(400f, 600f)
        val layer = DiaryDrawing(strokes = listOf(InkStroke(listOf(InkPoint(.1f, .2f), InkPoint(.3f, .4f)))))
        val factor = drawingFactor(layer, size)
        val center = Offset(200f + .2f * factor, 300f + .3f * factor)
        val changed = arrangeDrawing(layer, size, .4f, 75f)
        val point = pointInDrawing(center, changed, size)
        assertEquals(.2f, point.x, .0001f); assertEquals(.3f, point.y, .0001f)
        val centered = arrangeDrawing(changed, size, 1f, 0f, center = true)
        val middle = pointInDrawing(Offset(200f, 300f), centered, size)
        assertEquals(.2f, middle.x, .0001f); assertEquals(.3f, middle.y, .0001f)
    }
    @Test fun undoRedoRestoresErasureAndPlacementAndNewEditsClearRedo() {
        var saved = emptyList<DiaryDrawing>()
        val editor = DrawingEditor(emptyList()) { saved = it }
        val first = DiaryDrawing(id = "one", strokes = listOf(InkStroke(listOf(InkPoint(0f, 0f)))))
        editor.put(first)
        editor.put(first.copy(x = .7f, strokes = first.strokes + InkStroke(listOf(InkPoint(0f, 0f)), eraser = true)))
        editor.undo(); assertEquals(listOf(first), saved)
        editor.redo(); assertTrue(saved.single().strokes.last().eraser)
        editor.undo(); editor.put(first.copy(rotation = 20f)); assertFalse(editor.canRedo)
    }
}
