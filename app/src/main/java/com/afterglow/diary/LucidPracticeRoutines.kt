package com.afterglow.diary

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay

@Composable internal fun ColumnScope.RealityCheckPractice(data: LucidPracticeData, controller: LucidPracticeController, date: LocalDate) {
    PracticeRoutine("Reality check", RealitySteps, PracticeMethod.RealityCheck, controller, date,
        intro = "Give yourself twenty seconds of attention. A check is a practice prompt, not a conclusive test." +
            if (data.settings.signs.isEmpty()) "" else " Your chosen dream signs: ${data.settings.signs.joinToString() }.",
        detail = data.settings.signs.firstOrNull()?.let { "Dream sign: $it" } ?: "Guided daytime reflection", autoAdvance = true)
}

@Composable internal fun ColumnScope.SensoryPractice(controller: LucidPracticeController, date: LocalDate) {
    PracticeRoutine("Notice your senses", SsildSteps, PracticeMethod.SSILD, controller, date,
        intro = "Four quick cycles, then three slow cycles through vision, hearing and bodily sensations. Stay comfortable; you do not need to make anything happen.",
        detail = "Four quick and three slow sensory cycles", autoAdvance = true)
}

@Composable internal fun ColumnScope.MildPractice(entries: List<DreamEntry>, data: LucidPracticeData, controller: LucidPracticeController, date: LocalDate) {
    val dreams = entries.filter { it.body.isNotBlank() || it.title.isNotBlank() }.sortedByDescending { it.date }.take(12)
    var selected by rememberSaveable { mutableStateOf(dreams.firstOrNull()?.id ?: "") }
    var unusual by rememberSaveable { mutableStateOf(data.settings.signs.firstOrNull() ?: "") }
    var scene by rememberSaveable { mutableStateOf("") }
    var started by rememberSaveable { mutableStateOf(false) }
    var choosingDream by rememberSaveable { mutableStateOf(false) }
    val dream = dreams.firstOrNull { it.id == selected }
    val intention = data.night(date).intention.ifBlank { "When I dream again, I intend to recognize that I'm dreaming." }
    val goal = data.night(date).goal.takeIf { it.isNotBlank() }?.let { "Your dream goal: $it." } ?: ""
    if (!started) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            PageHeading("Dream rehearsal", "Remember a dream. Imagine recognizing it.")
            PracticeLabel("Choose a recent page")
            GroupedSection {
                Column(Modifier.padding(18.dp)) {
                    if (dream != null) {
                        Text(dream.date.format(DateTimeFormatter.ofPattern("d MMMM")), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(dream.title.ifBlank { "Untitled dream" }, fontFamily = DiarySerif, fontSize = 23.sp, lineHeight = 30.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
                        Text(dream.body.replace('\u000C', '\n'), color = Pencil, fontSize = 14.sp, lineHeight = 22.sp, maxLines = 4,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                    } else OutlinedTextField(scene, { scene = it.take(1500) }, Modifier.fillMaxWidth(), label = { Text("A scene you remember") }, minLines = 3)
                    TextButton(onClick = { choosingDream = !choosingDream }, contentPadding = PaddingValues(0.dp)) { Text(if (choosingDream) "Done choosing" else "Choose another dream") }
                    androidx.compose.animation.AnimatedVisibility(choosingDream) {
                        Column {
                            dreams.forEach { entry ->
                                TextButton(onClick = { selected = entry.id; choosingDream = false }) { Text(entry.title.ifBlank { "Dream from ${entry.date}" }) }
                            }
                            TextButton(onClick = { selected = ""; choosingDream = false }) { Text("Use another remembered scene") }
                        }
                    }
                }
            }
            OutlinedTextField(unusual, { unusual = it.take(200) }, Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("An unusual detail") }, placeholder = { Text("The staircase opened into the sky…") }, minLines = 2)
            if (data.settings.signs.isNotEmpty()) PracticeChoices(data.settings.signs, unusual, { unusual = it })
            PracticeLabel("Your intention")
            Text(intention, fontFamily = DiarySerif, fontSize = 21.sp, lineHeight = 30.sp, color = Ink, modifier = Modifier.padding(bottom = 12.dp))
            Button(onClick = { started = true }, enabled = dream != null || scene.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(top = 16.dp).testTag("begin-mild")) { Text("Begin rehearsal") }
            PracticeNote("MILD combines recall, visualization and intention. Research often pairs it with a brief later-night awakening.")
            PracticeSource(MILD_SOURCE)
        }
    } else {
        val memory = dream?.let { entry -> entry.body.replace('\u000C', '\n').take(500).ifBlank { entry.title } } ?: scene
        val sign = unusual.ifBlank { "an unusual detail in the scene" }
        val steps = listOf(
            PracticeStep("Remember the scene", "Recall this moment as clearly as feels comfortable: $memory", 25),
            PracticeStep("Recognize the unusual", "Imagine being there again. Notice $sign. Imagine realizing, within that scene, that you are dreaming.", 25),
            PracticeStep("Rehearse your intention", "$intention Picture recognizing the dream and remembering what you wanted to do. $goal", 25),
            PracticeStep("Let yourself settle", "When the intention feels clear, let go of effort. Put the phone down and allow yourself to sleep. No need to force another repetition.", 15),
        )
        PracticeRoutine("Rehearse a dream", steps, PracticeMethod.MILD, controller, date,
            intro = "Move through the steps at your own pace. The timer is a gentle guide, not a requirement.",
            detail = "Rehearsed ${dream?.title?.ifBlank { "a diary scene" } ?: "a remembered scene"}", autoAdvance = false)
    }
}

@Composable internal fun ColumnScope.CueTrainingPractice(data: LucidPracticeData, controller: LucidPracticeController, date: LocalDate) {
    val cue = data.settings.cue
    val steps = remember(cue) { buildList {
        repeat(3) { round ->
            add(PracticeStep("Listen · round ${round + 1}", "Listen to ${cue.label.lowercase()}, then connect it with a deliberate moment of awareness.", 4, playCue = true))
            addAll(RealitySteps)
        }
    } }
    PracticeRoutine("Learn your sound cue", steps, PracticeMethod.SoundTraining, controller, date,
        intro = "Three short rounds pair ${cue.label.lowercase()} with attentive reflection. Use audible, comfortable media volume. Nighttime cues are experimental and require this rehearsal first.",
        detail = cue.name, autoAdvance = true, cue = cue, volume = data.settings.cueVolume)
}

@Composable private fun ColumnScope.PracticeRoutine(title: String, steps: List<PracticeStep>, method: PracticeMethod,
    controller: LucidPracticeController, date: LocalDate, intro: String, detail: String, autoAdvance: Boolean,
    cue: LucidCue? = null, volume: Float = .15f) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val speech = remember { PracticeSpeech(context) }
    val player = remember { PracticeCuePlayer(context) }
    var runId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var remaining by rememberSaveable { mutableIntStateOf(steps.first().seconds) }
    var begun by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var readAloud by rememberSaveable { mutableStateOf(false) }
    var taps by rememberSaveable { mutableStateOf(false) }
    var dim by rememberSaveable { mutableStateOf(false) }
    var heardCue by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val current = steps[step.coerceIn(0, steps.lastIndex)]
    fun finish() {
        running = false; speech.stop(); player.close()
        if (cue != null && !heardCue) {
            message = "The cue couldn't play. Check media volume and use Restart to rehearse before arming night cues."
        } else if (controller.record(method, date, runId, detail)) {
            completed = true; message = "Practice kept for the night ending ${date.format(DateTimeFormatter.ofPattern("d MMMM"))}."
        }
    }
    fun next() {
        if (step == steps.lastIndex) finish()
        else { step++; remaining = steps[step].seconds }
    }
    DisposableEffect(speech, player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { running = false; speech.stop(); player.close() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); speech.close(); player.close() }
    }
    LaunchedEffect(running, step) {
        if (running) {
            while (remaining > 0) { delay(1000); remaining-- }
            if (autoAdvance) next()
        }
    }
    LaunchedEffect(running, step, readAloud, speech.ready) {
        speech.stop()
        if (running && readAloud && speech.ready && !current.playCue) {
            speech.say(if (current.seconds <= 5) current.title.substringAfter('·').trim() else current.text)
        }
    }
    LaunchedEffect(running, step) {
        if (running) {
            if (taps) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (current.playCue && cue != null) {
                val mediaVolume = context.getSystemService(android.media.AudioManager::class.java).getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                if (mediaVolume > 0 && player.play(cue, volume)) heardCue = true
                else message = "Your cue is not audible. Check media volume, pause, then restart the rehearsal."
            }
        } else player.close()
    }
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, running, dim) {
        val oldBrightness = window?.attributes?.screenBrightness
        val hadKeepAwake = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (running) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (dim) window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = .03f } }
        }
        onDispose {
            if (oldBrightness != null) window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = oldBrightness } }
            if (!hadKeepAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("practice-routine")) {
        PageHeading(title)
        Text("Night ending ${date.format(DateTimeFormatter.ofPattern("d MMMM"))}", color = Pencil, fontSize = 13.sp, modifier = Modifier.padding(bottom = 20.dp))
        if (!completed) {
            GroupedSection {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("STEP ${step + 1} OF ${steps.size}", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    AwarenessDial(Modifier.padding(vertical = 22.dp).size(126.dp),
                        progress = (step + (current.seconds - remaining).toFloat() / current.seconds.coerceAtLeast(1)) / steps.size,
                        label = "${remaining}s")
                    Text(current.title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 22.sp, lineHeight = 28.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(current.text, color = Pencil, fontSize = 17.sp, lineHeight = 26.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
                    Button(onClick = {
                        begun = true
                        running = !running
                        if (!running) speech.stop()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("start-practice-guide")) {
                        Text(if (!begun) "Start practice" else if (running) "Pause" else "Continue", fontWeight = FontWeight.SemiBold)
                    }
                    if (begun) TextButton(onClick = ::next) { Text(if (step == steps.lastIndex) "Finish practice" else "Next step") }
                }
            }
            TextButton(onClick = { options = !options }, modifier = Modifier.padding(top = 12.dp), contentPadding = PaddingValues(0.dp)) {
                Text(if (options) "Hide guide options" else "Voice, haptics & display  +")
            }
            androidx.compose.animation.AnimatedVisibility(options) {
                GroupedSection {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        PracticeToggle("Read aloud", readAloud) { readAloud = it; if (it) speech.enable() else speech.stop() }
                        if (readAloud) {
                            LaunchedEffect(Unit) { speech.enable() }
                            PracticeNote(speech.status)
                        }
                        HorizontalDivider(color = Hairline)
                        PracticeToggle("Tap between steps", taps) { taps = it }
                        HorizontalDivider(color = Hairline)
                        PracticeToggle("Dim the screen", dim) { dim = it }
                    }
                }
            }
            PracticeNote("Leaving this page or locking your phone pauses the guide.")
        } else {
            GroupedSection {
                Column(Modifier.padding(24.dp)) {
                    AwarenessDial(Modifier.size(72.dp), progress = 1f)
                    Text("Practice complete", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
                    PracticeNote("Saved to your practice history. Settle back into your day, or put the phone down for the night.")
                }
            }
        }
        TextButton(onClick = { about = !about }, contentPadding = PaddingValues(0.dp)) { Text(if (about) "Hide about this practice" else "About this practice") }
        androidx.compose.animation.AnimatedVisibility(about) { PracticeNote(intro) }
        if (message.isNotBlank()) PracticeNote(message)
        if (begun) TextButton(onClick = {
            speech.stop(); player.close(); runId = UUID.randomUUID().toString(); running = false; begun = false
            step = 0; remaining = steps.first().seconds; completed = false; heardCue = false; message = ""
        }) { Text("Restart practice") }
        if (method == PracticeMethod.MILD || method == PracticeMethod.SSILD) PracticeSource(if (method == PracticeMethod.MILD) MILD_SOURCE else INDUCTION_SOURCE)
    }
}
