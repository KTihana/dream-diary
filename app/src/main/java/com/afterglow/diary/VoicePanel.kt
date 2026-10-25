package com.afterglow.diary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Audio stays in private storage. No recording continues in the background. */
internal class DreamAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L
    fun start() {
        check(recorder == null)
        val directory = File(context.filesDir, "audio").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(target.absolutePath)
            r.prepare(); r.start()
            file = target; recorder = r; started = SystemClock.elapsedRealtime()
        } catch (error: Exception) { r.release(); target.delete(); throw error }
    }
    fun stop(): AudioClip? {
        val r = recorder ?: return null
        recorder = null
        val target = file ?: return null
        file = null
        return try {
            r.stop()
            AudioClip(target.name, ((SystemClock.elapsedRealtime() - started) / 1000).toInt().coerceAtLeast(1))
        } catch (_: Exception) { target.delete(); null } finally { r.release() }
    }
}

@Composable
internal fun VoicePanel(entry: DreamEntry, model: DiaryViewModel, onClose: () -> Unit, autoRecord: Boolean = false) {
    val context = LocalContext.current
    val preferences = LocalNotebookPreferences.current
    val language = preferences.dictationLanguage
    val languageName = if (language == "hr-HR") "Hrvatski" else "English"
    val lifecycle = LocalLifecycleOwner.current
    val dreamId = remember { entry.id }
    val recorder = remember { DreamAudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    val soundLevels = remember { DictationSoundLevels() }
    val speechScope = rememberCoroutineScope()
    val dictationControls = remember { BringIntoViewRequester() }
    var keepListening by remember { mutableStateOf(false) }
    var betweenPhrases by remember { mutableStateOf(false) }
    var recognizingPhrase by remember { mutableStateOf(false) }
    var phraseTimeout by remember { mutableStateOf<Job?>(null) }
    var nextPhrase by remember { mutableStateOf<Job?>(null) }
    var finishTimeout by remember { mutableStateOf<Job?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var transcript by rememberSaveable { mutableStateOf("") }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speechGeneration by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    var pendingPermission by remember { mutableStateOf("record") }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    var remove by remember { mutableStateOf<AudioClip?>(null) }
    var languageHelp by remember { mutableStateOf(false) }
    val hasOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    fun stopRecording() {
        val clip = recorder.stop()
        recording = false
        if (clip != null) { model.addAudio(dreamId, clip); message = "Recording saved with this dream." }
        else message = "That recording was too short to save. Try again."
    }
    fun stopPlayer() { player?.release(); player = null; playing = null }
    fun stopDictation() {
        speechGeneration++
        keepListening = false; betweenPhrases = false; recognizingPhrase = false
        phraseTimeout?.cancel(); phraseTimeout = null
        nextPhrase?.cancel(); nextPhrase = null
        finishTimeout?.cancel(); finishTimeout = null
        val speech = recognizer; recognizer = null
        speech?.cancel(); speech?.destroy()
        listening = false; finishing = false
        soundLevels.reset()
    }
    fun finishDictation() {
        keepListening = false
        nextPhrase?.cancel(); nextPhrase = null
        if (betweenPhrases || recognizer == null) {
            stopDictation(); partial = ""
            message = if (transcript.isBlank()) "No words were caught. Try again when you’re ready." else "Review your words, or dictate a little more."
        } else {
            finishing = true; soundLevels.reset(); message = "Finishing transcription…"
            if (!recognizingPhrase) recognizer?.stopListening()
            finishTimeout?.cancel()
            finishTimeout = speechScope.launch {
                delay(8000)
                if (finishing) {
                    val keptPreview = partial.isNotBlank()
                    if (keptPreview) {
                        transcript += (if (transcript.isBlank()) "" else " ") + partial.trim()
                        reviewing = true
                    }
                    stopDictation(); partial = ""
                    message = if (keptPreview) "Review your words. The last phrase was kept from the live preview."
                        else if (transcript.isBlank()) "No words were caught. Try again when you’re ready."
                        else "Review your words, or dictate a little more."
                }
            }
        }
    }
    fun eraseDictation() {
        // Invalidate the entire take, including scheduled restarts and late recognition results.
        stopDictation()
        transcript = ""; partial = ""; reviewing = false
        languageHelp = false
        message = "Ready to start again."
    }
    fun startRecord() {
        stopPlayer(); stopDictation()
        try { recorder.start(); elapsed = 0; recording = true; message = "" }
        catch (_: Exception) { message = "The microphone couldn’t start. Check that another app isn’t using it, then try again." }
    }
    fun dictate(preferOnDevice: Boolean = true, continuing: Boolean = false, allowSegments: Boolean = true, reusable: SpeechRecognizer? = null) {
        if (recording) stopRecording()
        stopPlayer()
        languageHelp = false
        if (!continuing) { keepListening = true; finishTimeout?.cancel() }
        betweenPhrases = false; recognizingPhrase = false
        phraseTimeout?.cancel()
        soundLevels.reset()
        val useOnDevice = preferOnDevice && hasOnDevice
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !useOnDevice) {
            stopDictation()
            message = "Speech recognition isn’t installed on this device. You can still save an audio recording or type your dream."; return
        }
        val existing = reusable?.takeIf { it === recognizer }
        val generation = ++speechGeneration
        if (existing == null) { val old = recognizer; recognizer = null; old?.destroy() }
        try {
            // Reuse the provider connection after terminal results/errors. Rebinding every phrase
            // can trigger connection limits or disconnect races in Android's speech broker.
            val speech = existing ?: if (useOnDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            fun isCurrent() = recognizer === speech && speechGeneration == generation
            var segmented = false
            fun appendWords(words: String) {
                if (words.isNotBlank()) {
                    transcript += (if (transcript.isBlank()) "" else " ") + words.trim()
                    reviewing = true
                }
                partial = ""
            }
            fun continueOrFinish(retryDelay: Long = 180, reuseRecognizer: Boolean = true) {
                phraseTimeout?.cancel(); recognizingPhrase = false
                soundLevels.reset()
                if (keepListening) {
                    betweenPhrases = true
                    nextPhrase?.cancel()
                    nextPhrase = speechScope.launch {
                        delay(retryDelay)
                        if (keepListening && isCurrent()) dictate(preferOnDevice = useOnDevice, continuing = true, allowSegments = allowSegments, reusable = if (reuseRecognizer) speech else null)
                    }
                } else {
                    stopDictation()
                    message = if (transcript.isBlank()) "No words were caught. Try again when you’re ready." else "Review your words, or dictate a little more."
                }
            }
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (isCurrent() && listening && !finishing) {
                        soundLevels.ready = true
                        message = "Listening · $languageName…"
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (isCurrent() && listening && !finishing) soundLevels.ready = true
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrent() && listening && !finishing) {
                        if (rmsdB.isFinite()) soundLevels.ready = true
                        soundLevels.receive(rmsdB)
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (!isCurrent()) return
                    // A provider ending one phrase does not end the user's take.
                    soundLevels.reset()
                    recognizingPhrase = true
                    phraseTimeout?.cancel()
                    if (keepListening) phraseTimeout = speechScope.launch {
                        delay(8000)
                        if (isCurrent() && keepListening && recognizingPhrase) {
                            appendWords(partial)
                            continueOrFinish(reuseRecognizer = false)
                        }
                    }
                    if (!keepListening) { finishing = true; message = "Finishing transcription…" }
                }
                override fun onError(error: Int) {
                    if (!isCurrent()) return
                    android.util.Log.w("DreamDictation", "Speech provider error: $error")
                    if (useOnDevice && keepListening && error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
                        dictate(preferOnDevice = false, continuing = true, allowSegments = allowSegments)
                        return
                    }
                    if (keepListening && allowSegments && Build.VERSION.SDK_INT >= 33 && error == SpeechRecognizer.ERROR_CLIENT) {
                        dictate(preferOnDevice = useOnDevice, continuing = true, allowSegments = false, reusable = speech)
                        return
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        appendWords(partial)
                        continueOrFinish(retryDelay = 650)
                        return
                    }
                    appendWords(partial)
                    stopDictation()
                    languageHelp = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                    message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No words were caught. Try again when you’re ready."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed to hear your dream."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Please try again."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "$languageName isn’t ready in your speech service. Check its languages or download the language model in speech settings. You can still keep an audio note."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Your speech service needs a connection. Try again, or keep an audio recording instead."
                        else -> "Transcription is unavailable right now. You can record audio instead."
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (!isCurrent()) return
                    if (!segmented) appendWords(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { partial })
                    else appendWords(partial)
                    continueOrFinish()
                }
                override fun onSegmentResults(segmentResults: Bundle) {
                    if (!isCurrent()) return
                    segmented = true
                    phraseTimeout?.cancel(); recognizingPhrase = false
                    appendWords(segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                    soundLevels.ready = true
                }
                override fun onEndOfSegmentedSession() {
                    if (isCurrent()) { appendWords(partial); continueOrFinish() }
                }
                override fun onPartialResults(results: Bundle?) {
                    if (isCurrent()) partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            listening = true; finishing = false; partial = ""; message = "Listening · $languageName…"
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            if (Build.VERSION.SDK_INT >= 33 && allowSegments) request.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            speech.startListening(request)
        } catch (_: Exception) {
            if (useOnDevice && keepListening) { dictate(preferOnDevice = false, continuing = true, allowSegments = allowSegments); return }
            stopDictation()
            message = "The speech service couldn’t start. Audio recording is still available."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (pendingPermission == "record") startRecord() else dictate() }
        else message = "Microphone access is off. Allow it in Android app settings to record or dictate."
    }
    fun request(action: String) {
        pendingPermission = action
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (action == "record") startRecord() else dictate()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(autoRecord) { if (autoRecord && !autoStarted) { autoStarted = true; request("record") } }
    LaunchedEffect(listening) {
        if (listening) {
            // Keep Finish and the sound meter together in view on short landscape screens.
            withFrameNanos { }
            dictationControls.bringIntoView()
        }
    }
    LaunchedEffect(recording) {
        if (recording) {
            val start = SystemClock.elapsedRealtime()
            while (recording) {
                elapsed = ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
                if (elapsed >= 1800) { stopRecording(); break }
                delay(500)
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (recording) stopRecording()
                stopDictation(); partial = ""
                stopPlayer()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            if (recording) stopRecording()
            stopDictation()
            stopPlayer()
        }
    }
    NotebookPanel("Voice notes", onClose) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            PageHeading("Speak your dream", "Say what you remember. We’ll turn it into words for your page.")
            Text("Dictation language", color = Pencil, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("hr-HR" to "Hrvatski", "en-US" to "English").forEach { (tag, name) ->
                    FilterChip(selected = language == tag,
                        onClick = { model.preferences(preferences.copy(dictationLanguage = tag)); message = ""; languageHelp = false },
                        label = { Text(name) }, enabled = !listening && !recording,
                        modifier = Modifier.heightIn(min = 44.dp).testTag("dictation-language-$tag"))
                }
            }
            Column(Modifier.bringIntoViewRequester(dictationControls)) {
            Button(onClick = {
                if (listening) finishDictation()
                else request("dictate")
            }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("dictate-dream"), enabled = !recording && !finishing) {
                Icon(DreamIcons.Microphone, null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (finishing) "Finishing…" else if (listening) "Finish dictation" else if (transcript.isNotBlank()) "Dictate more" else "Start dictation")
            }
            if (listening && !finishing) DictationWaveform(soundLevels, languageName, recognizingPhrase || betweenPhrases)
            }
            if (listening && !finishing) Text("Take your time. Tap Finish dictation when you’re done.",
                Modifier.padding(top = 10.dp).testTag("dictation-manual-finish"), color = Pencil, fontSize = 13.sp)
            Text("Uses on-device speech when the language is available. Otherwise your Android speech service may need internet access.",
                Modifier.padding(top = 12.dp), color = Pencil, fontSize = 12.sp, lineHeight = 18.sp)
            val canErase = transcript.isNotEmpty() || partial.isNotEmpty() || listening || finishing
            val showMessage = message.isNotEmpty() && (!listening || finishing)
            if (showMessage || canErase || reviewing) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (showMessage) Text(message, Modifier.weight(1f).testTag("voice-status"), color = Pencil)
                    else Spacer(Modifier.weight(1f))
                    if (canErase || reviewing) TextButton(onClick = ::eraseDictation,
                        enabled = canErase, modifier = Modifier.heightIn(min = 44.dp).testTag("erase-dictation")) {
                        Text("Erase all")
                    }
                }
            }
            if (languageHelp) TextButton(onClick = {
                try { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                catch (_: Exception) { message = "Open Android Settings → voice input and add $languageName to your speech service." }
            }) { Text("Open speech settings") }
            if (partial.isNotBlank()) Text(partial, Modifier.padding(bottom = 16.dp), fontFamily = DiarySerif, fontSize = 19.sp, color = Ink)
            if (reviewing) {
                OutlinedTextField(transcript, { transcript = it }, Modifier.fillMaxWidth().testTag("dictation-transcript"), label = { Text("Review your words") }, minLines = 3)
                Button(onClick = { model.appendTranscript(dreamId, transcript.trim()); transcript = ""; onClose() },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp), enabled = !listening && !recording && transcript.isNotBlank()) { Text("Add to dream") }
            }
            SectionCaption("Keep your voice, too")
            Text("Save an audio note to listen to later.", color = Pencil, fontSize = 14.sp)
            OutlinedButton(onClick = { if (recording) stopRecording() else request("record") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp).testTag("record-audio"), enabled = !listening) {
                Text(if (recording) "Stop & keep · ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}" else "Record a voice note")
            }
            PanelLabel("Kept with this dream")
            if (entry.audio.isEmpty()) Text("Your voice notes will live here.", color = Pencil)
            entry.audio.forEachIndexed { i, clip ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Note ${i + 1} · ${clip.seconds / 60}:${(clip.seconds % 60).toString().padStart(2, '0')}", Modifier.weight(1f), color = Ink)
                    TextButton(onClick = {
                        if (playing == clip.file) stopPlayer()
                        else {
                            stopPlayer()
                            try {
                                val media = MediaPlayer()
                                player = media
                                media.setDataSource(File(context.filesDir, "audio/${clip.file}").absolutePath)
                                media.setOnCompletionListener { stopPlayer() }
                                media.setOnErrorListener { _, _, _ -> stopPlayer(); message = "This note couldn’t be played."; true }
                                media.prepare(); media.start(); playing = clip.file
                            } catch (_: Exception) { stopPlayer(); message = "This note couldn’t be opened." }
                        }
                    }) { Text(if (playing == clip.file) "Stop" else "Play") }
                    TextButton(onClick = { remove = clip }) { Text("Remove") }
                }
            }
        }
    }
    remove?.let { clip -> AlertDialog(onDismissRequest = { remove = null }, containerColor = Paper,
        title = { Text("Remove this voice note?") },
        confirmButton = { TextButton(onClick = { stopPlayer(); model.removeAudio(clip); remove = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("Keep") } }) }
}
