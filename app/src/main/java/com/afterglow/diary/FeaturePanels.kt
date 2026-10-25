package com.afterglow.diary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NotebookPanel(title: String, onClose: () -> Unit, onDismiss: () -> Unit = onClose,
    onBack: (() -> Unit)? = null, background: Color? = null, backLabel: String = "Back to practice",
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val night = LocalNotebookPreferences.current.night
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !night
                    isAppearanceLightNavigationBars = !night
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = background ?: PanelPaper) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 860.dp).fillMaxSize().padding(horizontal = 22.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, backLabel, tint = Accent, modifier = Modifier.size(21.dp))
                        }
                        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        IconButton(onClick = onClose, modifier = Modifier.size(44.dp).padding(6.dp).background(Ink.copy(alpha = .06f), CircleShape)) {
                            Icon(Icons.Outlined.Close, "Close", tint = Pencil, modifier = Modifier.size(18.dp))
                        }
                    }
                    content()
                }
            }
        }
    }
}

@Composable
internal fun DreamLibrary(state: DiaryState, model: DiaryViewModel, onPractice: () -> Unit, onClose: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    var dates by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    val fromDate = runCatching { LocalDate.parse(from) }.getOrNull()
    val untilDate = runCatching { LocalDate.parse(until) }.getOrNull()
    fun openDream(entry: DreamEntry) { model.select(state.entries.indexOfFirst { it.id == entry.id }); onClose() }
    val validDates = !dates || ((from.isEmpty() || fromDate != null) && (until.isEmpty() || untilDate != null) &&
        (fromDate == null || untilDate == null || fromDate <= untilDate))
    val results = if (!validDates) emptyList() else searchDreams(state.entries, query,
        when (filter) { "Lucid" -> true; "Non-lucid" -> false; else -> null }, bookmarks,
        if (dates) fromDate else null, if (dates) untilDate else null)
    NotebookPanel("Dream Diary", onClose, background = LibraryPaper) {
        val focus = LocalFocusManager.current
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("dream-library")) {
            val wide = maxWidth >= 680.dp
            fun newDream() { focus.clearFocus(); model.addDream(); onClose() }
            @Composable fun Controls() {
                LibrarySearch(query, { query = it }, { focus.clearFocus() })
                LibraryFilters(filter, { filter = it }, bookmarks, { bookmarks = !bookmarks }, dates, { dates = !dates }, wrap = wide)
                if (dates) {
                    Row(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(from, { from = it }, Modifier.weight(1f).testTag("library-date-from"), label = { Text("From") },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) }, isError = from.isNotEmpty() && fromDate == null, singleLine = true)
                        OutlinedTextField(until, { until = it }, Modifier.weight(1f).testTag("library-date-until"), label = { Text("Through") },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) }, isError = until.isNotEmpty() && untilDate == null, singleLine = true)
                    }
                    if (fromDate != null && untilDate != null && fromDate > untilDate) Text("The start date must be before the end date.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            Column(Modifier.fillMaxSize()) {
                if (!wide && !keyboardVisible) LibraryHeading(onNew = ::newDream)
                if (!keyboardVisible) LibraryTabs(tab) { if (it == 3) onPractice() else tab = it }
                when (tab) {
                    0 -> if (wide) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            Column(Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                                if (!keyboardVisible) LibraryHeading(compact = true, onNew = ::newDream)
                                Controls()
                            }
                            Column(Modifier.weight(1f)) {
                                LibraryEntries(results, state.selectedId, query.isNotBlank() || filter != "All" || bookmarks || dates,
                                    compact = true, onClear = { query = ""; filter = "All"; bookmarks = false; dates = false }, onOpen = ::openDream)
                            }
                        }
                    } else {
                        Controls()
                        LibraryEntries(results, state.selectedId, query.isNotBlank() || filter != "All" || bookmarks || dates,
                            onClear = { query = ""; filter = "All"; bookmarks = false; dates = false }, onOpen = ::openDream)
                    }
                    1 -> PatternPages(state.entries) { pattern -> query = "\"$pattern\""; tab = 0; filter = "All"; bookmarks = false; dates = false }
                    2 -> DictionaryPages(state, model) { name -> query = "\"$name\""; tab = 0; filter = "All"; bookmarks = false; dates = false }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PatternPages(entries: List<DreamEntry>, onPattern: (String) -> Unit) {
    val written = entries.filter { it.title.isNotBlank() || it.body.isNotBlank() || it.audio.isNotEmpty() || it.hasDrawing }
    val known = written.count { it.lucid != null }
    val lucid = written.count { it.lucid == true }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text("Threads through your dreams", Modifier.padding(top = 18.dp), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Ink)
            Text("Connections from the people, places, and symbols you record. Their meaning belongs to you.", Modifier.padding(vertical = 12.dp), color = Pencil)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("${written.size}", "dreams")
                Metric("${written.map { it.date }.distinct().size}", "nights")
                Metric(if (known == 0) "—" else "${lucid * 100 / known}%", "lucid¹")
            }
            Text("¹ Of $known dreams with a lucid choice. No targets, no streaks.", Modifier.padding(top = 12.dp), color = Pencil, fontSize = 11.sp)
            PanelLabel("The last eight weeks")
            val monday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
            val counts = (7 downTo 0).map { i ->
                val start = monday.minusWeeks(i.toLong()); written.count { !it.date.isBefore(start) && it.date.isBefore(start.plusWeeks(1)) }
            }
            val largest = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                counts.forEachIndexed { i, n ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("$n", fontSize = 11.sp, color = Pencil)
                        Box(Modifier.fillMaxWidth().height((8 + 72 * n / largest).dp).background(Pencil.copy(alpha = .25f)))
                        Text(monday.minusWeeks((7 - i).toLong()).format(DateTimeFormatter.ofPattern("d/M")), fontSize = 9.sp, color = Pencil)
                    }
                }
            }
        }
        listOf("Recurring symbols" to recurringPatterns(written) { it.symbols },
            "Familiar people" to recurringPatterns(written) { it.people },
            "Places you return to" to recurringPatterns(written) { it.places }).forEach { (title, patterns) ->
            item { PanelLabel(title) }
            if (patterns.isEmpty()) item { Text("Appears here after you note it in two dreams.", color = Pencil, fontSize = 13.sp) }
            items(patterns) { pattern ->
                Row(Modifier.fillMaxWidth().clickable { onPattern(pattern.label) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pattern.label, Modifier.weight(1f), fontFamily = DiarySerif, fontSize = 20.sp, color = Ink)
                    Text("${pattern.count} dreams  →", color = Pencil, fontSize = 12.sp)
                }
            }
        }
        item {
            val lucidDreams = written.filter { it.lucid == true }
            val awareness = lucidDreams.map { it.awareness }.filter { it > 0 }
            val control = lucidDreams.map { it.control }.filter { it > 0 }
            if (awareness.isNotEmpty() || control.isNotEmpty()) {
                PanelLabel("Lucid notes")
                Text("Average awareness: ${if (awareness.isEmpty()) "—" else String.format(Locale.getDefault(), "%.1f / 5", awareness.average())}\n" +
                    "Average control: ${if (control.isEmpty()) "—" else String.format(Locale.getDefault(), "%.1f / 5", control.average())}", color = Ink)
            }
        }
    }
}

@Composable private fun Metric(value: String, caption: String) {
    Column { Text(value, fontFamily = DiarySerif, fontSize = 32.sp, color = Ink); Text(caption, color = Pencil, fontSize = 12.sp) }
}
@Composable internal fun PanelLabel(text: String) { SectionCaption(text) }
@Composable private fun EmptyNote(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 28.dp)) {
        Text(title, fontFamily = DiarySerif, fontSize = 22.sp, color = Ink)
        Text(subtitle, Modifier.padding(top = 10.dp), color = Pencil)
    }
}

@Composable
private fun ColumnScope.DictionaryPages(state: DiaryState, model: DiaryViewModel, onDreams: (String) -> Unit) {
    var editor by remember { mutableStateOf<SymbolMeaning?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Your own dream dictionary", Modifier.weight(1f), fontFamily = DiarySerif, fontSize = 21.sp, color = Ink)
        TextButton(onClick = { editor = SymbolMeaning("", "") }) { Text("+ Symbol") }
    }
    Text("What a symbol means to you, in your words.", Modifier.padding(vertical = 12.dp), color = Pencil)
    val symbols = (state.meanings.map { it.name } + state.entries.flatMap { it.symbols }).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (symbols.isEmpty()) item { EmptyNote("A dictionary only you can write", "Add a symbol here, or note one in a dream’s details.") }
        items(symbols) { name ->
            val meaning = state.meanings.firstOrNull { it.name.equals(name, true) }
            val linked = state.entries.count { d -> d.symbols.any { it.equals(name, true) } }
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(name, fontFamily = DiarySerif, fontSize = 23.sp, color = Ink)
                Text(meaning?.meaning?.ifBlank { "No meaning written yet." } ?: "No meaning written yet.", Modifier.padding(top = 8.dp), color = Pencil)
                Row {
                    TextButton(onClick = { editor = meaning ?: SymbolMeaning(name, "") }) { Text("Write meaning") }
                    TextButton(onClick = { onDreams(name) }, enabled = linked > 0) { Text("$linked dreams →") }
                    if (meaning != null) TextButton(onClick = { deleting = name }) { Text("Remove") }
                }
            }
            HorizontalDivider(color = Hairline)
        }
    }
    editor?.let { selected ->
        var name by remember(selected) { mutableStateOf(selected.name) }
        var meaning by remember(selected) { mutableStateOf(selected.meaning) }
        AlertDialog(onDismissRequest = { editor = null }, containerColor = Paper,
            title = { Text("A personal meaning", fontFamily = DiarySerif) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, Modifier.testTag("symbol-name"), label = { Text("Symbol") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(meaning, { meaning = it }, Modifier.testTag("symbol-meaning"), label = { Text("What it means to me") }, minLines = 4)
            } },
            confirmButton = { TextButton(onClick = { model.saveMeaning(name, meaning, selected.name.takeIf { it.isNotBlank() }); editor = null }, enabled = name.isNotBlank()) { Text("Keep meaning") } },
            dismissButton = { TextButton(onClick = { editor = null }) { Text("Cancel") } })
    }
    deleting?.let { name -> AlertDialog(onDismissRequest = { deleting = null }, containerColor = Paper,
        title = { Text("Remove this meaning?") }, text = { Text("Dreams that mention $name will stay in your notebook.") },
        confirmButton = { TextButton(onClick = { model.deleteMeaning(name); deleting = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}

@Composable
internal fun DetailsPanel(entry: DreamEntry, model: DiaryViewModel, onClose: () -> Unit) {
    var symbols by rememberSaveable(entry.id) { mutableStateOf(entry.symbols.joinToString(", ")) }
    var people by rememberSaveable(entry.id) { mutableStateOf(entry.people.joinToString(", ")) }
    var places by rememberSaveable(entry.id) { mutableStateOf(entry.places.joinToString(", ")) }
    var awareness by rememberSaveable(entry.id) { mutableIntStateOf(entry.awareness) }
    var control by rememberSaveable(entry.id) { mutableIntStateOf(entry.control) }
    var technique by rememberSaveable(entry.id) { mutableStateOf(entry.technique) }
    var minutes by rememberSaveable(entry.id) { mutableStateOf(entry.lucidMinutes) }
    LaunchedEffect(symbols, people, places, awareness, control, technique, minutes) {
        model.setDetails(symbols, people, places, awareness, control, technique, minutes)
    }
    fun save() { model.setDetails(symbols, people, places, awareness, control, technique, minutes); onClose() }
    NotebookPanel("Dream Diary", ::save) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            PageHeading("Dream details", "Keep the details you want to remember.")
            Text("Separate names with commas. These connect your index, patterns, and personal dictionary.", color = Pencil, fontSize = 13.sp)
            OutlinedTextField(symbols, { symbols = it }, Modifier.fillMaxWidth().padding(top = 16.dp).testTag("detail-symbols"), label = { Text("Symbols · e.g. water, doors") })
            OutlinedTextField(people, { people = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("People") })
            OutlinedTextField(places, { places = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Places") })
            if (entry.lucid == true) {
                PanelLabel("Inside the lucid dream")
                Text("Optional. Leave a scale unmarked if you’re unsure.", color = Pencil, fontSize = 13.sp)
                RatingRow("Awareness", awareness, { awareness = it }, "Fleeting recognition → fully aware")
                RatingRow("Dream control", control, { control = it }, "Observing → directing the dream")
                OutlinedTextField(technique, { technique = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Technique or trigger") }, placeholder = { Text("Reality check, MILD, spontaneous…") })
                OutlinedTextField(minutes, { minutes = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Estimated lucid minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
            Button(onClick = ::save, Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Keep notes") }
        }
    }
}
@Composable private fun RatingRow(title: String, value: Int, onChange: (Int) -> Unit, caption: String) {
    Text(title, Modifier.padding(top = 20.dp), color = Ink)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { number -> FilterChip(value == number, { onChange(if (value == number) 0 else number) }, label = { Text("$number") }, modifier = Modifier.weight(1f)) }
    }
    Text(caption, color = Pencil, fontSize = 11.sp)
}

@Composable
internal fun RecallPanel(settings: NotebookPreferences, onChange: (NotebookPreferences) -> Unit,
    onBack: (() -> Unit)? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    var prompt by rememberSaveable { mutableIntStateOf(0) }
    var hour by rememberSaveable { mutableStateOf(settings.reminderHour.toString().padStart(2, '0')) }
    var minute by rememberSaveable { mutableStateOf(settings.reminderMinute.toString().padStart(2, '0')) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val latest by rememberUpdatedState(settings)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onChange(latest.copy(reminder = true))
    }
    NotebookPanel("Morning reminders", onClose, onDismiss = onBack ?: onClose, onBack = onBack, backLabel = "Back to settings") {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            PageHeading("Morning recall")
            Text("Stay still for a moment. Start with one image, then follow it backwards. A fragment is enough.", color = Pencil, lineHeight = 24.sp)
            Text(RecallPrompts[prompt], Modifier.padding(top = 28.dp, bottom = 16.dp), fontFamily = DiarySerif, fontSize = 26.sp, color = Ink, fontStyle = FontStyle.Italic)
            TextButton(onClick = { prompt = (prompt + 1) % RecallPrompts.size }) { Text("Another prompt") }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Prompts on empty pages", Modifier.weight(1f), color = Ink)
                Switch(settings.prompts, { onChange(settings.copy(prompts = it)) })
            }
            PanelLabel("Morning reminder")
            Text("One quiet reminder around your chosen time. No streaks or missed-day messages.", color = Pencil, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Morning reminder", Modifier.weight(1f), color = Ink)
                Switch(settings.reminder, { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else { denied = false; onChange(settings.copy(reminder = enabled)) }
                })
            }
            if (denied) Text("Notifications are disabled. You can enable them in Android’s app settings.", color = Pencil, fontSize = 12.sp)
            val h = hour.toIntOrNull(); val m = minute.toIntOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Hour · 0–23") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = h !in 0..23)
                OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Minute · 0–59") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = m !in 0..59)
            }
            TextButton(onClick = { onChange(settings.copy(reminderHour = h!!, reminderMinute = m!!)) }, enabled = h in 0..23 && m in 0..59) {
                Text("Keep reminder time")
            }
            PanelLabel("Catch a dream quickly")
            Text("Add the Dream Diary widget from your launcher, or add “Catch a dream” to Android’s Quick Settings. The tile opens recording after you unlock; the widget works on the lock screen where Android supports it.", color = Pencil, lineHeight = 24.sp)
            val widgets = android.appwidget.AppWidgetManager.getInstance(context)
            if (widgets.isRequestPinAppWidgetSupported) TextButton(onClick = {
                widgets.requestPinAppWidget(android.content.ComponentName(context, CaptureWidget::class.java), null, null)
            }) { Text("Add capture widget") }
            if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = {
                context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                    android.content.ComponentName(context, DreamCaptureTile::class.java), "Catch a dream",
                    android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_moon), context.mainExecutor) { }
            }) { Text("Add Quick Settings tile") }
        }
    }
}
