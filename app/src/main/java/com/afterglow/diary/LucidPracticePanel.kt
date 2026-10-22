package com.afterglow.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun LucidPracticePanel(state: DiaryState, model: DiaryViewModel, initialRoute: String = "home",
    initialNight: String? = null, onWrite: (Boolean, LocalDate) -> Unit, onMorningSettings: () -> Unit, onClose: () -> Unit) {
    val practice by model.lucid.state.collectAsStateWithLifecycle()
    var page by rememberSaveable(initialRoute, initialNight) { mutableStateOf(initialRoute) }
    var nightDate by rememberSaveable(initialRoute, initialNight) { mutableStateOf(initialNight ?: upcomingPracticeNight().toString()) }
    var reviewDate by rememberSaveable(initialRoute, initialNight) { mutableStateOf(initialNight ?: LocalDate.now().toString()) }
    val data = practice.data
    fun go(route: String) { page = route }
    BackHandler(page != "home") { page = "home" }
    NotebookPanel(if (page == "home") "Dream Diary" else "Practice", onClose, onDismiss = { if (page == "home") onClose() else page = "home" },
        onBack = if (page != "home") ({ page = "home" }) else null) {
        if (data == null) {
            PracticeLabel("Your practice notes are safe")
            Text(practice.error ?: "Opening your practice notes…", color = Pencil)
            TextButton(onClick = model.lucid::refresh) { Text("Try again") }
        } else {
            practice.error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
            key(page) {
                when (page) {
                    "check" -> RealityCheckPractice(data, model.lucid, upcomingPracticeNight())
                    "mild" -> MildPractice(state.entries, data, model.lucid, LocalDate.parse(nightDate))
                    "ssild" -> SensoryPractice(model.lucid, LocalDate.parse(nightDate))
                    "signs" -> DreamSignsPractice(state.entries, data, model.lucid)
                    "intention" -> IntentionPractice(data, model.lucid, LocalDate.parse(nightDate)) { go("mild") }
                    "morning" -> MorningPractice(state.entries, data, model.lucid, LocalDate.parse(reviewDate), { reviewDate = it.toString() }, onWrite, onMorningSettings)
                    "history" -> HistoryPractice(state.entries, data) { reviewDate = it.toString(); go("morning") }
                    "reminders" -> RealityReminderSettings(data, model.lucid, onMorningSettings)
                    "night" -> NightPracticeSettings(data, model.lucid) { nightDate = it.toString(); go("cue-training") }
                    "cue-training" -> CueTrainingPractice(data, model.lucid, LocalDate.parse(nightDate))
                    "tips" -> TipsPractice()
                    else -> PracticeHome(data, ::go)
                }
            }
        }
    }
}

@Composable internal fun PracticeLabel(text: String) {
    Text(text, Modifier.padding(top = 24.dp, bottom = 10.dp), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
}
@Composable internal fun PracticeNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(vertical = 8.dp), color = Pencil, fontSize = 14.sp, lineHeight = 21.sp)
}
@Composable internal fun PracticeHeading(text: String) { PageHeading(text) }
@Composable internal fun PracticeChoices(options: List<String>, selected: String?, onSelect: (String) -> Unit, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option -> FilterChip(selected == option, { onSelect(option) }, label = { Text(option, fontSize = 13.sp) }, enabled = enabled,
            shape = RoundedCornerShape(10.dp), border = FilterChipDefaults.filterChipBorder(enabled, selected == option,
                borderColor = Hairline, selectedBorderColor = Accent.copy(alpha = .2f)),
            colors = FilterChipDefaults.filterChipColors(containerColor = ControlPaper, selectedContainerColor = AccentWash, selectedLabelColor = Accent)) }
    }
}
@Composable internal fun PracticeToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 16.dp), color = Ink, fontSize = 16.sp)
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = ControlPaper,
            uncheckedTrackColor = Ink.copy(alpha = .09f), uncheckedThumbColor = ControlPaper, uncheckedBorderColor = Color.Transparent))
    }
}
@Composable internal fun PracticeSource(url: String, label: String = "Research & evidence") {
    val uri = LocalUriHandler.current
    TextButton(onClick = { runCatching { uri.openUri(url) } }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("$label ↗", fontSize = 12.sp, color = Accent) }
}

@Composable private fun ColumnScope.PracticeHome(data: LucidPracticeData, go: (String) -> Unit) {
    val tonight = data.night(upcomingPracticeNight())
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("lucid-home")) {
        PageHeading("Practice", "Small habits for more memorable nights.")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            @Composable fun DailyPractice(modifier: Modifier) {
                Column(modifier) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AccentWash).padding(22.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("REALITY CHECK", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                                Text("Am I dreaming?", Modifier.padding(top = 10.dp), fontFamily = DiarySerif, fontSize = 28.sp, lineHeight = 34.sp, color = Ink)
                            }
                            AwarenessDial(Modifier.padding(start = 10.dp).size(68.dp))
                        }
                        Text("Take a moment to notice where you are and how you got here.", Modifier.padding(top = 12.dp, bottom = 18.dp),
                            color = Pencil, fontSize = 14.sp, lineHeight = 21.sp)
                        Button(onClick = { go("check") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("practice-open-check"),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                            Text("Begin check", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("20 sec", fontSize = 12.sp)
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp))
                        }
                    }
                    SectionCaption("Your routine")
                    GroupedSection {
                        ToolRow("This morning", "Recall, lucidity & sleep notes", DreamIcons.Sun, Modifier.testTag("practice-open-morning"), onClick = { go("morning") })
                        ToolRow("Reminders", if (data.settings.realityEnabled) "${data.settings.checksPerDay} checks a day" else "Find a rhythm that works for you", DreamIcons.Bell,
                            Modifier.testTag("practice-open-reminders"), divider = false, onClick = { go("reminders") })
                    }
                }
            }
            @Composable fun NightPractice(modifier: Modifier) {
                Column(modifier) {
                    SectionCaption("Tonight")
                    GroupedSection {
                        ToolRow("Dream rehearsal", "MILD · Revisit a recent dream", DreamIcons.Moon, Modifier.testTag("practice-open-mild"), onClick = { go("mild") })
                        ToolRow("Senses exercise", "SSILD · Sight, sound & sensation", DreamIcons.Eye, Modifier.testTag("practice-open-ssild"), onClick = { go("ssild") })
                        ToolRow("Set an intention", tonight.intention.takeIf { it.isNotBlank() } ?: "What will you recognize?", DreamIcons.Pen,
                            Modifier.testTag("practice-open-intention"), divider = false, onClick = { go("intention") })
                    }
                    if (tonight.intention.isNotBlank()) {
                        Text(tonight.intention, Modifier.padding(horizontal = 16.dp, vertical = 16.dp), fontFamily = DiarySerif, fontStyle = FontStyle.Italic,
                            fontSize = 19.sp, lineHeight = 27.sp, color = Ink)
                    }
                    SectionCaption("Nighttime tools", "Optional")
                    GroupedSection {
                        ToolRow("Wake & sound cues", if (data.session != null) "Tonight is scheduled" else "Choose one night at a time", DreamIcons.Sound,
                            Modifier.testTag("practice-open-night"), divider = false, onClick = { go("night") })
                    }
                    if (data.settings.pausedUntil > System.currentTimeMillis()) PracticeNote("Daytime reminders are paused until tomorrow.")
                    PracticeNote("No streaks or targets. Make this practice your own.", Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DailyPractice(Modifier.weight(1f)); NightPractice(Modifier.weight(1f))
            } else Column {
                DailyPractice(Modifier.fillMaxWidth())
                NightPractice(Modifier.fillMaxWidth())
            }

        }
            Column(Modifier.fillMaxWidth()) {
                    SectionCaption("Explore")
                    GroupedSection {
                        ToolRow("Dream signs", "Details that keep coming back", DreamIcons.Signs, Modifier.testTag("practice-open-signs"), onClick = { go("signs") })
                        ToolRow("Practice history", "Your nights, over time", DreamIcons.History, Modifier.testTag("practice-open-history"), onClick = { go("history") })
                        ToolRow("Tips & tricks", "A practical guide to lucid dreaming", DreamIcons.Book, Modifier.testTag("practice-open-tips"), divider = false, onClick = { go("tips") })
                    }
            }
    }
}

@Composable private fun ColumnScope.DreamSignsPractice(entries: List<DreamEntry>, data: LucidPracticeData, controller: LucidPracticeController) {
    var custom by rememberSaveable { mutableStateOf("") }
    val suggested = entries.flatMap { it.symbols + it.people + it.places }.groupBy { it.lowercase() }
        .values.sortedByDescending { it.size }.map { it.first() }.take(30)
    val choices = (data.settings.signs + suggested).distinctBy { it.lowercase() }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        PageHeading("Dream signs", "Notice the details that return.")
        PracticeNote("Choose symbols, people or places from your own diary. Selected signs can appear in your daytime reminders and MILD rehearsal.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { sign ->
                val selected = data.settings.signs.any { it.equals(sign, true) }
                FilterChip(selected, {
                    val signs = if (selected) data.settings.signs.filterNot { it.equals(sign, true) } else (data.settings.signs + sign).take(30)
                    controller.settings(data.settings.copy(signs = signs))
                }, label = { Text(sign) }, modifier = Modifier.testTag("dream-sign-$sign"))
            }
        }
        OutlinedTextField(custom, { custom = it.take(80) }, Modifier.fillMaxWidth().padding(top = 16.dp).testTag("lucid-custom-sign"),
            label = { Text("Add your own dream sign") }, placeholder = { Text("Flying, the old house, a train…") }, singleLine = true)
        TextButton(onClick = {
            val value = custom.trim()
            if (value.isNotEmpty() && controller.settings(data.settings.copy(signs = (data.settings.signs + value).distinctBy { it.lowercase() }.take(30)))) custom = ""
        }, enabled = custom.isNotBlank() && data.settings.signs.size < 30) { Text("Keep this sign") }
        PracticeLabel("Make it personal")
        PracticeNote("You might pair an everyday encounter with a short reflection: ‘When I see a train today, I'll pause and notice how I arrived here.’ The app uses the signs you choose; their meaning belongs to you.")
        PracticeSource(REALITY_SOURCE)
    }
}

@Composable private fun ColumnScope.IntentionPractice(data: LucidPracticeData, controller: LucidPracticeController, date: LocalDate, onMild: () -> Unit) {
    val night = data.night(date)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        PageHeading("Tonight’s intention")
        PracticeNote("For the night ending ${date.format(DateTimeFormatter.ofPattern("d MMMM"))}. These notes save as you write.")
        OutlinedTextField(night.intention, { value -> controller.editNight(date) { it.copy(intention = value) } }, Modifier.fillMaxWidth().testTag("lucid-intention"),
            label = { Text("What do you want to remember?") }, placeholder = { Text("When I see the old house, I'll notice I'm dreaming.") }, minLines = 3)
        OutlinedTextField(night.goal, { value -> controller.editNight(date) { it.copy(goal = value) } }, Modifier.fillMaxWidth().padding(top = 16.dp).testTag("lucid-goal"),
            label = { Text("A simple dream goal · optional") }, placeholder = { Text("Look at the sky. Explore a doorway.") }, minLines = 2)
        PracticeNote("A lucid dream means recognizing that you're dreaming. Controlling the dream is a separate possibility.")
        Button(onClick = onMild, modifier = Modifier.padding(top = 12.dp)) { Text("Rehearse with MILD") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ColumnScope.MorningPractice(entries: List<DreamEntry>, data: LucidPracticeData, controller: LucidPracticeController,
    date: LocalDate, onDate: (LocalDate) -> Unit, onWrite: (Boolean, LocalDate) -> Unit, onMorningSettings: () -> Unit) {
    var pickerVisible by remember { mutableStateOf(false) }
    val night = data.night(date)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("practice-morning")) {
        PageHeading("This morning")
        TextButton(onClick = { pickerVisible = true }, contentPadding = PaddingValues(0.dp)) {
            Text("Night ending ${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}  ·  change")
        }
        PracticeNote("A few words are enough. Review notes save as you make them.")
        PracticeLabel("Dream recall")
        PracticeChoices(DreamRecall.entries.map { it.label }, night.recall.label, { choice ->
            val recall = DreamRecall.entries.first { it.label == choice }
            controller.editNight(date) { it.copy(recall = recall, lucid = if (recall == DreamRecall.NoRecall) null else it.lucid) }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onWrite(false, date) }) { Text("Write a dream") }
            TextButton(onClick = { onWrite(true, date) }) { Text("Record a dream") }
        }
        PracticeLabel("Did you know you were dreaming?")
        val diaryLucid = entries.any { it.date == date && it.lucid == true }
        PracticeChoices(listOf("Not sure", "Yes", "No"), when (night.lucid) { true -> "Yes"; false -> "No"; null -> "Not sure" }, { choice ->
            controller.editNight(date) { it.copy(lucid = when (choice) { "Yes" -> true; "No" -> false; else -> null },
                recall = if (choice == "Yes") DreamRecall.Remembered else it.recall) }
        }, enabled = night.recall != DreamRecall.NoRecall)
        if (night.recall == DreamRecall.NoRecall) PracticeNote("Lucidity stays unknown when no dream is recalled.")
        if (diaryLucid) PracticeNote("Your diary already includes a lucid dream for this waking date. History includes that report.")
        OutlinedTextField(night.realization, { value -> controller.editNight(date) { it.copy(realization = value) } }, Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("What helped you realize? · optional") }, placeholder = { Text("A dream sign, a sound, an impossible detail…") }, minLines = 2)
        PracticeLabel("Practices for this night")
        PracticeNote("Guides log completed practice automatically. You can also note a method you practised away from the app.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PracticeMethod.entries.forEach { method -> FilterChip(method in night.methods,
                { controller.editNight(date) { it.copy(methods = if (method in it.methods) it.methods - method else it.methods + method) } },
                label = { Text(method.label) }) }
        }
        PracticeLabel("Sleep quality · optional")
        PracticeChoices(listOf("Not recorded", "1 · poor", "2", "3", "4", "5 · restful"),
            listOf("Not recorded", "1 · poor", "2", "3", "4", "5 · restful")[night.sleepQuality], { value ->
                val score = listOf("Not recorded", "1 · poor", "2", "3", "4", "5 · restful").indexOf(value)
                controller.editNight(date) { it.copy(sleepQuality = score) }
            })
        OutlinedTextField(night.sleepNote, { value -> controller.editNight(date) { it.copy(sleepNote = value) } }, Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("A sleep note · optional") }, placeholder = { Text("Did practice disturb your sleep? Was returning to sleep easy?") }, minLines = 2)
        TextButton(onClick = onMorningSettings) { Text("Morning reminder settings") }
    }
    if (pickerVisible) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        val selected = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        DatePickerDialog(onDismissRequest = { pickerVisible = false }, confirmButton = {
            TextButton(onClick = { selected?.let(onDate); pickerVisible = false }, enabled = selected != null && !selected.isAfter(LocalDate.now())) { Text("Review this night") }
        }, dismissButton = { TextButton(onClick = { pickerVisible = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@Composable private fun ColumnScope.HistoryPractice(entries: List<DreamEntry>, data: LucidPracticeData, onReview: (LocalDate) -> Unit) {
    var limit by rememberSaveable { mutableIntStateOf(30) }
    val completedNights = data.nights.filter { !it.date.isAfter(LocalDate.now()) }
    val answers = completedNights.map { nightLucidity(it, entries) }
    val known = answers.count { it != null }
    val datedReports = entries.filter { entry -> completedNights.any { it.date == entry.date } &&
        (entry.title.isNotBlank() || entry.body.isNotBlank() || entry.audio.isNotEmpty() || entry.strokes.isNotEmpty()) }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("practice-history")) {
        PageHeading("Practice history")
        Text("${answers.count { it == true }} lucid nights / $known answered nights", color = Ink, fontFamily = DiarySerif, fontSize = 22.sp)
        PracticeNote("${completedNights.size} nights with practice notes · ${answers.count { it == null }} unknown. Unrecalled nights stay unknown. Upcoming nights are not scored.")
        PracticeNote("${datedReports.size} diary dream reports linked to these nights · ${completedNights.count { it.recall == DreamRecall.NoRecall }} nights explicitly marked no recall.")
        PracticeLabel("By practice")
        val comparisons = practiceComparisons(data, entries, LocalDate.now())
        if (comparisons.isEmpty()) PracticeNote("Your completed guides and morning notes will appear here.")
        comparisons.forEach { c ->
            Text(c.method.label, Modifier.padding(top = 14.dp), color = Ink, fontFamily = DiarySerif, fontSize = 20.sp)
            PracticeNote("${c.lucid} lucid / ${c.answered} answered · ${c.practiced} nights practised")
            val quality = completedNights.filter { c.method in it.methods && it.sleepQuality > 0 }.map { it.sleepQuality }
            if (quality.isNotEmpty()) PracticeNote("Sleep quality ${"%.1f".format(java.util.Locale.getDefault(), quality.average())}/5 across ${quality.size} optional ratings")
            HorizontalDivider(color = Pencil.copy(alpha = .16f))
        }
        PracticeNote("Several methods can belong to one night. These are personal associations, not proof of what caused a dream. Nighttime playback records a cue or reminder sounding, not confirmation that you were asleep or woke up.")
        PracticeLabel("Night notes")
        data.nights.sortedByDescending { it.date }.take(limit).forEach { night ->
            Column(Modifier.fillMaxWidth().clickable { onReview(night.date) }.padding(vertical = 14.dp)) {
                Text(night.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontFamily = DiarySerif, fontSize = 21.sp, color = Ink)
                PracticeNote(listOfNotNull(if (night.date.isAfter(LocalDate.now())) "Upcoming" else when (nightLucidity(night, entries)) {
                    true -> "Lucid reported"; false -> "Non-lucid reported"; null -> "Lucidity unknown" },
                    night.methods.takeIf { it.isNotEmpty() }?.joinToString { it.label },
                    if (night.recall != DreamRecall.Unknown) night.recall.label else null,
                    if (night.checks > 0) "${night.checks} guided checks" else null,
                    if (night.sleepQuality > 0) "Sleep ${night.sleepQuality}/5" else null).joinToString(" · "))
                if (night.realization.isNotBlank()) Text(night.realization, color = Ink, fontSize = 14.sp)
                if (night.sleepNote.isNotBlank()) PracticeNote(night.sleepNote)
            }
            HorizontalDivider(color = Pencil.copy(alpha = .16f))
        }
        if (data.nights.size > limit) TextButton(onClick = { limit += 30 }) { Text("Earlier notes") }
        PracticeLabel("Recent practice")
        data.events.takeLast(12).reversed().forEach { event ->
            Text("${PracticeAlarms.time(event.at)} · ${event.method.label}", color = Ink, modifier = Modifier.padding(top = 10.dp))
            PracticeNote("${event.night.format(DateTimeFormatter.ofPattern("d MMM"))} waking date${if (event.detail.isBlank()) "" else " · ${event.detail}"}")
        }
    }
}

@Composable private fun ColumnScope.TipsPractice() {
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("lucid-tips")) {
        PageHeading("Tips & tricks", "Practical advice. Research where it matters.")
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("lucid-tips-search"),
            placeholder = { Text("Search recall, MILD, sound…", fontSize = 14.sp) }, singleLine = true,
            shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Pencil, modifier = Modifier.size(20.dp)) })
        val matches = LucidTips.filter { search.isBlank() || "${it.title} ${it.text} ${it.evidence}".contains(search, true) }
        if (matches.isEmpty()) PracticeNote("No matching tips. Try ‘recall’, ‘MILD’, ‘senses’ or ‘sound’.")
        matches.forEachIndexed { index, tip ->
            val open = expanded == tip.title || search.isNotBlank()
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(ControlPaper)) {
                Column(Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Expanded" else "Collapsed" }.clickable { expanded = if (expanded == tip.title) null else tip.title }.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tip.evidence.uppercase(), Modifier.weight(1f), color = Accent, fontSize = 10.sp, letterSpacing = .7.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (open) "−" else "+", color = Pencil, fontSize = 22.sp)
                    }
                    Text(tip.title, Modifier.padding(top = 9.dp), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = Ink)
                    if (!open) Text(tip.text, Modifier.padding(top = 8.dp), color = Pencil, fontSize = 14.sp, lineHeight = 21.sp,
                        maxLines = if (index == 0) 3 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                androidx.compose.animation.AnimatedVisibility(open) {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) {
                        Text(tip.text, color = Ink, fontSize = 15.sp, lineHeight = 23.sp)
                        tip.source?.let { PracticeSource(it) }
                    }
                }
            }
        }
        PracticeNote("Written guides work offline. Sound cues are experimental; study results vary.")
        PracticeSource("https://doi.org/10.11588/ijodr.2026.1.115408", "Further sound-cue research")
    }
}
