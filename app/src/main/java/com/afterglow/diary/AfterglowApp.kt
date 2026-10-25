package com.afterglow.diary

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

internal val Desk = Color(0xFFE5DFD7)
internal val LocalNotebookPreferences = staticCompositionLocalOf { NotebookPreferences() }
internal val LocalLucidPaper = staticCompositionLocalOf { false }
private val LocalPaperTint = staticCompositionLocalOf<Color?> { null }
internal fun notebookPaperColor(lucid: Boolean, night: Boolean): Color = when {
    lucid && night -> Color(0xFF302A3D)
    lucid -> Color(0xFFF0E8F8)
    night -> Color(0xFF252329)
    else -> Color(0xFFFCFAF4)
}
internal val Paper: Color @Composable get() = LocalPaperTint.current
    ?: notebookPaperColor(LocalLucidPaper.current, LocalNotebookPreferences.current.night)
private val PaperRule: Color @Composable get() = when {
    LocalLucidPaper.current && LocalNotebookPreferences.current.night -> Color(0xFF78668C)
    LocalLucidPaper.current -> Color(0xFFC4B6D2)
    LocalNotebookPreferences.current.night -> Color(0xFF5A5565)
    else -> Color(0xFFD5DEE5)
}
internal val Ink: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFFE0D8CA) else Color(0xFF292D30)
internal val Pencil: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFFB5AA9D) else Color(0xFF72736F)
internal val Cover: Color @Composable get() = when (LocalNotebookPreferences.current.cover) {
    "Rose" -> Color(0xFF925862); "Midnight" -> Color(0xFF536A88); else -> Color(0xFF7F6B57)
}
internal val DiarySerif = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
)
internal val Handwritten = FontFamily(Font(R.font.caveat))
private val DateFormat get() = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterglowApp(viewModel: DiaryViewModel, captureRequest: String? = null, onCaptureConsumed: () -> Unit = {},
    practiceRequest: String? = null, practiceNight: String? = null, onPracticeConsumed: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalNotebookPreferences provides state.preferences) {
    MaterialTheme(colorScheme = (if (state.preferences.night) darkColorScheme() else lightColorScheme()).copy(
        primary = Accent, onPrimary = if (state.preferences.night) Color(0xFF1C2C38) else Color.White, surface = ControlPaper, onSurface = Ink,
        background = PanelPaper, onBackground = Ink, secondary = Accent,
        primaryContainer = AccentWash, onPrimaryContainer = Accent,
        secondaryContainer = AccentWash, onSecondaryContainer = Accent,
        surfaceContainerLow = ControlPaper, surfaceContainer = ControlPaper, surfaceContainerHigh = ControlPaper,
        surfaceContainerHighest = AccentWash, surfaceVariant = ControlPaper, onSurfaceVariant = Pencil,
        outline = Pencil.copy(alpha = .32f), outlineVariant = Hairline,
    ), shapes = Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp))) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cover, modifier = Modifier.size(28.dp))
                }
                state.loadFailed -> Column(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Your notebook couldn't be opened", fontFamily = DiarySerif, fontSize = 24.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Your saved file has been kept. Try opening it again.", color = Pencil)
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
                else -> NotebookScreen(state, viewModel, captureRequest, onCaptureConsumed, practiceRequest, practiceNight, onPracticeConsumed)
            }
        }
    }
    }
}

private data class PageTurn(val entry: DreamEntry, val index: Int, val direction: Int, val page: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookScreen(state: DiaryState, model: DiaryViewModel, captureRequest: String?, onCaptureConsumed: () -> Unit,
    practiceRequest: String?, practiceNight: String?, onPracticeConsumed: () -> Unit) {
    val entry = state.current ?: return
    val configuration = LocalConfiguration.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val angle = remember { Animatable(0f) }
    var turn by remember { mutableStateOf<PageTurn?>(null) }
    var editing by remember { mutableStateOf(false) }
    var drawingMode by rememberSaveable(entry.id) { mutableStateOf(false) }
    val drawingEditor = remember(entry.id) { model.drawingEditor(entry) }
    androidx.activity.compose.BackHandler(enabled = drawingMode) { drawingMode = false; drawingEditor.preview = null }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var recallParent by rememberSaveable { mutableStateOf<String?>(null) }
    var practiceRoute by rememberSaveable { mutableStateOf("home") }
    var practiceDate by rememberSaveable { mutableStateOf<String?>(null) }
    var practiceLaunch by rememberSaveable { mutableIntStateOf(0) }
    var autoRecord by rememberSaveable { mutableStateOf(false) }
    var writingPage by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var writingPages by remember(entry.id) { mutableIntStateOf(1) }
    var openLastSheet by remember { mutableStateOf(false) }
    val hasPrevious = writingPage > 0 || state.index > 0
    val hasNext = writingPage < writingPages - 1 || state.index < state.entries.lastIndex
    fun openPanel(name: String) {
        focus.clearFocus(); autoRecord = false
        recallParent = null
        if (name == "sketch") { drawingMode = true; drawingEditor.expanded = true; panel = null; return }
        drawingMode = false
        if (name == "practice") { practiceRoute = "home"; practiceDate = null; practiceLaunch++ }
        panel = name
    }
    LaunchedEffect(practiceRequest, practiceNight) {
        if (practiceRequest != null) {
            focus.clearFocus(); showContents = false; autoRecord = false
            practiceRoute = practiceRequest.takeIf { it in setOf("home", "check", "mild", "ssild", "morning", "history", "tips", "night", "reminders") } ?: "home"
            practiceDate = practiceNight
            practiceLaunch++
            panel = "practice"
            onPracticeConsumed()
        }
    }
    LaunchedEffect(captureRequest) {
        if (captureRequest != null) {
            panel = null
            showContents = false
            if (entry.title.isNotBlank() || entry.body.isNotBlank() || entry.audio.isNotEmpty() || entry.hasDrawing) model.addDream()
            if (captureRequest == "voice") { autoRecord = true; panel = "voice" }
            onCaptureConsumed()
        }
    }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun flip(direction: Int) {
        if (turn != null || (direction > 0 && !hasNext) || (direction < 0 && !hasPrevious)) return
        focus.clearFocus()
        turn = PageTurn(entry, state.index, direction, writingPage)
        if (direction > 0 && writingPage < writingPages - 1) writingPage++
        else if (direction < 0 && writingPage > 0) writingPage--
        else {
            openLastSheet = direction < 0
            model.select(state.index + direction)
        }
        scope.launch {
            try {
                angle.snapTo(0f)
                angle.animateTo(if (direction > 0) -180f else 180f, tween(460))
            } finally {
                turn = null
                angle.snapTo(0f)
            }
        }
    }

    val pageTint by animateColorAsState(
        targetValue = notebookPaperColor(entry.lucid == true, state.preferences.night),
        animationSpec = tween(320), label = "Lucid paper tint",
    )
    // The tint belongs to this dream and extends to both leaves and the screen edges.
    CompositionLocalProvider(LocalLucidPaper provides (entry.lucid == true), LocalPaperTint provides pageTint) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("fullscreen-paper")) {
        val spread = maxWidth >= 600.dp && (isLandscape || maxWidth >= 840.dp)
        val paperTop = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
        val keyboardBottom = WindowInsets.ime.getBottom(LocalDensity.current)
        Column(Modifier.fillMaxSize().paperSurface(spread, paperTop, drawRuling = state.preferences.ruling != "Lined")) {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .heightIn(min = 52.dp).padding(start = if (spread) 28.dp else 26.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_name), Modifier.weight(1f), fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-.3).sp)
                if (entry.bookmarked) IconButton(onClick = model::toggleBookmark) {
                    Icon(DreamIcons.Bookmark, "Remove bookmark", tint = Cover, modifier = Modifier.size(19.dp))
                }
                if (editing) IconButton(onClick = { focus.clearFocus() }) {
                    Icon(Icons.Outlined.Check, "Done writing", tint = Accent, modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = { focus.clearFocus(); drawingMode = false; model.addDream() }, enabled = turn == null) {
                    Icon(Icons.Outlined.Add, "A new dream", tint = Pencil, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { openPanel("voice") }) {
                    Icon(DreamIcons.Microphone, "Speak your dream", tint = Pencil, modifier = Modifier.size(21.dp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, "Notebook options", tint = Ink, modifier = Modifier.size(23.dp).graphicsLayer { rotationZ = 90f })
                    }
                    DropdownMenu(menuExpanded, { menuExpanded = false }, modifier = Modifier.width(260.dp).testTag("notebook-menu"),
                        containerColor = ControlPaper, shape = RoundedCornerShape(20.dp), tonalElevation = 0.dp) {
                        NotebookMenu(entry.bookmarked,
                            { name -> menuExpanded = false; openPanel(name) },
                            { menuExpanded = false; model.toggleBookmark() },
                            { menuExpanded = false; focus.clearFocus(); model.addWritingPage(); writingPage = writingPages },
                            { menuExpanded = false; showDelete = true })
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.fillMaxSize().testTag(if (spread) "notebook-spread" else "notebook-portrait")
                        .pointerInput(entry.id, editing, drawingMode, turn != null) {
                            if (!editing && !drawingMode && turn == null) {
                                var distance = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { distance = 0f },
                                    onHorizontalDrag = { change, amount -> distance += amount; change.consume() },
                                    onDragEnd = { if (abs(distance) > 64.dp.toPx()) flip(if (distance < 0) 1 else -1) },
                                )
                            }
                        },
                ) {
                    Row(Modifier.fillMaxSize()) {
                        if (spread) {
                            TitlePage(entry, state.index, model::editTitle, { showDate = true },
                                { editing = it }, Modifier.weight(1f).fillMaxHeight().testTag("left-leaf"), readOnly = drawingMode)
                        }
                        WritingPage(
                            entry = entry, index = state.index, showTitle = !spread,
                            onTitle = model::editTitle, onBody = model::editBody,
                            onLucid = model::setLucid, onDate = { showDate = true },
                            onFocus = { editing = it }, onDone = { focus.clearFocus() },
                            onNext = if (hasNext && turn == null) ({ flip(1) }) else null,
                            pageIndex = writingPage, drawings = drawingEditor.visibleLayers, drawingEditor = drawingEditor, drawingActive = drawingMode,
                            onPageRequest = { writingPage = it }, onPageCount = { count ->
                                writingPages = count
                                if (openLastSheet) { writingPage = count - 1; openLastSheet = false }
                                else if (writingPage >= count) writingPage = count - 1
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag("right-leaf"),
                        )
                    }
                    turn?.let { outgoing ->
                        val forward = outgoing.direction > 0
                        Box(
                            Modifier.matchParentSize().clearAndSetSemantics {}.graphicsLayer {
                                rotationY = angle.value
                                transformOrigin = TransformOrigin(if (forward) { if (spread) .5f else 0f } else { if (spread) .5f else 1f }, .5f)
                                cameraDistance = 24 * density
                                shadowElevation = (sin(Math.toRadians(abs(angle.value).toDouble())) * 12).toFloat() * density
                            },
                            contentAlignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            // A turning leaf keeps the color of its own dream.
                            CompositionLocalProvider(LocalLucidPaper provides (outgoing.entry.lucid == true),
                                LocalPaperTint provides notebookPaperColor(outgoing.entry.lucid == true, state.preferences.night)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(if (spread) .5f else 1f).background(Paper)) {
                                if (abs(angle.value) <= 90f) {
                                    if (!forward && spread) {
                                        TitlePage(outgoing.entry, outgoing.index, {}, {}, {}, Modifier.fillMaxSize(), readOnly = true)
                                    } else {
                                        WritingPage(outgoing.entry, outgoing.index, !spread, {}, {}, {}, {}, {}, {}, null,
                                            Modifier.fillMaxSize(), readOnly = true, pageIndex = outgoing.page)
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize().paperSurface())
                                }
                            }
                            }
                        }
                    }

                }
                if (drawingMode && turn == null) Box(Modifier.align(if (spread) Alignment.BottomStart else Alignment.BottomEnd).fillMaxWidth(if (spread) .5f else 1f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    DrawingToolbar(drawingEditor, writingPage, onDone = { drawingMode = false; drawingEditor.preview = null })
                }
            }
            Column(Modifier.fillMaxWidth().offset { IntOffset(0, -keyboardBottom) }
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = if (spread) 28.dp else 22.dp, vertical = 8.dp)) {
                if (state.saveStatus == SaveStatus.Failed) {
                    TextButton(onClick = model::retrySave, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Couldn't save — tap to retry", color = Color(0xFF923D32))
                    }
                }
                @Composable fun SheetNavigation(modifier: Modifier = Modifier) {
                    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        PageButton("Previous page", hasPrevious && turn == null, false) { flip(-1) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 88.dp)) {
                            Text(if (writingPages > 1) "${state.index + 1} · ${writingPage + 1}/$writingPages" else "%02d / %02d".format(state.index + 1, state.entries.size),
                                Modifier.testTag("sheet-counter").semantics { contentDescription = "Dream ${state.index + 1} of ${state.entries.size}, sheet ${writingPage + 1} of $writingPages" },
                                fontSize = 12.sp, color = Pencil, letterSpacing = .7.sp)
                            if (state.saveStatus == SaveStatus.Saving) Text("Saving…", fontSize = 10.sp, color = Pencil,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        PageButton("Next page", hasNext && turn == null, true) { flip(1) }
                    }
                }
                @Composable fun JournalDock(modifier: Modifier = Modifier) {
                    Surface(modifier, color = ControlPaper, shape = RoundedCornerShape(30.dp),
                        border = androidx.compose.foundation.BorderStroke(.5.dp, Hairline), shadowElevation = 2.dp) {
                        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            @Composable fun DockItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, tag: String, action: () -> Unit) {
                                Column(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (selected) AccentWash else Color.Transparent).clickable(onClick = action)
                                    .padding(vertical = 6.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, if (label == "Dreams") "All dreams" else label, Modifier.size(20.dp), tint = if (selected) Accent else Pencil)
                                    Text(label, fontSize = 11.sp, color = if (selected) Accent else Pencil)
                                }
                            }
                            DockItem("Dreams", DreamIcons.Book, false, "open-dreams") { focus.clearFocus(); drawingMode = false; showContents = true }
                            DockItem("Practice", DreamIcons.Moon, false, "open-practice") { openPanel("practice") }
                            DockItem("Write", DreamIcons.Pen, !drawingMode, "write-mode") { drawingMode = false; drawingEditor.preview = null }
                            DockItem("Draw", DreamIcons.Highlighter, drawingMode, "draw-mode") { focus.clearFocus(); drawingMode = true; drawingEditor.expanded = true }
                        }
                    }
                }
                if (spread) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SheetNavigation(Modifier.weight(1f))
                    JournalDock(Modifier.width(320.dp))
                } else {
                    SheetNavigation(Modifier.fillMaxWidth().height(34.dp))
                    Spacer(Modifier.height(6.dp))
                    JournalDock(Modifier.fillMaxWidth())
                }
            }
        }
    }

    }
    if (showContents) DreamLibrary(state, model, onPractice = { showContents = false; openPanel("practice") }) { showContents = false }
    key(entry.id) { when (panel) {
        "practice" -> key(practiceLaunch) { LucidPracticePanel(state, model, practiceRoute, practiceDate, onWrite = { voice, date ->
            val current = model.state.value.current
            if (current == null || current.title.isNotBlank() || current.body.isNotBlank() || current.audio.isNotEmpty() || current.hasDrawing) model.addDream()
            model.setDate(date)
            autoRecord = voice
            panel = if (voice) "voice" else null
        }, onMorningSettings = { openPanel("recall") }) { panel = null } }
        "details" -> DetailsPanel(entry, model) { panel = null }
        "voice" -> VoicePanel(entry, model, { panel = null }, autoRecord)
        "sketch" -> LaunchedEffect(Unit) { panel = null; drawingMode = true }
        "settings" -> NotebookSettingsPanel(state.preferences, model::preferences,
            onMorning = { openPanel("recall"); recallParent = "settings" },
            onReality = { openPanel("settings-reality") }, onUpdates = { openPanel("updates") }) { panel = null }
        "updates" -> AppUpdatePanel(model.updates, onBack = { panel = "settings" }) { panel = null }
        "settings-reality" -> NotebookReminderPanel(model, onBack = { panel = "settings" },
            onMorning = { openPanel("recall"); recallParent = "settings-reality" }) { panel = null }
        "recall" -> RecallPanel(state.preferences, model::preferences,
            onBack = recallParent?.let { parent -> ({ panel = parent; recallParent = null }) }) { panel = null; recallParent = null }
    } }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false }, containerColor = Paper,
            title = { Text("Delete this dream?", fontFamily = DiarySerif) },
            text = { Text("This removes “${entry.title.ifBlank { "Untitled dream" }}” from your notebook.") },
            confirmButton = { TextButton(onClick = { focus.clearFocus(); model.deleteCurrent(); showDelete = false }) { Text("Delete dream") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Keep dream") } },
        )
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = entry.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { model.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showDate = false
            }) { Text("Keep date") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun PageButton(label: String, enabled: Boolean, forward: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(if (forward) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft, label,
            tint = Ink.copy(alpha = if (enabled) 1f else .25f), modifier = Modifier.size(18.dp))
    }
}

/** Paper texture and ruling are drawn locally; no image downloads or bitmap scaling. */
@Composable
private fun Modifier.paperSurface(spread: Boolean = false, topInset: Dp = 0.dp, drawRuling: Boolean = true): Modifier {
    val paper = Paper
    val ink = Ink
    val settings = LocalNotebookPreferences.current
    val rule = PaperRule
    val accent = Cover
    val marginColor = when {
        LocalLucidPaper.current -> if (settings.night) Color(0xFF8C749E).copy(alpha = .45f) else Color(0xFFBCA0C5).copy(alpha = .5f)
        settings.night -> accent.copy(alpha = .45f)
        else -> Color(0xFFD8B4AD).copy(alpha = .5f)
    }
    return background(paper).drawWithCache {
        val random = Random(47)
        val flecks = List(180) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
        val start = if (spread) topInset.toPx() + 112.dp.toPx() else 64.dp.toPx()
        val bottom = size.height - (if (spread) 64.dp else 82.dp).toPx()
        val step = (settings.textSize + 14).sp.toPx()
        onDrawBehind {
            var y = start
            while (drawRuling && y < bottom && settings.ruling != "Plain") {
                if (settings.ruling == "Dotted") {
                    var x = 18.dp.toPx()
                    while (x < size.width) { drawCircle(rule.copy(alpha = .7f), .65.dp.toPx(), Offset(x, y)); x += 16.dp.toPx() }
                } else drawLine(rule.copy(alpha = .55f), Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
                y += if (settings.ruling == "Dotted") step / 2 else step
            }
            val top = if (spread) topInset.toPx() + 68.dp.toPx() else topInset.toPx() + 72.dp.toPx()
            if (settings.ruling == "Lined") {
                val margin = marginColor
                val marginX = (if (spread) 28.dp else 18.dp).toPx()
                drawLine(margin, Offset(marginX, top), Offset(marginX, bottom), .65.dp.toPx())
                if (spread) drawLine(margin, Offset(size.width / 2 + 28.dp.toPx(), top), Offset(size.width / 2 + 28.dp.toPx(), bottom), .65.dp.toPx())
            }
            if (spread) {
                val spine = size.width / 2
                val shade = 14.dp.toPx()
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, .45f to ink.copy(alpha = .035f),
                    .5f to ink.copy(alpha = .09f), .58f to ink.copy(alpha = .025f), 1f to Color.Transparent,
                    startX = spine - shade, endX = spine + shade), topLeft = Offset(spine - shade, 0f), size = Size(shade * 2, size.height))
            }
            flecks.forEach { drawCircle(ink.copy(alpha = .025f), .4.dp.toPx(), it) }
        }
    }
}

@Composable
private fun TitlePage(
    entry: DreamEntry, index: Int, onTitle: (String) -> Unit, onDate: () -> Unit,
    onFocus: (Boolean) -> Unit, modifier: Modifier = Modifier, readOnly: Boolean = false,
) {
    val ruling = remember { PaperRuling() }
    val settings = LocalNotebookPreferences.current
    val ruleColor = PaperRule
    val step = with(LocalDensity.current) { (settings.textSize + 14).sp.toPx() }
    Box(modifier.then(ruling.pageModifier(settings.ruling == "Lined", ruleColor.copy(alpha = .55f), step, titlePage = true))) {
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            .verticalScroll(rememberScrollState())
            .padding(start = 48.dp, end = 32.dp, top = 16.dp, bottom = 42.dp)) {
            EntryDate(entry.date, onDate, enabled = !readOnly)
            Spacer(Modifier.height(18.dp))
            DreamTitle(entry.title, onTitle, onFocus, readOnly, ruling = ruling)
        }
        Text(String.format(Locale.ROOT, "%02d", index * 2 + 1),
            Modifier.align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                .padding(start = 48.dp, bottom = 14.dp), color = Pencil, fontSize = 11.sp)
    }
}

@Composable
private fun WritingPage(
    entry: DreamEntry, index: Int, showTitle: Boolean,
    onTitle: (String) -> Unit, onBody: (String) -> Unit, onLucid: (Boolean) -> Unit,
    onDate: () -> Unit, onFocus: (Boolean) -> Unit, onDone: () -> Unit, onNext: (() -> Unit)?,
    modifier: Modifier = Modifier, readOnly: Boolean = false,
    pageIndex: Int = 0, onPageCount: (Int) -> Unit = {},
    onPageRequest: (Int) -> Unit = {},
    drawings: List<DiaryDrawing> = entry.pageDrawings, drawingEditor: DrawingEditor? = null, drawingActive: Boolean = false,
) {
    val paper = Paper
    val settings = LocalNotebookPreferences.current
    val ruling = remember { PaperRuling() }
    val ruleColor = PaperRule
    val step = with(LocalDensity.current) { (settings.textSize + 14).sp.toPx() }
    val foldShade = when {
        LocalLucidPaper.current -> if (settings.night) Color(0xFF514260) else Color(0xFFC9B9DA)
        settings.night -> Color(0xFF4E4653)
        else -> Color(0xFFD6CEBF)
    }
    BoxWithConstraints(modifier.then(ruling.pageModifier(settings.ruling == "Lined", ruleColor.copy(alpha = .55f), step))) {
        PageDrawingCanvas(drawings, pageIndex, behind = true, modifier = Modifier.fillMaxSize().padding(bottom = 48.dp))
        val compact = maxHeight < 360.dp
        val cameraTop = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
        val bodySize = settings.textSize.sp
        val bodyLeading = (settings.textSize + 14).sp
        val writingFont = when (settings.font) { "Handwritten" -> Handwritten; "Simple" -> FontFamily.SansSerif; else -> DiarySerif }
        val writingStyle = TextStyle(fontFamily = writingFont, color = Ink, fontSize = bodySize, lineHeight = bodyLeading,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(if (showTitle) WindowInsetsSides.Horizontal else WindowInsetsSides.End))
            .padding(start = if (showTitle) 32.dp else 48.dp, end = if (showTitle) 24.dp else 32.dp,
                top = if (showTitle && !compact) 8.dp else 8.dp, bottom = 48.dp)) {
            if (showTitle) {
                if (!compact) {
                    EntryDate(entry.date, onDate, enabled = !readOnly && !drawingActive)
                    Spacer(Modifier.height(12.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                DreamTitle(entry.title, onTitle, onFocus, readOnly || drawingActive, large = !compact, ruling = ruling)
                Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
            }
            if (!showTitle) {
                Text("DREAM ENTRY", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Pencil, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(12.dp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                // The sheet keeps its physical dimensions; the editor scrolls above the keyboard.
                val lines = with(density) { (maxHeight.toPx() / bodyLeading.toPx()).toInt() }.coerceAtLeast(1)
                val measurer = rememberTextMeasurer()
                fun split(value: String) = paginatePaper(value) { remaining ->
                    val layout = measurer.measure(AnnotatedString(remaining), writingStyle,
                        constraints = Constraints(maxWidth = width), maxLines = lines, overflow = TextOverflow.Clip)
                    layout.getLineEnd((layout.lineCount - 1).coerceAtLeast(0), visibleEnd = false)
                }
                val slices = remember(entry.body, width, lines, writingStyle) { split(entry.body) }
                val totalPages = maxOf(slices.size, (drawings.maxOfOrNull { it.page } ?: 0) + 1)
                LaunchedEffect(entry.id, totalPages) { if (!readOnly) onPageCount(totalPages) }
                // A drawing retains its sheet even when a wider layout fits the text into fewer sheets.
                val effectiveBody = if (pageIndex >= slices.size) entry.body + "\u000C".repeat(pageIndex - slices.lastIndex) else entry.body
                val slice = slices.getOrNull(pageIndex) ?: PaperSlice(effectiveBody.length, effectiveBody.length)
                val text = effectiveBody.substring(slice.start, slice.end)
                var caret by remember(entry.id) { mutableStateOf(TextRange(0)) }
                var composing by remember(entry.id) { mutableStateOf<TextRange?>(null) }
                fun local(range: TextRange) = TextRange((range.start - slice.start).coerceIn(0, text.length), (range.end - slice.start).coerceIn(0, text.length))
                BasicTextField(
                    value = TextFieldValue(text, local(caret), composing?.takeIf { it.min >= slice.start && it.max <= slice.end }?.let(::local)),
                    onValueChange = { value ->
                        caret = TextRange(slice.start + value.selection.start, slice.start + value.selection.end)
                        composing = value.composition?.let { TextRange(slice.start + it.start, slice.start + it.end) }
                        if (value.text != text) {
                            val updated = effectiveBody.substring(0, slice.start) + value.text + effectiveBody.substring(slice.end)
                            onBody(updated)
                            val pages = split(updated)
                            val target = pages.indexOfFirst { caret.end >= it.start && caret.end <= it.end }.coerceAtLeast(0)
                            if (target != pageIndex) onPageRequest(target)
                        }
                    },
                    readOnly = readOnly || drawingActive, enabled = !readOnly && !drawingActive,
                    modifier = Modifier.fillMaxSize().imePadding().then(ruling.textModifier(PaperRuling.Part.Body)).testTag("dream-body").semantics { contentDescription = "Dream entry" }
                        .onFocusChanged { if (!readOnly) onFocus(it.isFocused) },
                    textStyle = writingStyle,
                    onTextLayout = { ruling.textLayout(PaperRuling.Part.Body, it) },
                    cursorBrush = SolidColor(Ink), minLines = 1,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(if (pageIndex > 0) "The dream continues…" else if (settings.prompts)
                                RecallPrompts[entry.date.dayOfYear % RecallPrompts.size] else "I remember…",
                                style = writingStyle.copy(fontStyle = FontStyle.Italic, color = Pencil.copy(alpha = .7f)))
                            inner()
                        }
                    },
                )
            }
        }
        PageDrawingCanvas(drawings, pageIndex, behind = false, modifier = Modifier.fillMaxSize().padding(bottom = 48.dp), editor = drawingEditor, active = drawingActive && !readOnly)
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(if (showTitle) WindowInsetsSides.Horizontal else WindowInsetsSides.End))
            .padding(start = if (showTitle) 32.dp else 48.dp, end = 34.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically) {
            LucidityNote(entry.lucid, onLucid, enabled = !readOnly)
            Spacer(Modifier.weight(1f))
            Text(if (pageIndex > 0) "${String.format(Locale.ROOT, "%02d", index * 2 + 2)} · ${pageIndex + 1}" else String.format(Locale.ROOT, "%02d", index * 2 + 2),
                fontFamily = DiarySerif, color = Pencil, fontSize = 11.sp)
        }
        if (onNext != null && !readOnly) {
            Box(Modifier.align(Alignment.BottomEnd).size(48.dp).clip(RoundedCornerShape(bottomEnd = 4.dp))
                .clickable(onClickLabel = "Turn page", onClick = onNext).semantics { contentDescription = "Turn page" }) {
                Canvas(Modifier.align(Alignment.BottomEnd).size(32.dp)) {
                    val fold = Path().apply { moveTo(size.width, 0f); lineTo(0f, size.height); lineTo(size.width, size.height); close() }
                    drawPath(fold, Brush.linearGradient(listOf(foldShade, paper)))
                }
            }
        }
    }
}

@Composable
private fun EntryDate(date: LocalDate, onClick: () -> Unit, enabled: Boolean) {
    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
        Modifier.clickable(enabled = enabled, onClickLabel = "Change dream date", onClick = onClick).padding(vertical = 10.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Accent)
}

@Composable
private fun DreamTitle(value: String, onChange: (String) -> Unit, onFocus: (Boolean) -> Unit, readOnly: Boolean, large: Boolean = false, ruling: PaperRuling? = null) {
    val focus = LocalFocusManager.current
    val titleStyle = TextStyle(fontFamily = DiarySerif, color = Ink,
        fontSize = if (large) 30.sp else 32.sp, lineHeight = if (large) 40.sp else 42.sp, letterSpacing = (-.7).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
    BasicTextField(
        value = value, onValueChange = onChange, readOnly = readOnly, enabled = !readOnly,
        modifier = Modifier.fillMaxWidth().then(ruling?.textModifier(PaperRuling.Part.Title) ?: Modifier).testTag("dream-title").semantics { contentDescription = "Dream title" }
            .onFocusChanged { if (!readOnly) onFocus(it.isFocused) },
        textStyle = titleStyle,
        onTextLayout = { ruling?.textLayout(PaperRuling.Part.Title, it) },
        cursorBrush = SolidColor(Ink), maxLines = if (large) 3 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("Name this dream…", style = titleStyle.copy(color = Pencil.copy(alpha = .65f)))
                inner()
            }
        },
    )
}

@Composable
private fun LucidityNote(lucid: Boolean?, onChange: (Boolean) -> Unit, enabled: Boolean) {
    val pen = Pencil
    // A small ink mark on the page, with a full-height touch target.
    Row(Modifier.heightIn(min = 44.dp).testTag("lucid-marker")
        .semantics { stateDescription = when (lucid) { true -> "Lucid"; false -> "Not lucid"; null -> "Not recorded" } }
        .toggleable(value = lucid == true, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(12.dp)) {
            val inset = .75.dp.toPx()
            drawRoundRect(pen.copy(alpha = .65f), topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(.8.dp.toPx()))
            if (lucid == true) {
                val tick = Path().apply {
                    moveTo(size.width * .24f, size.height * .51f)
                    lineTo(size.width * .43f, size.height * .69f)
                    lineTo(size.width * .77f, size.height * .31f)
                }
                drawPath(tick, pen, style = Stroke(1.25.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        Text("Lucid", color = pen, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun NotebookMenu(bookmarked: Boolean, onPanel: (String) -> Unit, onBookmark: () -> Unit,
    onPage: () -> Unit, onDelete: () -> Unit) {
    Text("THIS DREAM", Modifier.padding(start = 18.dp, top = 8.dp, bottom = 6.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, color = Pencil)
    DropdownMenuItem(text = { Text("Dream details", fontSize = 14.sp) },
        leadingIcon = { Icon(DreamIcons.Info, null, Modifier.size(19.dp), tint = Pencil) }, onClick = { onPanel("details") })
    DropdownMenuItem(text = { Text(if (bookmarked) "Remove bookmark" else "Bookmark this dream", fontSize = 14.sp) },
        leadingIcon = { Icon(DreamIcons.Bookmark, null, Modifier.size(19.dp), tint = Pencil) }, onClick = onBookmark)
    DropdownMenuItem(text = { Text("Continue on a new page", fontSize = 14.sp) },
        leadingIcon = { Icon(DreamIcons.Book, null, Modifier.size(19.dp), tint = Pencil) }, onClick = onPage)
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Hairline)
    DropdownMenuItem(text = { Text("Settings", fontSize = 14.sp) },
        leadingIcon = { Icon(DreamIcons.Settings, null, Modifier.size(19.dp), tint = Pencil) }, onClick = { onPanel("settings") })
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Hairline)
    DropdownMenuItem(text = { Text("Delete this dream", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(19.dp)) },
        colors = MenuDefaults.itemColors(textColor = Pencil, leadingIconColor = Pencil), onClick = onDelete)
}
