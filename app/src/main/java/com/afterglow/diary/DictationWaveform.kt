package com.afterglow.diary

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Only real recognizer levels feed the envelope; silence and missing callbacks stay flat. */
@Stable
internal class DictationSoundLevels {
    var ready by mutableStateOf(false)
    var hasLevels by mutableStateOf(false)
        private set
    private var level = 0f
    private var lastLevelAt = 0L
    var samples by mutableStateOf(List(36) { 0f })
        private set
    var soundDetected by mutableStateOf(false)
        private set

    fun receive(rmsDb: Float) {
        if (!rmsDb.isFinite()) return
        hasLevels = true
        // Android speech providers report relative RMS dB, not calibrated sound pressure.
        level = (rmsDb / 10f).coerceIn(0f, 1f)
        lastLevelAt = SystemClock.elapsedRealtime()
    }

    fun sample() {
        val current = if (hasLevels && SystemClock.elapsedRealtime() - lastLevelAt < 250) level else 0f
        samples = samples.drop(1) + current
        soundDetected = current > .12f
    }

    fun reset() {
        ready = false
        hasLevels = false
        level = 0f
        lastLevelAt = 0L
        samples = List(36) { 0f }
        soundDetected = false
    }
}

@Composable
internal fun DictationWaveform(levels: DictationSoundLevels, languageName: String) {
    val accent = Accent
    val status = when {
        !levels.ready -> "Preparing microphone"
        levels.soundDetected -> "Sound detected"
        levels.hasLevels -> "Listening for your voice"
        else -> "Speak when you’re ready"
    }
    LaunchedEffect(levels) {
        while (true) {
            levels.sample()
            delay(70)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)
        .background(ControlPaper, RoundedCornerShape(18.dp))
        .testTag("dictation-waveform")
        .semantics(mergeDescendants = true) {
            contentDescription = "Microphone input"
            stateDescription = status
        }
        .padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(if (levels.ready) accent else Pencil, CircleShape))
            Text(if (levels.ready) "Listening · $languageName" else "Preparing microphone…",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp).height(38.dp)) {
            val step = size.width / levels.samples.size
            val width = 3.dp.toPx().coerceAtMost(step * .55f)
            levels.samples.forEachIndexed { index, sample ->
                val height = width + (size.height - width) * sample
                val x = step * (index + .5f)
                drawLine(accent.copy(alpha = if (sample > .02f) .8f else .18f),
                    Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2),
                    strokeWidth = width, cap = StrokeCap.Round)
            }
        }
        Text(status, fontSize = 12.sp, color = Pencil)
    }
}
