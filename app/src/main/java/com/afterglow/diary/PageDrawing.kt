package com.afterglow.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

internal enum class DrawingTool { Pen, Highlighter, Eraser, Move }

/** One editor per dream in the ViewModel: history survives screen rotation. Only completed edits save. */
@Stable
internal class DrawingEditor(initial: List<DiaryDrawing>, private val save: (List<DiaryDrawing>) -> Unit) {
    var layers by mutableStateOf(initial); private set
    var selectedId by mutableStateOf<String?>(initial.lastOrNull()?.id)
    var preview by mutableStateOf<DiaryDrawing?>(null)
    var tool by mutableStateOf(DrawingTool.Pen)
    var color by mutableLongStateOf(0xFF385D79)
    var width by mutableFloatStateOf(3f)
    var eraserWidth by mutableFloatStateOf(20f)
    var expanded by mutableStateOf(true)
    var viewport by mutableStateOf(Size.Zero)
    private var past by mutableStateOf(emptyList<List<DiaryDrawing>>())
    private var future by mutableStateOf(emptyList<List<DiaryDrawing>>())
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    val visibleLayers: List<DiaryDrawing> get() = preview?.let { draft ->
        if (layers.any { it.id == draft.id }) layers.map { if (it.id == draft.id) draft else it } else layers + draft
    } ?: layers
    fun selected(page: Int) = layers.firstOrNull { it.id == selectedId && it.page == page }
    fun commit(next: List<DiaryDrawing>) {
        preview = null
        if (next == layers) return
        past = (past + listOf(layers)).takeLast(50); future = emptyList()
        layers = next; save(layers)
    }
    fun put(drawing: DiaryDrawing) {
        selectedId = drawing.id
        commit(if (layers.any { it.id == drawing.id }) layers.map { if (it.id == drawing.id) drawing else it } else layers + drawing)
    }
    fun undo() {
        if (!canUndo) return
        preview = null; future = future + listOf(layers); layers = past.last(); past = past.dropLast(1); save(layers)
    }
    fun redo() {
        if (!canRedo) return
        preview = null; past = past + listOf(layers); layers = future.last(); future = future.dropLast(1); save(layers)
    }
    fun remove(id: String) { commit(layers.filterNot { it.id == id }); selectedId = null }
}

internal fun drawingBounds(drawing: DiaryDrawing): Rect? {
    val ink = drawing.strokes.filter { !it.eraser && it.points.isNotEmpty() }
    if (ink.isEmpty()) return null
    return Rect(ink.minOf { s -> s.points.minOf { it.x } - s.width / 2 },
        ink.minOf { s -> s.points.minOf { it.y } - s.width / 2 },
        ink.maxOf { s -> s.points.maxOf { it.x } + s.width / 2 },
        ink.maxOf { s -> s.points.maxOf { it.y } + s.width / 2 })
}
internal fun drawingFactor(drawing: DiaryDrawing, size: Size) = min(size.width, size.height * drawing.aspect) * drawing.scale
private fun Offset.turned(degrees: Float): Offset {
    val angle = Math.toRadians(degrees.toDouble())
    return Offset((x * cos(angle) - y * sin(angle)).toFloat(), (x * sin(angle) + y * cos(angle)).toFloat())
}
internal fun pointInDrawing(point: Offset, drawing: DiaryDrawing, size: Size): InkPoint {
    val local = (point - Offset(drawing.x * size.width, drawing.y * size.height)).turned(-drawing.rotation) / drawingFactor(drawing, size)
    return InkPoint(local.x, local.y)
}
internal fun transformDrawing(drawing: DiaryDrawing, size: Size, pivot: Offset, pan: Offset, zoom: Float, rotation: Float): DiaryDrawing {
    val scale = (drawing.scale * zoom).coerceIn(.1f, 5f)
    val ratio = scale / drawing.scale
    val center = pivot + (Offset(drawing.x * size.width, drawing.y * size.height) - pivot).times(ratio).turned(rotation) + pan
    return drawing.copy(x = (center.x / size.width).coerceIn(-1f, 2f), y = (center.y / size.height).coerceIn(-1f, 2f),
        scale = scale, rotation = ((drawing.rotation + rotation + 540f) % 360f) - 180f)
}

internal fun arrangeDrawing(drawing: DiaryDrawing, size: Size, scale: Float = drawing.scale, rotation: Float = drawing.rotation, center: Boolean = false): DiaryDrawing {
    if (size.width <= 0f || size.height <= 0f) return drawing.copy(scale = scale, rotation = rotation)
    val localCenter = drawingBounds(drawing)?.center ?: Offset.Zero
    val pivot = Offset(drawing.x * size.width, drawing.y * size.height) + (localCenter * drawingFactor(drawing, size)).turned(drawing.rotation)
    val pan = if (center) Offset(size.width / 2, size.height / 2) - pivot else Offset.Zero
    return transformDrawing(drawing, size, pivot, pan, scale / drawing.scale, rotation - drawing.rotation)
}

private fun DrawScope.inkLayer(drawing: DiaryDrawing) {
    val bounds = drawingBounds(drawing) ?: return
    // Each drawing has its own transparent layer. An eraser never clears diary text or other drawings.
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = drawing.opacity })
    withTransform({
        translate(drawing.x * size.width, drawing.y * size.height)
        rotate(drawing.rotation, Offset.Zero)
        scale(drawingFactor(drawing, size), drawingFactor(drawing, size), Offset.Zero)
    }) {
        drawing.background?.let { drawRect(Color(it), topLeft = bounds.topLeft - Offset(.015f, .015f), size = Size(bounds.width + .03f, bounds.height + .03f)) }
        drawing.strokes.forEach { stroke ->
            val color = Color(stroke.color).copy(alpha = stroke.opacity)
            val blend = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                drawCircle(color, stroke.width / 2, Offset(point.x, point.y), blendMode = blend)
            } else if (stroke.points.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    for (i in 1 until stroke.points.size) {
                        val previous = stroke.points[i - 1]; val point = stroke.points[i]
                        quadraticBezierTo(previous.x, previous.y, (previous.x + point.x) / 2, (previous.y + point.y) / 2)
                    }
                    lineTo(stroke.points.last().x, stroke.points.last().y)
                }
                drawPath(path, color, style = Stroke(stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
            }
        }
    }
    drawContext.canvas.restore()
}

@Composable
internal fun PageDrawingCanvas(layers: List<DiaryDrawing>, page: Int, behind: Boolean, modifier: Modifier = Modifier,
    editor: DrawingEditor? = null, active: Boolean = false) {
    val accent = Accent
    Canvas(modifier.clipToBounds().onSizeChanged { if (editor != null && !behind) editor.viewport = Size(it.width.toFloat(), it.height.toFloat()) }.testTag(if (behind) "drawing-under-text" else "page-drawing-canvas")
        .semantics {
            contentDescription = if (active) "Draw directly on the diary page" else "Dream drawings"
            stateDescription = "${layers.count { it.page == page }} drawings on this sheet"
        }
        .then(if (active && !behind && editor != null) Modifier.pointerInput(editor, page) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val viewport = Size(size.width.toFloat(), size.height.toFloat())
                if (editor.tool == DrawingTool.Move) {
                    val hit = editor.layers.filter { it.page == page }.sortedBy { if (it.behindText) 0 else 1 }.asReversed().firstOrNull { drawing ->
                        val point = pointInDrawing(down.position, drawing, viewport)
                        drawingBounds(drawing)?.inflate(14.dp.toPx() / drawingFactor(drawing, viewport))?.contains(Offset(point.x, point.y)) == true
                    }
                    editor.selectedId = hit?.id
                    if (hit != null) {
                        var moved: DiaryDrawing = hit
                        try {
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.pressed }) {
                                    moved = transformDrawing(moved, viewport, event.calculateCentroid(useCurrent = false),
                                        event.calculatePan(), event.calculateZoom(), event.calculateRotation())
                                    editor.preview = moved
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            editor.put(moved)
                        } finally { editor.preview = null }
                    }
                } else {
                    val selected = editor.selected(page)
                    if (editor.tool != DrawingTool.Eraser || selected != null) {
                        val layer = selected ?: DiaryDrawing(page = page, aspect = viewport.width / viewport.height)
                        val isEraser = editor.tool == DrawingTool.Eraser
                        val brush = if (isEraser) editor.eraserWidth else if (editor.tool == DrawingTool.Highlighter) max(12f, editor.width) else editor.width
                        var stroke = InkStroke(listOf(pointInDrawing(down.position, layer, viewport)), editor.color,
                            brush.dp.toPx() / drawingFactor(layer, viewport), if (editor.tool == DrawingTool.Highlighter) .28f else 1f, isEraser)
                        editor.selectedId = layer.id
                        editor.preview = layer.copy(strokes = layer.strokes + stroke)
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (stroke.points.size < 12000 && change.position != change.previousPosition) {
                                    stroke = stroke.copy(points = stroke.points + pointInDrawing(change.position, layer, viewport))
                                    editor.preview = layer.copy(strokes = layer.strokes + stroke)
                                }
                                change.consume()
                            } while (change.pressed)
                            editor.put(layer.copy(strokes = layer.strokes + stroke))
                        } finally { editor.preview = null }
                    }
                }
            }
        } else Modifier)) {
        layers.filter { it.page == page && it.behindText == behind }.forEach { inkLayer(it) }
        if (active && !behind && editor?.tool == DrawingTool.Move) {
            layers.firstOrNull { it.id == editor.selectedId && it.page == page }?.let { drawing ->
                drawingBounds(drawing)?.let { bounds ->
                    val factor = drawingFactor(drawing, size)
                    withTransform({ translate(drawing.x * size.width, drawing.y * size.height); rotate(drawing.rotation, Offset.Zero); scale(factor, factor, Offset.Zero) }) {
                        drawRect(accent, bounds.topLeft - Offset(.012f, .012f), Size(bounds.width + .024f, bounds.height + .024f),
                            style = Stroke(1.2.dp.toPx() / factor, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() / factor, 4.dp.toPx() / factor))))
                    }
                }
            }
        }
    }
}

private val DrawingColors = listOf("Ink" to 0xFF292D30, "Blue" to 0xFF385D79, "Purple" to 0xFF8066A1,
    "Rose" to 0xFFB96580, "Orange" to 0xFFCB8150, "Gold" to 0xFFE1B94E, "Teal" to 0xFF438A8A, "White" to 0xFFFFFFFF)

@Composable
internal fun DrawingToolbar(editor: DrawingEditor, page: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var showSize by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showArrange by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    val selected = editor.selected(page)
    Surface(modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = ControlPaper,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline), shadowElevation = 5.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editor.expanded) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    listOf(DrawingTool.Pen to DreamIcons.Pen, DrawingTool.Highlighter to DreamIcons.Highlighter,
                        DrawingTool.Eraser to DreamIcons.Eraser, DrawingTool.Move to DreamIcons.Move).forEach { (tool, icon) ->
                        IconButton(onClick = { editor.tool = tool }, modifier = Modifier.size(42.dp)
                            .background(if (editor.tool == tool) AccentWash else Color.Transparent, RoundedCornerShape(12.dp))
                            .testTag("drawing-tool-${tool.name.lowercase()}")) {
                            Icon(icon, tool.name, Modifier.size(21.dp), tint = if (editor.tool == tool) Accent else Pencil)
                        }
                    }
                    }
                    IconButton(onClick = editor::undo, enabled = editor.canUndo, modifier = Modifier.size(40.dp).testTag("drawing-undo")) { Icon(DreamIcons.Undo, "Undo drawing edit", Modifier.size(20.dp)) }
                    IconButton(onClick = editor::redo, enabled = editor.canRedo, modifier = Modifier.size(40.dp).testTag("drawing-redo")) { Icon(DreamIcons.Redo, "Redo drawing edit", Modifier.size(20.dp)) }
                } else {
                    TextButton(onClick = { editor.expanded = true }) { Text("Drawing tools") }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Done") }
            }
            if (editor.expanded) {
                if (editor.tool == DrawingTool.Move) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editor.selectedId = null; editor.tool = DrawingTool.Pen }, modifier = Modifier.testTag("new-drawing")) { Text("New drawing", fontSize = 12.sp) }
                        TextButton(onClick = { showLayers = true }) { Text("Layers", fontSize = 12.sp) }
                        TextButton(onClick = { showArrange = true }, enabled = selected != null, modifier = Modifier.testTag("arrange-drawing")) { Text("Arrange", fontSize = 12.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editor.expanded = false }, contentPadding = PaddingValues(4.dp)) { Text("Hide", fontSize = 12.sp) }
                    }
                    Text(if (selected != null) "Drag to move · pinch to resize & rotate" else "Tap a drawing to select it", color = Pencil, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (editor.tool != DrawingTool.Eraser) Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            DrawingColors.forEach { (name, color) ->
                                Box(Modifier.size(34.dp).clickable { editor.color = color }
                                    .semantics { contentDescription = "$name drawing color"; stateDescription = if (editor.color == color) "Selected" else "Not selected" }
                                    .testTag("drawing-color-${name.lowercase()}"), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(24.dp).border(if (editor.color == color) 2.dp else .5.dp, if (editor.color == color) Accent else Hairline, CircleShape)
                                        .padding(4.dp).background(Color(color), CircleShape))
                                }
                            }
                            TextButton(onClick = { showColor = true }) { Text("Custom", fontSize = 12.sp) }
                        } else Text("Erase this drawing", Modifier.weight(1f).padding(start = 8.dp), color = Pencil, fontSize = 12.sp)
                        TextButton(onClick = { showSize = true }, modifier = Modifier.testTag("drawing-brush-size")) {
                            Text("${(if (editor.tool == DrawingTool.Eraser) editor.eraserWidth else editor.width).roundToInt()} px", fontSize = 12.sp)
                        }
                        TextButton(onClick = { editor.expanded = false }, contentPadding = PaddingValues(4.dp)) { Text("Hide", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
    if (showSize) AlertDialog(onDismissRequest = { showSize = false }, containerColor = ControlPaper,
        title = { Text(if (editor.tool == DrawingTool.Eraser) "Eraser size" else "Line thickness") }, text = {
            Column {
                val eraser = editor.tool == DrawingTool.Eraser
                Text("${(if (eraser) editor.eraserWidth else editor.width).roundToInt()} px", color = Pencil)
                Slider(if (eraser) editor.eraserWidth else editor.width, { if (eraser) editor.eraserWidth = it else editor.width = it },
                    valueRange = if (eraser) 4f..50f else 1f..24f, modifier = Modifier.testTag("brush-width-slider"))
                Row { listOf(2f, 5f, 10f, 20f).forEach { size -> TextButton(onClick = { if (eraser) editor.eraserWidth = max(4f, size) else editor.width = size }) { Text("${size.toInt()}") } } }
            }
        }, confirmButton = { TextButton(onClick = { showSize = false }) { Text("Done") } })
    if (showColor) {
        var hex by remember { mutableStateOf("%06X".format(editor.color and 0xFFFFFF)) }
        val valid = hex.matches(Regex("[0-9a-fA-F]{6}"))
        AlertDialog(onDismissRequest = { showColor = false }, containerColor = ControlPaper, title = { Text("Custom color") },
            text = { OutlinedTextField(hex, { hex = it.removePrefix("#").take(6) }, label = { Text("Hex color, e.g. 8066A1") }, leadingIcon = { Text("#") }, singleLine = true, isError = !valid) },
            confirmButton = { TextButton(enabled = valid, onClick = { editor.color = 0xFF000000 or hex.toLong(16); showColor = false }) { Text("Use color") } },
            dismissButton = { TextButton(onClick = { showColor = false }) { Text("Cancel") } })
    }
    if (showLayers) AlertDialog(onDismissRequest = { showLayers = false }, containerColor = ControlPaper, title = { Text("Drawings on this page") },
        text = { Column {
            editor.layers.filter { it.page == page }.forEachIndexed { index, layer ->
                TextButton(onClick = { editor.selectedId = layer.id; editor.tool = DrawingTool.Move; showLayers = false }) {
                    Text("Drawing ${index + 1}${if (layer.id == editor.selectedId) " · selected" else ""}")
                }
            }
            TextButton(onClick = { editor.selectedId = null; editor.tool = DrawingTool.Pen; showLayers = false }) { Text("New drawing") }
        } }, confirmButton = { TextButton(onClick = { showLayers = false }) { Text("Done") } })
    if (showArrange && selected != null) DrawingArrangeDialog(selected, editor) { showArrange = false }
}

@Composable
private fun DrawingArrangeDialog(drawing: DiaryDrawing, editor: DrawingEditor, onClose: () -> Unit) {
    var draft by remember(drawing.id) { mutableStateOf(drawing) }
    fun apply() { editor.put(draft); onClose() }
    DisposableEffect(drawing.id) { onDispose { editor.preview = null } }
    AlertDialog(onDismissRequest = ::apply, containerColor = ControlPaper, title = { Text("Arrange drawing") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).widthIn(min = 240.dp)) {
                Text("Size · ${(draft.scale * 100).roundToInt()}%", color = Pencil)
                Slider(draft.scale, { draft = arrangeDrawing(draft, editor.viewport, scale = it); editor.preview = draft }, valueRange = .1f..5f, modifier = Modifier.testTag("drawing-scale"))
                Text("Rotation · ${draft.rotation.roundToInt()}°", color = Pencil)
                Slider(draft.rotation, { draft = arrangeDrawing(draft, editor.viewport, rotation = it); editor.preview = draft }, valueRange = -180f..180f, modifier = Modifier.testTag("drawing-rotation"))
                Text("Opacity · ${(draft.opacity * 100).roundToInt()}%", color = Pencil)
                Slider(draft.opacity, { draft = draft.copy(opacity = it); editor.preview = draft }, valueRange = .1f..1f, modifier = Modifier.testTag("drawing-opacity"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.background == null, { draft = draft.copy(background = if (it) null else 0xFFFCFAF4); editor.preview = draft }, Modifier.testTag("drawing-transparent"))
                    Text("Transparent background", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.behindText, { draft = draft.copy(behindText = it); editor.preview = draft }, Modifier.testTag("drawing-behind-text"))
                    Text("Behind the writing", fontSize = 13.sp)
                }
                Row {
                    TextButton(onClick = { draft = arrangeDrawing(draft, editor.viewport, scale = 1f, rotation = 0f, center = true); editor.preview = draft }) { Text("Center") }
                    TextButton(onClick = { editor.remove(drawing.id); onClose() }, modifier = Modifier.testTag("delete-drawing")) { Text("Delete drawing") }
                }
            }
        }, confirmButton = { TextButton(onClick = ::apply) { Text("Done") } })
}
