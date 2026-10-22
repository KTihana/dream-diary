package com.afterglow.diary

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-wide transaction lock also covers notification and audio-service writes. */
class LucidPracticeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lucid-practice", Context.MODE_PRIVATE)
    fun load(): LucidPracticeData = synchronized(lock) {
        prefs.getString("notebook", null)?.let(LucidPracticeCodec::decode) ?: LucidPracticeData()
    }
    fun update(edit: (LucidPracticeData) -> LucidPracticeData): LucidPracticeData = synchronized(lock) {
        val before = load()
        val after = edit(before)
        if (after != before) prefs.edit().putString("notebook", LucidPracticeCodec.encode(after)).apply()
        after
    }
    fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
    companion object {
        private val lock = Any()
    }
}

data class LucidPracticeState(val data: LucidPracticeData? = null, val error: String? = null)

class LucidPracticeController(private val context: Context) {
    private val store = LucidPracticeStore(context)
    private val mutableState = MutableStateFlow(LucidPracticeState())
    val state = mutableState.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "notebook") refresh() }
    init {
        store.listen(listener)
        refresh()
        if (mutableState.value.data != null) runCatching { PracticeAlarms.reschedule(context) }
    }
    fun refresh() {
        mutableState.value = try { LucidPracticeState(store.load()) }
        catch (_: Exception) { LucidPracticeState(error = "Your practice notes couldn't be opened. They have been kept. Your dream diary is unaffected.") }
    }
    fun close() = store.unlisten(listener)
    fun edit(change: (LucidPracticeData) -> LucidPracticeData): Boolean {
        if (mutableState.value.data == null) return false
        return try {
            mutableState.value = LucidPracticeState(store.update(change))
            true
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(error = "These practice notes couldn't be saved. Try again.")
            false
        }
    }
    fun settings(value: LucidSettings): Boolean {
        if (!validLucidSettings(value)) return false
        val saved = edit { it.copy(settings = value, snoozeUntil = 0) }
        if (saved) runCatching { PracticeAlarms.reschedule(context) }.onFailure {
            mutableState.value = mutableState.value.copy(error = "The notes were saved, but Android couldn't schedule the reminders. Open notification settings and try again.")
        }
        return saved
    }
    fun editNight(date: LocalDate, change: (PracticeNight) -> PracticeNight): Boolean = edit { data ->
        data.editNight(date) { current -> change(current).let { value -> value.copy(date = date,
            intention = value.intention.take(500), goal = value.goal.take(500), realization = value.realization.take(4000),
            sleepNote = value.sleepNote.take(2000), sleepQuality = value.sleepQuality.coerceIn(0, 5),
            lucid = if (value.recall == DreamRecall.NoRecall) null else value.lucid) } }
    }
    fun record(method: PracticeMethod, date: LocalDate, runId: String, detail: String = ""): Boolean =
        edit { it.record(PracticeEvent(id = runId, night = date, method = method, detail = detail.take(500))) }
    fun pauseToday() {
        val tomorrow = ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
        mutableState.value.data?.settings?.let { settings(it.copy(pausedUntil = tomorrow.toInstant().toEpochMilli())) }
    }
    fun armNight(): Boolean {
        return try {
            require(PracticeAlarms.canUseExactAlarms(context)) { "Allow Alarms & reminders in Android settings first." }
            require(PracticeAlarms.canNotify(context)) { "Allow Dream Diary notifications first." }
            val data = store.load()
            val s = data.settings
            PracticeAlarms.ensureChannels(context, s)
            require(context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(PracticeAlarms.NIGHT_CHANNEL)?.importance != android.app.NotificationManager.IMPORTANCE_NONE) {
                "Allow Optional nighttime tools in Android's notification settings."
            }
            require(!s.wakeEnabled || !s.cuesEnabled || s.cuesAfterHours > s.wakeAfterHours) { "Set sound cues later than the wake-up reminder." }
            require(!s.cuesEnabled || trainedForCue(data, s.cue)) { "Complete today's sound-cue rehearsal with this sound first." }
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            require(!s.cuesEnabled || audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) > 0) { "Your media volume is muted. Test the sound before starting cues." }
            require(!s.wakeEnabled || audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM) > 0) { "Your alarm volume is muted. Adjust it before starting a wake reminder." }
            val session = makeNightSession(ZonedDateTime.now(), s)
            if (!edit { it.copy(session = session, notice = "") }) return false
            PracticeAlarms.scheduleNight(context)
            PracticeAlarms.showNightStatus(context)
            true
        } catch (error: Exception) {
            runCatching { PracticeAlarms.stopNight(context) }
            mutableState.value = mutableState.value.copy(error = error.message ?: "This night's reminders couldn't be started.")
            false
        }
    }
    fun stopNight() { runCatching { PracticeAlarms.stopNight(context) }; refresh() }
}
