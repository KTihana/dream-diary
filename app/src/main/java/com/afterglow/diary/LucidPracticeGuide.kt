package com.afterglow.diary

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

internal data class LucidTip(val title: String, val text: String, val evidence: String, val source: String? = null)
internal const val MILD_SOURCE = "https://doi.org/10.1111/jsr.13786"
internal const val INDUCTION_SOURCE = "https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2020.01746/full"
internal const val REALITY_SOURCE = "https://doi.org/10.11588/ijodr.2025.2.110523"
internal const val CUE_SOURCE = "https://faculty.wcas.northwestern.edu/~paller/C%26C24.pdf"
internal val LucidTips = listOf(
    LucidTip("Begin with a fragment", "On waking, give the last image a moment to return. Note a place, person, sensation or a few words before trying to write a complete story. You can use the diary's recorder, too.", "Recall practice", INDUCTION_SOURCE),
    LucidTip("A missing memory is not a failed dream", "Choose ‘No dream recalled’ in your morning review when that is what happened. We leave lucidity unknown rather than treating an unremembered night as non-lucid.", "Keeping useful records"),
    LucidTip("Give a reality check your attention", "Pause and reconstruct the last few minutes. Notice your surroundings, inspect your hands, or look away from a short line of text and read it again. The reflection is the practice; finishing a notification is not the goal.", "Mixed evidence", REALITY_SOURCE),
    LucidTip("Choose meaningful dream signs", "Look through your own recurring symbols, people and places. Pick a few cues to practise noticing during the day, and imagine recognizing them in a future dream.", "Personal rehearsal", MILD_SOURCE),
    LucidTip("A check is a clue, not proof", "No hand, clock or text test works perfectly in every dream. Use a few observations together and remember that the reminders are an exercise in attention.", "Practice guidance", REALITY_SOURCE),
    LucidTip("MILD is more than repeating words", "Recall a recent dream. Imagine returning to its scene, noticing something unusual, and realizing you are dreaming. Set the intention to remember this next time. MILD has better research support than daytime checks alone.", "Best-supported cognitive technique", MILD_SOURCE),
    LucidTip("Let the intention settle", "You do not have to keep repeating a phrase until you feel tense. Once the scene and intention feel clear, put the phone down and allow yourself to sleep.", "MILD practice", INDUCTION_SOURCE),
    LucidTip("SSILD: notice without searching", "With your eyes closed, gently cycle through the darkness behind your eyelids, sounds, and sensations in your body. Nothing special has to appear. Stay comfortable rather than trying to force an experience.", "Promising; fewer replications", INDUCTION_SOURCE),
    LucidTip("Awareness and control are different", "Knowing you are dreaming is lucidity. Changing the scene is a separate skill. You can mark a dream lucid even if you could not control what happened.", "Understanding lucidity", MILD_SOURCE),
    LucidTip("Keep one simple dream goal", "You might choose to look at the sky, explore a doorway, or notice the texture of something nearby. Your goal is a personal intention, not a promise about what a dream will do.", "Personal practice idea"),
    LucidTip("Protect the return to sleep", "Nighttime techniques in research often involve waking later in the night. If you choose to try that, keep the interaction quiet and brief. Natural awakenings are also a chance to rehearse; an alarm is optional.", "Sleep interruption is optional", INDUCTION_SOURCE),
    LucidTip("Teach a cue before using it", "Listen to your chosen sound while practising attentive reflection. Revisit that association before bed. The experimental night tool uses the same sound later; simply playing unfamiliar sounds is not the studied training procedure.", "Experimental", CUE_SOURCE),
    LucidTip("A timer does not detect REM", "The sound-cue tool follows the schedule you choose. It does not know whether you are dreaming. Cue studies are encouraging but mixed, and sounds can wake people. Test quietly and stop if they disturb your sleep.", "Experimental", CUE_SOURCE),
    LucidTip("Compare gently", "Try a practice consistently enough to describe it, and log sleep quality as well as lucid dreams. Personal patterns can be interesting, but they cannot establish which technique caused a result. No streak needs protecting.", "Interpreting your history"),
    LucidTip("Make room for ordinary sleep", "You can pause daytime prompts and stop a night session at any time. If practice leaves you tired, anxious or uncomfortable, take a break and prioritize restful sleep.", "Comfort and sleep"),
)

internal data class PracticeStep(val title: String, val text: String, val seconds: Int, val playCue: Boolean = false)
internal val RealitySteps = listOf(
    PracticeStep("Pause for a moment", "Let your attention settle on your surroundings. What can you see, hear and feel?", 5),
    PracticeStep("How did you get here?", "Recall what you were doing a few minutes ago. Does the sequence make sense?", 5),
    PracticeStep("Look carefully", "Notice the details of your hands and something nearby. Observe rather than rushing to an answer.", 5),
    PracticeStep("Look away, then return", "Read a short line of text, look away, and read it again. Reflect on the whole experience. A single test is not conclusive.", 5),
)
internal val SsildSteps: List<PracticeStep> = buildList {
    repeat(4) { cycle ->
        add(PracticeStep("Quick cycle ${cycle + 1} · vision", "Close your eyes and gently notice the darkness behind your eyelids. You do not need to see anything.", 4))
        add(PracticeStep("Quick cycle ${cycle + 1} · hearing", "Let your attention rest on sounds, without trying to find a particular sound.", 4))
        add(PracticeStep("Quick cycle ${cycle + 1} · body", "Notice the weight of your body and the feeling of the bedding.", 4))
    }
    repeat(3) { cycle ->
        add(PracticeStep("Slow cycle ${cycle + 1} · vision", "Rest your attention on the darkness behind your closed eyelids. Keep your eyes relaxed.", 20))
        add(PracticeStep("Slow cycle ${cycle + 1} · hearing", "Notice sounds passively. It is fine if everything is quiet.", 20))
        add(PracticeStep("Slow cycle ${cycle + 1} · body", "Gently notice bodily sensations. Stay comfortable and let go of effort.", 20))
    }
}

/** Optional installed offline voice. Text guides always work without a speech engine. */
internal class PracticeSpeech(private val context: Context) {
    private var engine: TextToSpeech? = null
    private var closed = false
    var ready by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    fun enable() {
        if (engine != null || closed) return
        status = "Preparing the device voice…"
        engine = TextToSpeech(context.applicationContext) { result ->
            Handler(Looper.getMainLooper()).post {
                if (!closed) {
                    val tts = engine
                    val voice = if (result == TextToSpeech.SUCCESS) tts?.voices?.filter { !it.isNetworkConnectionRequired }
                        ?.sortedBy { if (it.locale.language == Locale.getDefault().language) 0 else if (it.locale.language == "en") 1 else 2 }
                        ?.firstOrNull { it.locale.language == "en" } else null
                    if (tts != null && voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                        tts.setSpeechRate(.85f); ready = true; status = "Device voice ready"
                    } else status = "No installed offline English voice is available. You can use the written guide."
                }
            }
        }
    }
    fun say(text: String) {
        if (ready && !closed && engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lucid-step") != TextToSpeech.SUCCESS) {
            ready = false; status = "The device voice couldn't speak. The written guide is still available."
        }
    }
    fun stop() { engine?.stop() }
    fun close() { closed = true; ready = false; engine?.stop(); engine?.shutdown(); engine = null }
}
