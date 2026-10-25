package com.afterglow.diary

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Every search term must match; quoted phrases remain together. */
fun searchDreams(entries: List<DreamEntry>, query: String, lucid: Boolean? = null,
    bookmarksOnly: Boolean = false, after: LocalDate? = null, before: LocalDate? = null): List<DreamEntry> {
    val terms = Regex("\"([^\"]+)\"|(\\S+)").findAll(query.trim()).map {
        it.groups[1]?.value ?: it.groups[2]!!.value
    }.toList()
    return entries.filter { dream ->
        val text = listOf(dream.title, dream.body.replace('\u000C', '\n'), dream.date.toString(),
            dream.symbols.joinToString(" "), dream.people.joinToString(" "), dream.places.joinToString(" ")).joinToString(" ")
        (lucid == null || dream.lucid == lucid) && (!bookmarksOnly || dream.bookmarked) &&
            (after == null || !dream.date.isBefore(after)) && (before == null || !dream.date.isAfter(before)) &&
            terms.all { text.contains(it, ignoreCase = true) }
    }.sortedByDescending { it.date }
}

fun parseLabels(text: String) = text.split(',').map { it.trim().take(80) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(30)
data class PatternCount(val label: String, val count: Int, val dreamIds: List<String>)
fun recurringPatterns(entries: List<DreamEntry>, labels: (DreamEntry) -> List<String>): List<PatternCount> =
    entries.flatMap { dream -> labels(dream).distinctBy { it.lowercase() }.map { it to dream.id } }
        .groupBy { it.first.lowercase() }.values.map { group ->
            PatternCount(group.first().first, group.size, group.map { it.second })
        }.filter { it.count >= 2 }.sortedWith(compareByDescending<PatternCount> { it.count }.thenBy { it.label })

data class SymbolMeaning(val name: String, val meaning: String)
data class NotebookPreferences(
    val ruling: String = "Lined", val font: String = "Book", val cover: String = "Linen",
    val night: Boolean = false, val textSize: Int = 18, val prompts: Boolean = true,
    val reminder: Boolean = false, val reminderHour: Int = 8, val reminderMinute: Int = 0,
    val dictationLanguage: String = "hr-HR",
    val paperTone: String = "Ivory", val inkColor: String = "Charcoal",
    val spacing: String = "Comfortable", val matchTitle: Boolean = false,
)
class NotebookPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("notebook-tools", Context.MODE_PRIVATE)
    fun load() = NotebookPreferences(
        ruling = prefs.getString("ruling", "Lined")!!, font = prefs.getString("font", "Book")!!,
        cover = prefs.getString("cover", "Linen")!!, night = prefs.getBoolean("night", false),
        textSize = prefs.getInt("textSize", 18).coerceIn(16, 26), prompts = prefs.getBoolean("prompts", true),
        reminder = prefs.getBoolean("reminder", false), reminderHour = prefs.getInt("hour", 8), reminderMinute = prefs.getInt("minute", 0),
        dictationLanguage = prefs.getString("dictationLanguage", "hr-HR")?.takeIf { it in setOf("hr-HR", "en-US") } ?: "hr-HR",
        paperTone = prefs.getString("paperTone", "Ivory")?.takeIf { it in PaperTones } ?: "Ivory",
        inkColor = prefs.getString("inkColor", "Charcoal")?.takeIf { it in InkColors } ?: "Charcoal",
        spacing = prefs.getString("spacing", "Comfortable")?.takeIf { it in WritingSpacing } ?: "Comfortable",
        matchTitle = prefs.getBoolean("matchTitle", false),
    )
    fun save(p: NotebookPreferences) { prefs.edit().putString("ruling", p.ruling).putString("font", p.font)
        .putString("cover", p.cover).putBoolean("night", p.night).putInt("textSize", p.textSize)
        .putBoolean("prompts", p.prompts).putBoolean("reminder", p.reminder)
        .putInt("hour", p.reminderHour).putInt("minute", p.reminderMinute)
        .putString("dictationLanguage", p.dictationLanguage)
        .putString("paperTone", p.paperTone).putString("inkColor", p.inkColor)
        .putString("spacing", p.spacing).putBoolean("matchTitle", p.matchTitle).apply() }
    fun selectedDreamId(): String? = prefs.getString("selectedDream", null)
    fun selectDream(id: String) { prefs.edit().putString("selectedDream", id).apply() }
    fun meanings(): List<SymbolMeaning> = JSONArray(prefs.getString("meanings", "[]")).let { a ->
        List(a.length()) { a.getJSONObject(it).let { d -> SymbolMeaning(d.getString("name"), d.getString("meaning")) } }
    }
    fun saveMeanings(meanings: List<SymbolMeaning>) { prefs.edit().putString("meanings", JSONArray().apply {
        meanings.forEach { put(JSONObject().put("name", it.name).put("meaning", it.meaning)) }
    }.toString()).apply() }
}
val RecallPrompts = listOf("Where were you, just before you woke?", "Who was there with you?",
    "What is the smallest detail you remember?", "Was there a colour, a sound, or a familiar place?",
    "What happened just before the last thing you remember?")

/** Ranges never trim whitespace. A form feed is an explicit new sheet, excluded from its text field. */
data class PaperSlice(val start: Int, val end: Int)
fun paginatePaper(text: String, fittingEnd: (String) -> Int): List<PaperSlice> {
    val pages = mutableListOf<PaperSlice>()
    var segmentStart = 0
    do {
        val breakAt = text.indexOf('\u000C', segmentStart).let { if (it < 0) text.length else it }
        var start = segmentStart
        if (start == breakAt) pages += PaperSlice(start, start)
        while (start < breakAt) {
            val remaining = text.substring(start, breakAt)
            var length = fittingEnd(remaining).coerceIn(1, remaining.length)
            // Do not split a UTF-16 surrogate pair if a fallback measurer returns one code unit.
            if (length < remaining.length && length > 0 && remaining[length - 1].isHighSurrogate() && remaining[length].isLowSurrogate()) length++
            pages += PaperSlice(start, start + length)
            start += length
        }
        segmentStart = breakAt + 1
    } while (segmentStart <= text.length)
    return pages
}
