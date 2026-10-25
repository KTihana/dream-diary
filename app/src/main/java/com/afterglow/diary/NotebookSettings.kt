package com.afterglow.diary

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun NotebookSettingsPanel(settings: NotebookPreferences, onChange: (NotebookPreferences) -> Unit,
    onMorning: () -> Unit, onReality: () -> Unit, onUpdates: () -> Unit, onClose: () -> Unit) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val back: (() -> Unit)? = if (section != null) ({ section = null }) else null
    NotebookPanel("Dream Diary", onClose, onDismiss = back ?: onClose, onBack = back,
        backLabel = "Back to settings", background = LibraryPaper) {
        key(section) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag(if (section == null) "notebook-settings" else "settings-$section-panel")) {
                val wide = maxWidth >= 680.dp
                @Composable fun Introduction() {
                    Text(section ?: "Settings", Modifier.padding(top = 16.dp), fontFamily = DiarySerif,
                        fontSize = if (wide) 30.sp else 36.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, color = Ink)
                    Text(when (section) { "Writing" -> "Find your own handwriting."; "Paper" -> "A different feel for your notebook."; else -> "Make these pages yours." },
                        Modifier.padding(top = 6.dp, bottom = 22.dp), fontSize = 13.sp, color = Pencil)
                    SettingsPaperPreview(settings)
                }
                @Composable fun Options() {
                    when (section) {
                        "Writing" -> WritingOptions(settings, onChange)
                        "Paper" -> PaperOptions(settings, onChange)
                        else -> {
                            SettingsLabel("Your notebook")
                            GroupedSection {
                                SettingsLink("Writing", "${settings.font} · ${settings.textSize} · ${settings.inkColor}", DreamIcons.Pen, "settings-font") { section = "Writing" }
                                SettingsDivider()
                                SettingsLink("Paper", "${settings.paperTone} · ${settings.ruling}", DreamIcons.Book, "settings-paper") { section = "Paper" }
                                SettingsDivider()
                                SettingChoice("Page marker", settings.cover, PageMarkers, "settings-marker", swatch = Cover) { onChange(settings.copy(cover = it)) }
                                SettingsDivider()
                                SettingsToggle("Night paper", "Softer light after dark", settings.night, "night-paper") { onChange(settings.copy(night = it)) }
                            }
                            SettingsLabel("Reminders")
                            GroupedSection {
                                SettingsLink("Morning recall", if (settings.reminder) "Around %02d:%02d".format(settings.reminderHour, settings.reminderMinute) else "A moment to remember", DreamIcons.Sun, "settings-morning", onMorning)
                                SettingsDivider()
                                SettingsLink("Reality checks", "Daytime & evening reminders", DreamIcons.Bell, "settings-reality", onReality)
                            }
                            SettingsLabel("Dream Diary")
                            GroupedSection { SettingsLink("App updates", "Check for a newer version", DreamIcons.History, "settings-updates", onUpdates) }
                            var resetRequested by remember { mutableStateOf(false) }
                            TextButton(onClick = { resetRequested = true }, modifier = Modifier.padding(top = 16.dp).testTag("settings-reset")) { Text("Reset appearance", color = Pencil) }
                            if (resetRequested) AlertDialog(onDismissRequest = { resetRequested = false },
                                title = { Text("Reset the look of your notebook?") },
                                text = { Text("Restore the original paper, font and colors. Your dreams, language and reminders stay as they are.") },
                                confirmButton = { TextButton(onClick = { onChange(settings.resetAppearance()); resetRequested = false }) { Text("Reset appearance") } },
                                dismissButton = { TextButton(onClick = { resetRequested = false }) { Text("Keep my style") } })
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
                if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) { Introduction() }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { Options() }
                } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Introduction(); Options() }
            }
        }
    }
}

@Composable private fun WritingOptions(settings: NotebookPreferences, onChange: (NotebookPreferences) -> Unit) {
    SettingsLabel("Writing style")
    WritingFonts.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            pair.forEach { font ->
                val selected = settings.font == font.name
                Surface(Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = { onChange(settings.copy(font = font.name)) })
                    .testTag("settings-font-${font.name}"), shape = RoundedCornerShape(13.dp),
                    color = if (selected) AccentWash else ControlPaper,
                    border = BorderStroke(if (selected) 1.dp else .7.dp, if (selected) Accent.copy(alpha = .5f) else Hairline)) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(font.name, Modifier.weight(1f), color = if (selected) Accent else Pencil, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            if (selected) Icon(Icons.Outlined.Check, "Selected", Modifier.size(14.dp), tint = Accent)
                        }
                        Text("I remember", Modifier.padding(top = 5.dp), fontFamily = font.family, color = Ink, fontSize = 20.sp,
                            lineHeight = 28.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    Text("${writingFont(settings.font).typeface} · Available offline", Modifier.padding(start = 3.dp, top = 2.dp), fontSize = 11.sp, color = Pencil)
    SettingsLabel("Size & spacing")
    GroupedSection {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", Modifier.weight(1f), fontSize = 15.sp, color = Ink)
            IconButton(onClick = { onChange(settings.copy(textSize = (settings.textSize - 2).coerceAtLeast(16))) },
                enabled = settings.textSize > 16, modifier = Modifier.testTag("settings-size-smaller").semantics { contentDescription = "Smaller text" }) {
                Text("−", fontSize = 24.sp, color = if (settings.textSize > 16) Accent else Pencil.copy(alpha = .35f))
            }
            Text(settings.textSize.toString(), Modifier.widthIn(min = 24.dp), color = Pencil, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onChange(settings.copy(textSize = (settings.textSize + 2).coerceAtMost(26))) },
                enabled = settings.textSize < 26, modifier = Modifier.testTag("settings-size-larger")) {
                Icon(Icons.Outlined.Add, "Larger text", Modifier.size(18.dp))
            }
        }
        SettingsDivider()
        SettingChoice("Line spacing", settings.spacing, WritingSpacing, "settings-spacing") { onChange(settings.copy(spacing = it)) }
        SettingsDivider()
        SettingsToggle("Use for titles too", "Match your chosen writing style", settings.matchTitle, "settings-match-title") { onChange(settings.copy(matchTitle = it)) }
    }
    SettingsLabel("Ink color")
    ColorChoices(InkColors, settings.inkColor, "settings-ink", { writingInk(settings.copy(inkColor = it)) }) { onChange(settings.copy(inkColor = it)) }
}

@Composable private fun PaperOptions(settings: NotebookPreferences, onChange: (NotebookPreferences) -> Unit) {
    SettingsLabel("Paper tone")
    ColorChoices(PaperTones, settings.paperTone, "settings-tone", ::paperToneColor) { onChange(settings.copy(paperTone = it)) }
    Text(if (settings.night) "Your chosen tone returns when night paper is off." else "Lucid dreams keep their lavender pages.",
        Modifier.padding(top = 12.dp, start = 3.dp), color = Pencil, fontSize = 12.sp, lineHeight = 18.sp)
    SettingsLabel("Page style")
    GroupedSection {
        listOf("Lined", "Dotted", "Plain").forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth().selectable(settings.ruling == name, role = Role.RadioButton,
                onClick = { onChange(settings.copy(ruling = name)) }).testTag("settings-paper-$name")
                .padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), color = Ink, fontSize = 15.sp)
                if (settings.ruling == name) Icon(Icons.Outlined.Check, "Selected", Modifier.size(19.dp), tint = Accent)
            }
            if (index < 2) SettingsDivider()
        }
    }
}

@Composable private fun ColorChoices(names: List<String>, selected: String, tag: String, color: (String) -> Color, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            val active = name == selected
            Column(Modifier.weight(1f).selectable(active, role = Role.RadioButton, onClick = { onSelect(name) }).testTag("$tag-$name")
                .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(45.dp).border(if (active) 1.5.dp else .7.dp, if (active) Accent else Hairline, CircleShape).padding(5.dp)
                    .background(color(name), CircleShape), contentAlignment = Alignment.Center) {
                    if (active) Icon(Icons.Outlined.Check, "Selected", Modifier.size(17.dp), tint = if (color(name).luminance() > .45f) Color(0xFF292D30) else Color.White)
                }
                Text(name, Modifier.padding(top = 6.dp), color = if (active) Accent else Pencil, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).testTag(tag)
        .padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp)
            Text(subtitle, Modifier.padding(top = 3.dp), color = Pencil, fontSize = 12.sp)
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = ControlPaper,
            uncheckedTrackColor = Ink.copy(alpha = .08f), uncheckedThumbColor = ControlPaper, uncheckedBorderColor = Hairline))
    }
}

@Composable private fun SettingsLabel(label: String) {
    Text(label, Modifier.padding(top = 22.dp, start = 4.dp, bottom = 10.dp), color = Pencil, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable private fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 18.dp), thickness = .7.dp, color = Hairline) }

@Composable
private fun SettingChoice(label: String, value: String, choices: List<String>, tag: String, swatch: Color? = null, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = true }.testTag(tag)
            .heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), color = Ink, fontSize = 15.sp)
            if (swatch != null) Box(Modifier.size(9.dp).background(swatch, CircleShape))
            Text(value, color = Pencil, fontSize = 14.sp)
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(17.dp), tint = Pencil)
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = ControlPaper, tonalElevation = 0.dp) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false },
                    modifier = Modifier.testTag("$tag-$option"), trailingIcon = if (option == value) ({ Icon(Icons.Outlined.Check, "Selected", Modifier.size(17.dp)) }) else null)
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).testTag(tag).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = Pencil)
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp)
            Text(subtitle, Modifier.padding(top = 4.dp), color = Pencil, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = Pencil)
    }
}

@Composable
private fun SettingsPaperPreview(settings: NotebookPreferences) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val rule = if (settings.night) Color(0xFF5A5565) else Color(0xFFD5DEE5)
    Surface(Modifier.fillMaxWidth().testTag("settings-paper-preview"), color = notebookPaperColor(false, settings.night, settings.paperTone),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(.7.dp, Hairline)) {
        Box {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 22.dp).width(7.dp).height(26.dp).background(Cover))
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text("A page of your own", fontFamily = if (settings.matchTitle) writingFont(settings.font).family else DiarySerif, fontSize = 20.sp, color = writingInk(settings), modifier = Modifier.padding(bottom = 10.dp))
                Text("I remember the light\nand how it felt to fly.", Modifier.fillMaxWidth().drawBehind {
                    if (settings.ruling == "Lined") textLayout?.let { layout ->
                        repeat(layout.lineCount) { line ->
                            val y = layout.getLineBottom(line) - 2.dp.toPx()
                            drawLine(rule, Offset(0f, y), Offset(size.width, y), .7.dp.toPx())
                        }
                    } else if (settings.ruling == "Dotted") {
                        val gap = 14.dp.toPx()
                        var y = gap
                        while (y < size.height) {
                            var x = 0f
                            while (x < size.width) { drawCircle(rule, .75.dp.toPx(), Offset(x, y)); x += gap }
                            y += gap
                        }
                    }
                }, style = notebookWritingStyle(settings),
                    onTextLayout = { textLayout = it })
            }
        }
    }
}

@Composable
internal fun NotebookReminderPanel(model: DiaryViewModel, onBack: () -> Unit, onMorning: () -> Unit, onClose: () -> Unit) {
    val practice by model.lucid.state.collectAsStateWithLifecycle()
    NotebookPanel("Reality checks", onClose, onDismiss = onBack, onBack = onBack, backLabel = "Back to settings") {
        val data = practice.data
        if (data == null) {
            Text(practice.error ?: "Opening your reminders…", color = Pencil)
            TextButton(onClick = model.lucid::refresh) { Text("Try again") }
        } else {
            practice.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            RealityReminderSettings(data, model.lucid, onMorning)
        }
    }
}
