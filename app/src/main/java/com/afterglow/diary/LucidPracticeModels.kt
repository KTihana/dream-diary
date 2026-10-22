package com.afterglow.diary

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

enum class PracticeMethod(val label: String) {
    RealityCheck("Reality check"), MILD("MILD"), SSILD("SSILD"), WBTB("Wake back to bed"),
    SoundTraining("Sound-cue rehearsal"), SoundCue("Sound cues played")
}
enum class DreamRecall(val label: String) { Unknown("Not recorded"), Remembered("I remember a dream"), NoRecall("No dream recalled") }
enum class LucidCue(val label: String) { Bell("Little bell"), Taps("Soft taps") }

data class LucidSettings(
    val realityEnabled: Boolean = false,
    val checksPerDay: Int = 3,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 21 * 60,
    val variedTimes: Boolean = true,
    val days: Set<Int> = (1..7).toSet(),
    val notificationSound: Boolean = false,
    val vibration: Boolean = true,
    val pausedUntil: Long = 0,
    val signs: List<String> = emptyList(),
    val bedtimeEnabled: Boolean = false,
    val bedtimeMinute: Int = 21 * 60 + 30,
    val wakeEnabled: Boolean = false,
    val wakeAfterHours: Int = 5,
    val cuesEnabled: Boolean = false,
    val cuesAfterHours: Int = 6,
    val cueCount: Int = 3,
    val cueGapMinutes: Int = 15,
    val cue: LucidCue = LucidCue.Bell,
    val cueVolume: Float = .15f,
) : java.io.Serializable

/** A night is named for its waking date, not the date on which bedtime began. */
data class PracticeNight(
    val date: LocalDate,
    val intention: String = "",
    val goal: String = "",
    val methods: Set<PracticeMethod> = emptySet(),
    val checks: Int = 0,
    val recall: DreamRecall = DreamRecall.Unknown,
    val lucid: Boolean? = null,
    val realization: String = "",
    val sleepQuality: Int = 0,
    val sleepNote: String = "",
)

data class PracticeEvent(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    val night: LocalDate,
    val method: PracticeMethod,
    val detail: String = "",
)

data class LucidNightSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long,
    val night: LocalDate,
    val wakeAt: Long = 0,
    val cueAt: Long = 0,
    val remainingCues: Int = 0,
    val cueGapMinutes: Int = 15,
    val cue: LucidCue = LucidCue.Bell,
    val volume: Float = .15f,
    val playbackRequestedAt: Long = 0,
)

data class LucidPracticeData(
    val settings: LucidSettings = LucidSettings(),
    val nights: List<PracticeNight> = emptyList(),
    val events: List<PracticeEvent> = emptyList(),
    val session: LucidNightSession? = null,
    val snoozeUntil: Long = 0,
    val pendingRealityAt: Long = 0,
    val pendingBedtimeAt: Long = 0,
    val notice: String = "",
) {
    fun night(date: LocalDate) = nights.firstOrNull { it.date == date } ?: PracticeNight(date)
    fun editNight(date: LocalDate, edit: (PracticeNight) -> PracticeNight): LucidPracticeData =
        copy(nights = (nights.filterNot { it.date == date } + edit(night(date))).sortedByDescending { it.date })

    fun record(event: PracticeEvent): LucidPracticeData {
        if (events.any { it.id == event.id }) return this
        return editNight(event.night) { it.copy(methods = it.methods + event.method,
            checks = it.checks + if (event.method == PracticeMethod.RealityCheck) 1 else 0) }
            .copy(events = (events + event).takeLast(1000))
    }
}

fun validLucidSettings(s: LucidSettings): Boolean = s.checksPerDay in 1..12 &&
    s.startMinute in 0..1439 && s.endMinute in 1..1440 && s.endMinute - s.startMinute >= s.checksPerDay * 10 &&
    s.days.isNotEmpty() && s.days.all { it in 1..7 } && s.bedtimeMinute in 0..1439 &&
    s.wakeAfterHours in 5..7 && s.cuesAfterHours in 5..8 && s.cueCount in 1..5 &&
    s.cueGapMinutes in 15..60 && s.cueVolume.isFinite() && s.cueVolume in .05f.. .5f &&
    s.signs.size <= 30 && s.signs.all { it.length in 1..80 }

/** Deterministic per date: reopening the app never rolls a new set of random times. */
fun realityCheckTimes(date: LocalDate, settings: LucidSettings): List<LocalTime> {
    require(validLucidSettings(settings))
    if (date.dayOfWeek.value !in settings.days) return emptyList()
    val width = settings.endMinute - settings.startMinute
    val random = Random(date.toEpochDay())
    return (0 until settings.checksPerDay).map { slot ->
        val left = settings.startMinute + slot * width / settings.checksPerDay
        val right = settings.startMinute + (slot + 1) * width / settings.checksPerDay
        val minute = if (settings.variedTimes) random.nextInt(left, right) else (left + right) / 2
        LocalTime.of(minute / 60, minute % 60)
    }
}

fun insideRealityWindow(now: ZonedDateTime, s: LucidSettings): Boolean = s.realityEnabled &&
    now.dayOfWeek.value in s.days && now.hour * 60 + now.minute in s.startMinute until s.endMinute &&
    now.toInstant().toEpochMilli() >= s.pausedUntil

fun nextRealityCheck(now: ZonedDateTime, s: LucidSettings): ZonedDateTime? {
    if (!s.realityEnabled) return null
    return (0L..14L).flatMap { day ->
        realityCheckTimes(now.toLocalDate().plusDays(day), s).map { time ->
            now.toLocalDate().plusDays(day).atTime(time).atZone(now.zone)
        }
    }.filter { it.isAfter(now) && insideRealityWindow(it, s) }.minByOrNull { it.toInstant() }
}

fun nextBedtimePractice(now: ZonedDateTime, s: LucidSettings): ZonedDateTime? {
    if (!s.bedtimeEnabled) return null
    return (0L..7L).map { day -> now.toLocalDate().plusDays(day)
        .atTime(s.bedtimeMinute / 60, s.bedtimeMinute % 60).atZone(now.zone) }
        .firstOrNull { it.isAfter(now) && it.dayOfWeek.value in s.days }
}

fun makeNightSession(now: ZonedDateTime, s: LucidSettings): LucidNightSession {
    require(validLucidSettings(s) && (s.wakeEnabled || s.cuesEnabled))
    require(!s.wakeEnabled || !s.cuesEnabled || s.cuesAfterHours > s.wakeAfterHours)
    val morning = now.plusHours(if (s.cuesEnabled) s.cuesAfterHours.toLong() else s.wakeAfterHours.toLong()).toLocalDate()
    return LucidNightSession(startedAt = now.toInstant().toEpochMilli(), night = morning,
        wakeAt = if (s.wakeEnabled) now.plusHours(s.wakeAfterHours.toLong()).toInstant().toEpochMilli() else 0,
        cueAt = if (s.cuesEnabled) now.plusHours(s.cuesAfterHours.toLong()).toInstant().toEpochMilli() else 0,
        remainingCues = if (s.cuesEnabled) s.cueCount else 0, cueGapMinutes = s.cueGapMinutes, cue = s.cue, volume = s.cueVolume)
}

fun upcomingPracticeNight(now: ZonedDateTime = ZonedDateTime.now()): LocalDate =
    if (now.hour < 6) now.toLocalDate() else now.toLocalDate().plusDays(1)

fun trainedForCue(data: LucidPracticeData, cue: LucidCue, now: Long = System.currentTimeMillis()): Boolean =
    data.events.any { it.method == PracticeMethod.SoundTraining && it.detail == cue.name && it.at <= now && now - it.at < 24 * 60 * 60_000L }

/** An unrecalled or unanswered night stays unknown. A lucid dream report establishes a lucid night. */
fun nightLucidity(night: PracticeNight, entries: List<DreamEntry>): Boolean? {
    val dreams = entries.filter { it.date == night.date && (it.body.isNotBlank() || it.title.isNotBlank() || it.audio.isNotEmpty() || it.strokes.isNotEmpty()) }
    if (dreams.any { it.lucid == true }) return true
    if (night.recall == DreamRecall.NoRecall) return null
    return night.lucid ?: if (dreams.isNotEmpty() && dreams.all { it.lucid == false }) false else null
}

data class PracticeComparison(val method: PracticeMethod, val practiced: Int, val answered: Int, val lucid: Int)
fun practiceComparisons(data: LucidPracticeData, entries: List<DreamEntry>, through: LocalDate): List<PracticeComparison> =
    PracticeMethod.entries.map { method ->
        val nights = data.nights.filter { !it.date.isAfter(through) && method in it.methods }
        val outcomes = nights.map { nightLucidity(it, entries) }
        PracticeComparison(method, nights.size, outcomes.count { it != null }, outcomes.count { it == true })
    }.filter { it.practiced > 0 }

/** Separate versioned practice data leaves every existing diary schema unchanged. */
object LucidPracticeCodec {
    fun encode(data: LucidPracticeData): String {
        val s = data.settings
        require(validLucidSettings(s))
        return JSONObject().put("version", 1).put("settings", JSONObject()
            .put("reality", s.realityEnabled).put("count", s.checksPerDay).put("start", s.startMinute).put("end", s.endMinute)
            .put("varied", s.variedTimes).put("days", JSONArray(s.days.sorted())).put("sound", s.notificationSound)
            .put("vibration", s.vibration).put("paused", s.pausedUntil).put("signs", JSONArray(s.signs))
            .put("bedtime", s.bedtimeEnabled).put("bedtimeMinute", s.bedtimeMinute).put("wake", s.wakeEnabled)
            .put("wakeHours", s.wakeAfterHours).put("cues", s.cuesEnabled).put("cueHours", s.cuesAfterHours)
            .put("cueCount", s.cueCount).put("cueGap", s.cueGapMinutes).put("cue", s.cue.name).put("volume", s.cueVolume))
            .put("nights", JSONArray().apply { data.nights.forEach { n -> put(JSONObject().put("date", n.date.toString())
                .put("intention", n.intention).put("goal", n.goal).put("methods", JSONArray(n.methods.map { it.name }))
                .put("checks", n.checks).put("recall", n.recall.name).put("lucid", n.lucid ?: JSONObject.NULL)
                .put("realization", n.realization).put("quality", n.sleepQuality).put("sleepNote", n.sleepNote)) } })
            .put("events", JSONArray().apply { data.events.forEach { e -> put(JSONObject().put("id", e.id).put("at", e.at)
                .put("night", e.night.toString()).put("method", e.method.name).put("detail", e.detail)) } })
            .put("session", data.session?.let { n -> JSONObject().put("id", n.id).put("start", n.startedAt).put("night", n.night.toString())
                .put("wake", n.wakeAt).put("cueAt", n.cueAt).put("remaining", n.remainingCues).put("gap", n.cueGapMinutes)
                .put("cue", n.cue.name).put("volume", n.volume).put("playbackAt", n.playbackRequestedAt) } ?: JSONObject.NULL)
            .put("snooze", data.snoozeUntil).put("realityAt", data.pendingRealityAt).put("bedtimeAt", data.pendingBedtimeAt)
            .put("notice", data.notice).toString()
    }

    fun decode(json: String): LucidPracticeData {
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported practice version" }
        val s = root.getJSONObject("settings")
        val settings = LucidSettings(realityEnabled = s.getBoolean("reality"), checksPerDay = s.getInt("count"),
            startMinute = s.getInt("start"), endMinute = s.getInt("end"), variedTimes = s.getBoolean("varied"),
            days = s.getJSONArray("days").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            notificationSound = s.getBoolean("sound"), vibration = s.getBoolean("vibration"), pausedUntil = s.getLong("paused"),
            signs = s.getJSONArray("signs").let { a -> (0 until a.length()).map { a.getString(it) } },
            bedtimeEnabled = s.getBoolean("bedtime"), bedtimeMinute = s.getInt("bedtimeMinute"),
            wakeEnabled = s.getBoolean("wake"), wakeAfterHours = s.getInt("wakeHours"), cuesEnabled = s.getBoolean("cues"),
            cuesAfterHours = s.getInt("cueHours"), cueCount = s.getInt("cueCount"), cueGapMinutes = s.getInt("cueGap"),
            cue = LucidCue.valueOf(s.getString("cue")), cueVolume = s.getDouble("volume").toFloat())
        require(validLucidSettings(settings))
        val nights = root.getJSONArray("nights").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { n ->
            PracticeNight(LocalDate.parse(n.getString("date")), n.getString("intention"), n.getString("goal"),
                n.getJSONArray("methods").let { m -> (0 until m.length()).map { PracticeMethod.valueOf(m.getString(it)) }.toSet() },
                n.getInt("checks").also { require(it >= 0) }, DreamRecall.valueOf(n.getString("recall")),
                if (n.isNull("lucid")) null else n.getBoolean("lucid"), n.getString("realization"),
                n.getInt("quality").also { require(it in 0..5) }, n.getString("sleepNote"))
        } } }
        require(nights.map { it.date }.distinct().size == nights.size)
        val events = root.getJSONArray("events").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { e ->
            PracticeEvent(e.getString("id"), e.getLong("at"), LocalDate.parse(e.getString("night")),
                PracticeMethod.valueOf(e.getString("method")), e.getString("detail"))
        } } }
        val session = if (root.isNull("session")) null else root.getJSONObject("session").let { n ->
            LucidNightSession(n.getString("id"), n.getLong("start"), LocalDate.parse(n.getString("night")), n.getLong("wake"),
                n.getLong("cueAt"), n.getInt("remaining").also { require(it in 0..5) }, n.getInt("gap").also { require(it in 15..60) },
                LucidCue.valueOf(n.getString("cue")), n.getDouble("volume").toFloat().also { require(it.isFinite() && it in .05f.. .5f) }, n.optLong("playbackAt"))
        }
        return LucidPracticeData(settings, nights, events, session, root.getLong("snooze"), root.getLong("realityAt"),
            root.getLong("bedtimeAt"), root.optString("notice"))
    }
}
