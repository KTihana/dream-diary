package com.afterglow.diary

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
    onMorning: () -> Unit, onReality: () -> Unit, onClose: () -> Unit) {
    NotebookPanel("Dream Diary", onClose, background = LibraryPaper) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("notebook-settings")) {
            val wide = maxWidth >= 680.dp
            @Composable fun Introduction() {
                Text("Settings", Modifier.padding(top = 16.dp), fontFamily = DiarySerif,
                    fontSize = if (wide) 30.sp else 36.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, color = Ink)
                Text("Paper, writing & reminders.", Modifier.padding(top = 6.dp, bottom = 24.dp), fontSize = 13.sp, color = Pencil)
                SettingsPaperPreview(settings)
            }
            @Composable fun Options() {
                SettingsLabel("Your notebook")
                GroupedSection {
                    SettingChoice("Paper", settings.ruling, listOf("Lined", "Dotted", "Plain"), "settings-paper") { onChange(settings.copy(ruling = it)) }
                    SettingsDivider()
                    SettingChoice("Writing", settings.font, listOf("Book", "Handwritten", "Simple"), "settings-font") { onChange(settings.copy(font = it)) }
                    SettingsDivider()
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", Modifier.weight(1f), fontSize = 15.sp, color = Ink)
                        IconButton(onClick = { onChange(settings.copy(textSize = (settings.textSize - 2).coerceAtLeast(16))) },
                            enabled = settings.textSize > 16, modifier = Modifier.testTag("settings-size-smaller").semantics { contentDescription = "Smaller text" }) {
                            Text("−", fontSize = 24.sp, color = if (settings.textSize > 16) Accent else Pencil.copy(alpha = .35f))
                        }
                        Text(settings.textSize.toString(), Modifier.widthIn(min = 24.dp), color = Pencil, fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { onChange(settings.copy(textSize = (settings.textSize + 2).coerceAtMost(22))) },
                            enabled = settings.textSize < 22, modifier = Modifier.testTag("settings-size-larger")) {
                            Icon(Icons.Outlined.Add, "Larger text", Modifier.size(18.dp))
                        }
                    }
                    SettingsDivider()
                    SettingChoice("Page marker", settings.cover, listOf("Linen", "Rose", "Midnight"), "settings-marker", swatch = Cover) { onChange(settings.copy(cover = it)) }
                    SettingsDivider()
                    Row(Modifier.fillMaxWidth().toggleable(settings.night, role = Role.Switch, onValueChange = { onChange(settings.copy(night = it)) })
                        .testTag("night-paper").padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Night paper", color = Ink, fontSize = 15.sp)
                            Text("Softer light after dark", Modifier.padding(top = 3.dp), color = Pencil, fontSize = 12.sp)
                        }
                        Switch(settings.night, onCheckedChange = null, colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent, checkedThumbColor = ControlPaper,
                            uncheckedTrackColor = Ink.copy(alpha = .08f), uncheckedThumbColor = ControlPaper,
                            uncheckedBorderColor = Hairline))
                    }
                }
                SettingsLabel("Reminders")
                GroupedSection {
                    SettingsLink("Morning recall", if (settings.reminder) "Around %02d:%02d".format(settings.reminderHour, settings.reminderMinute) else "A moment to remember", DreamIcons.Sun, "settings-morning", onMorning)
                    SettingsDivider()
                    SettingsLink("Reality checks", "Daytime & evening reminders", DreamIcons.Bell, "settings-reality", onReality)
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
    Surface(Modifier.fillMaxWidth().testTag("settings-paper-preview"), color = notebookPaperColor(false, settings.night),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(.7.dp, Hairline)) {
        Box {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 22.dp).width(7.dp).height(26.dp).background(Cover))
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text("A page of your own", fontFamily = DiarySerif, fontSize = 20.sp, color = Ink, modifier = Modifier.padding(bottom = 10.dp))
                Text("I remember the light\nand how it felt to fly.", Modifier.fillMaxWidth().drawBehind {
                    if (settings.ruling == "Lined") textLayout?.let { layout ->
                        repeat(layout.lineCount) { line ->
                            val y = layout.getLineBaseline(line) + 5.dp.toPx()
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
                }, color = Ink, fontSize = settings.textSize.sp, lineHeight = (settings.textSize + 14).sp,
                    fontFamily = when (settings.font) { "Handwritten" -> Handwritten; "Simple" -> FontFamily.SansSerif; else -> DiarySerif },
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
