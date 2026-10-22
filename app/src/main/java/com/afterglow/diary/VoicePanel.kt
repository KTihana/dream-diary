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
    var elapsed by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var transcript by rememberSaveable { mutableStateOf("") }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
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
    fun startRecord() {
        stopPlayer(); recognizer?.destroy(); recognizer = null; listening = false; finishing = false
        soundLevels.reset()
        try { recorder.start(); elapsed = 0; recording = true; message = "" }
        catch (_: Exception) { message = "The microphone couldn’t start. Check that another app isn’t using it, then try again." }
    }
    fun dictate(preferOnDevice: Boolean = true) {
        if (recording) stopRecording()
        stopPlayer()
        languageHelp = false
        soundLevels.reset()
        val useOnDevice = preferOnDevice && hasOnDevice
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !useOnDevice) {
            message = "Speech recognition isn’t installed on this device. You can still save an audio recording or type your dream."; return
        }
        recognizer?.destroy()
        try {
            val speech = if (useOnDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (recognizer === speech && listening && !finishing) {
                        soundLevels.ready = true
                        message = "Listening · $languageName…"
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (recognizer === speech && listening && !finishing) soundLevels.ready = true
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (recognizer === speech && listening && !finishing) {
                        if (rmsdB.isFinite()) soundLevels.ready = true
                        soundLevels.receive(rmsdB)
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (recognizer !== speech) return
                    finishing = true; message = "Finishing transcription…"
                    soundLevels.reset()
                }
                override fun onError(error: Int) {
                    if (recognizer !== speech) return
                    listening = false; finishing = false; partial = ""
                    soundLevels.reset()
                    if (useOnDevice && error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
                        dictate(preferOnDevice = false)
                        return
                    }
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
                    if (recognizer !== speech) return
                    listening = false; finishing = false; partial = ""
                    soundLevels.reset()
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    if (words.isNotBlank()) {
                        transcript += (if (transcript.isBlank()) "" else " ") + words
                        reviewing = true
                    }
                    message = if (words.isBlank()) "No words were caught. Try again when you’re ready." else "Review your words, or dictate a little more."
                }
                override fun onPartialResults(results: Bundle?) {
                    if (recognizer === speech) partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            listening = true; finishing = false; partial = ""; message = "Listening · $languageName…"
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice))
        } catch (_: Exception) {
            recognizer?.destroy(); recognizer = null; listening = false; finishing = false
            soundLevels.reset()
            if (useOnDevice) { dictate(preferOnDevice = false); return }
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
                recognizer?.destroy(); recognizer = null; listening = false; finishing = false; partial = ""
                soundLevels.reset()
                stopPlayer()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            if (recording) stopRecording()
            recognizer?.destroy(); recognizer = null
            soundLevels.reset()
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
            Button(onClick = {
                if (listening) { finishing = true; soundLevels.reset(); message = "Finishing transcription…"; recognizer?.stopListening() }
                else request("dictate")
            }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("dictate-dream"), enabled = !recording && !finishing) {
                Icon(DreamIcons.Microphone, null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (finishing) "Finishing…" else if (listening) "Finish dictation" else if (transcript.isNotBlank()) "Dictate more" else "Start dictation")
            }
            if (listening && !finishing) DictationWaveform(soundLevels, languageName)
            Text("Uses on-device speech when the language is available. Otherwise your Android speech service may need internet access.",
                Modifier.padding(top = 12.dp), color = Pencil, fontSize = 12.sp, lineHeight = 18.sp)
            if (message.isNotEmpty() && (!listening || finishing)) Text(message, Modifier.padding(vertical = 16.dp).testTag("voice-status"), color = Pencil)
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
