package com.afterglow.diary

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.roundToInt

internal fun practiceTimeText(minute: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
internal fun parsePracticeTime(text: String, allowMidnightEnd: Boolean = false): Int? {
    if (allowMidnightEnd && text == "24:00") return 1440
    if (!text.matches(Regex("\\d{2}:\\d{2}"))) return null
    return runCatching { java.time.LocalTime.parse(text) }.getOrNull()?.let { it.hour * 60 + it.minute }
}
private fun notificationSettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
}

@Composable internal fun ColumnScope.RealityReminderSettings(data: LucidPracticeData, controller: LucidPracticeController, onMorningSettings: () -> Unit) {
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf(data.settings) }
    var start by rememberSaveable { mutableStateOf(practiceTimeText(draft.startMinute)) }
    var end by rememberSaveable { mutableStateOf(practiceTimeText(draft.endMinute)) }
    var bedtime by rememberSaveable { mutableStateOf(practiceTimeText(draft.bedtimeMinute)) }
    var message by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf<LucidSettings?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { value ->
            controller.settings(if (granted) value else value.copy(realityEnabled = false, bedtimeEnabled = false))
            draft = draft.copy(realityEnabled = granted && value.realityEnabled, bedtimeEnabled = granted && value.bedtimeEnabled)
            message = if (granted) "Your reminder schedule is saved." else "Notification permission wasn't granted. Your other choices were saved; reminders stay off."
        }
        pending = null
    }
    val first = parsePracticeTime(start)
    val last = parsePracticeTime(end, true)
    val evening = parsePracticeTime(bedtime)
    val candidate = data.settings.copy(realityEnabled = draft.realityEnabled, checksPerDay = draft.checksPerDay,
        startMinute = first ?: data.settings.startMinute, endMinute = last ?: data.settings.endMinute,
        variedTimes = draft.variedTimes, days = draft.days, notificationSound = draft.notificationSound,
        vibration = draft.vibration, bedtimeEnabled = draft.bedtimeEnabled, bedtimeMinute = evening ?: data.settings.bedtimeMinute)
    val valid = first != null && last != null && (!draft.bedtimeEnabled || evening != null) && validLucidSettings(candidate)
    val lifecycle = LocalLifecycleOwner.current
    var notifications by remember { mutableStateOf(PracticeAlarms.canNotify(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) notifications = PracticeAlarms.canNotify(context) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("practice-reminders")) {
        PageHeading("Reminders", "A little awareness, at the right time.")
        GroupedSection {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                PracticeToggle("Reality checks", draft.realityEnabled) { draft = draft.copy(realityEnabled = it) }
            }
        }
        SectionCaption("Daily schedule")
        GroupedSection {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Checks per day", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Within your active hours", color = Pencil, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(PanelPaper), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { draft = draft.copy(checksPerDay = draft.checksPerDay - 1) }, enabled = draft.checksPerDay > 1,
                            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−", fontSize = 22.sp) }
                        Text(draft.checksPerDay.toString(), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                            modifier = Modifier.widthIn(min = 22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton(onClick = { draft = draft.copy(checksPerDay = draft.checksPerDay + 1) }, enabled = draft.checksPerDay < 12,
                            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+", fontSize = 22.sp) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Hairline)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(start, { start = it.take(5) }, Modifier.weight(1f), label = { Text("From") }, singleLine = true, isError = first == null)
                    OutlinedTextField(end, { end = it.take(5) }, Modifier.weight(1f), label = { Text("Until") }, singleLine = true, isError = last == null)
                }
                Spacer(Modifier.height(8.dp))
                PracticeChoices(listOf("Varied times", "Evenly spaced"), if (draft.variedTimes) "Varied times" else "Evenly spaced", { draft = draft.copy(variedTimes = it == "Varied times") })
                Text(if (draft.variedTimes) "A different time in each part of your day." else "The same times each day.", color = Pencil, fontSize = 12.sp, lineHeight = 18.sp)
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Hairline)
                Text("Repeat", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    days.forEachIndexed { i, day ->
                        val chosen = i + 1 in draft.days
                        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) Accent else PanelPaper).semantics { contentDescription = names[i] }
                            .toggleable(value = chosen, role = Role.Checkbox, onValueChange = {
                                val selected = if (chosen) draft.days - (i + 1) else draft.days + (i + 1)
                                if (selected.isNotEmpty()) draft = draft.copy(days = selected)
                            }), contentAlignment = Alignment.Center) {
                            Text(day, color = if (chosen) if (LocalNotebookPreferences.current.night) PanelPaper else Color.White else Pencil,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        SectionCaption("Evening rehearsal")
        GroupedSection {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                PracticeToggle("A reminder for MILD", draft.bedtimeEnabled) { draft = draft.copy(bedtimeEnabled = it) }
                androidx.compose.animation.AnimatedVisibility(draft.bedtimeEnabled) {
                    Column(Modifier.padding(bottom = 14.dp)) {
                        OutlinedTextField(bedtime, { bedtime = it.take(5) }, Modifier.fillMaxWidth(), label = { Text("Evening time · HH:mm") },
                            isError = evening == null, singleLine = true)
                        Text("Uses the repeat days above.", color = Pencil, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        SectionCaption("Sound & touch")
        GroupedSection {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                PracticeToggle("Notification sound", draft.notificationSound) { draft = draft.copy(notificationSound = it) }
                HorizontalDivider(color = Hairline)
                PracticeToggle("Vibration", draft.vibration) { draft = draft.copy(vibration = it) }
            }
        }
        PracticeNote("Android may deliver daytime prompts a little later to conserve battery.")
        if (!valid) Text("Use a daytime window with enough space for the reminders, a valid time, and at least one practice day.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Button(onClick = {
            if ((candidate.realityEnabled || candidate.bedtimeEnabled) && Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pending = candidate; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (controller.settings(candidate)) {
                message = if (PracticeAlarms.canNotify(context)) "Your reminder schedule is saved." else "Schedule saved. Enable Dream Diary notifications in Android settings to receive it."
            }
        }, enabled = valid, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(top = 6.dp).testTag("save-practice-reminders")) { Text("Save schedule", fontWeight = FontWeight.SemiBold) }
        if (message.isNotBlank()) PracticeNote(message)
        if (!notifications) PracticeNote("Dream Diary notifications are currently disabled in Android.")
        TextButton(onClick = { notificationSettings(context) }) { Text("Android notification settings") }
        if (data.settings.realityEnabled) {
            if (data.settings.pausedUntil > System.currentTimeMillis()) TextButton(onClick = { controller.settings(data.settings.copy(pausedUntil = 0)) }) { Text("Resume daytime reminders") }
            else TextButton(onClick = controller::pauseToday) { Text("Pause daytime reminders until tomorrow") }
        }
        if (data.pendingRealityAt > System.currentTimeMillis()) PracticeNote("Next daytime prompt: around ${PracticeAlarms.time(data.pendingRealityAt)}.")
        TextButton(onClick = onMorningSettings) { Text("Morning recall reminder settings") }
        PracticeNote("Three prompts is a starting suggestion, not a scientifically established dose. More prompts do not guarantee more lucid dreams.")
        PracticeSource(REALITY_SOURCE)
    }
}

@Composable internal fun ColumnScope.NightPracticeSettings(data: LucidPracticeData, controller: LucidPracticeController, onTrain: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var draft by rememberSaveable { mutableStateOf(data.settings) }
    var exact by remember { mutableStateOf(PracticeAlarms.canUseExactAlarms(context)) }
    var notifications by remember { mutableStateOf(PracticeAlarms.canNotify(context)) }
    var message by rememberSaveable { mutableStateOf("") }
    val player = remember { PracticeCuePlayer(context) }
    DisposableEffect(player) { onDispose { player.close() } }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exact = PracticeAlarms.canUseExactAlarms(context); notifications = PracticeAlarms.canNotify(context)
            }
            if (event == Lifecycle.Event.ON_STOP) player.close()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = it && PracticeAlarms.canNotify(context) }
    fun plan() = data.settings.copy(wakeEnabled = draft.wakeEnabled, wakeAfterHours = draft.wakeAfterHours,
        cuesEnabled = draft.cuesEnabled, cuesAfterHours = draft.cuesAfterHours, cueCount = draft.cueCount,
        cueGapMinutes = draft.cueGapMinutes, cue = draft.cue, cueVolume = draft.cueVolume)
    val valid = (!draft.wakeEnabled || !draft.cuesEnabled || draft.cuesAfterHours > draft.wakeAfterHours)
    val trained = trainedForCue(data, draft.cue)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("night-practice")) {
        PageHeading("Nighttime tools", "Choose one night at a time.")
        PracticeNote("Times count from when you start a session. These tools stay off until you choose to use them.")
        if (data.notice.isNotBlank()) PracticeNote(data.notice)
        data.session?.let { session ->
            PracticeLabel("Tonight is armed")
            if (session.wakeAt > 0) Text("Wake reminder · ${PracticeAlarms.time(session.wakeAt)}", color = Ink)
            if (session.cueAt > 0) Text("${session.remainingCues} ${session.cue.label.lowercase()} cues · from ${PracticeAlarms.time(session.cueAt)}", color = Ink)
            PracticeNote("Waking date: ${session.night}. The notification includes a stop control.")
            Button(onClick = controller::stopNight, modifier = Modifier.testTag("stop-lucid-night")) { Text("Stop tonight") }
            PracticeNote("Use Stop tonight to change the active schedule. The choices below are saved for a new session.")
        }
        PracticeLabel("Wake back to bed · optional")
        PracticeToggle("One gentle wake-up reminder", draft.wakeEnabled) { draft = draft.copy(wakeEnabled = it) }
        PracticeChoices((5..7).map { "$it hours after start" }, "${draft.wakeAfterHours} hours after start", { draft = draft.copy(wakeAfterHours = it.substringBefore(' ').toInt()) })
        PracticeNote("The reminder sounds for up to one minute using alarm volume. Open it for MILD, then settle back to sleep. Natural awakenings are also a chance to rehearse. Android's sound and Do Not Disturb settings apply.")
        PracticeSource(INDUCTION_SOURCE)
        PracticeLabel("Trained sound cues · experimental")
        PracticeToggle("A short set of scheduled sound cues", draft.cuesEnabled) { draft = draft.copy(cuesEnabled = it) }
        PracticeNote("Pair a sound with attentive reflection first. The same sound can play later at chosen times. This tool does not detect REM or verify that you're asleep; cues can wake you.")
        PracticeChoices(LucidCue.entries.map { it.label }, draft.cue.label, { label -> draft = draft.copy(cue = LucidCue.entries.first { it.label == label }) })
        PracticeLabel("Cue volume · ${(draft.cueVolume * 100).roundToInt()}% of media volume")
        Slider(draft.cueVolume, { draft = draft.copy(cueVolume = it) }, valueRange = .05f.. .5f)
        TextButton(onClick = {
            val volume = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)
            message = if (volume == 0) "Your media volume is muted. Adjust it before testing." else if (player.play(draft.cue, draft.cueVolume)) "Listen with the phone set where you plan to leave it." else "Audio couldn't play. Check your device sound settings."
        }) { Text("Test this sound") }
        PracticeChoices((5..8).map { "$it hours after start" }, "${draft.cuesAfterHours} hours after start", { draft = draft.copy(cuesAfterHours = it.substringBefore(' ').toInt()) })
        PracticeLabel("How many cues?")
        PracticeChoices((1..5).map { it.toString() }, draft.cueCount.toString(), { draft = draft.copy(cueCount = it.toInt()) })
        PracticeChoices(listOf("15 minutes apart", "30 minutes apart", "60 minutes apart"), "${draft.cueGapMinutes} minutes apart", { draft = draft.copy(cueGapMinutes = it.substringBefore(' ').toInt()) })
        TextButton(onClick = {
            if (controller.settings(plan())) { player.close(); onTrain(makeNightSession(ZonedDateTime.now(), plan().copy(wakeEnabled = false, cuesEnabled = true)).night) }
        }) { Text(if (trained) "Repeat today's cue rehearsal" else "Learn this cue before tonight") }
        PracticeNote(if (trained) "You rehearsed this sound in the last 24 hours." else "Complete the short rehearsal with this sound before arming cues. Repeat it before each practice night.")
        PracticeSource(CUE_SOURCE)
        if (!valid) Text("Choose sound cues later than the wake reminder so they do not overlap.", color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) PracticeNote(message)
        TextButton(onClick = { if (controller.settings(plan())) message = "Your choices are saved. Start a session when you're going to sleep." }, enabled = valid) { Text("Keep nighttime choices") }
        if ((draft.wakeEnabled || draft.cuesEnabled) && !notifications) TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else notificationSettings(context)
        }) { Text("Enable night-tool notifications") }
        if ((draft.wakeEnabled || draft.cuesEnabled) && !exact && Build.VERSION.SDK_INT >= 31) {
            PracticeNote("Allow Android's Alarms & reminders access for the nighttime schedule. Daytime prompts do not need it.")
            TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                .onFailure { message = "Open Android settings → Apps → Special app access → Alarms & reminders." } }) { Text("Allow timed night reminders") }
        }
        Button(onClick = {
            player.close()
            if (controller.settings(plan()) && controller.armNight()) message = "Tonight is armed. You can stop it from the notification or this page."
        }, enabled = data.session == null && valid && (draft.wakeEnabled || draft.cuesEnabled) && exact && notifications && (!draft.cuesEnabled || trained),
            modifier = Modifier.padding(top = 12.dp).testTag("start-lucid-night")) { Text("I'm going to sleep · Start tonight") }
        PracticeNote("Each session is one night only. Restarting the phone, changing its clock or stopping the session ends the nighttime tools. There is no automatic next-night repeat.")
        TextButton(onClick = { notificationSettings(context) }) { Text("Android notification settings") }
        TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) } }) { Text("Device sound settings") }
    }
}
