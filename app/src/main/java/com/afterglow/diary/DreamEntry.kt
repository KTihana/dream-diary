package com.afterglow.diary

import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class InkPoint(val x: Float, val y: Float)
data class InkStroke(val points: List<InkPoint>)
data class AudioClip(val file: String, val seconds: Int)
data class DreamEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val body: String = "",
    val lucid: Boolean? = null,
    val bookmarked: Boolean = false,
    val symbols: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val awareness: Int = 0,
    val control: Int = 0,
    val technique: String = "",
    val lucidMinutes: String = "",
    val strokes: List<InkStroke> = emptyList(),
    val audio: List<AudioClip> = emptyList(),
)

/** Version 2 adds notebook tools; version 1 diaries are migrated without losing any text. */
object JournalCodec {
    fun encode(entries: List<DreamEntry>): String = JSONObject()
        .put("version", 2)
        .put("entries", JSONArray().apply {
            entries.forEach { dream ->
                put(JSONObject()
                    .put("id", dream.id).put("date", dream.date.toString())
                    .put("title", dream.title).put("body", dream.body)
                    .put("lucid", dream.lucid ?: JSONObject.NULL)
                    .put("bookmarked", dream.bookmarked)
                    .put("symbols", JSONArray(dream.symbols)).put("people", JSONArray(dream.people))
                    .put("places", JSONArray(dream.places)).put("awareness", dream.awareness)
                    .put("control", dream.control).put("technique", dream.technique)
                    .put("lucidMinutes", dream.lucidMinutes)
                    .put("strokes", JSONArray().apply { dream.strokes.forEach { stroke ->
                        put(JSONArray().apply { stroke.points.forEach { point ->
                            put(JSONArray(listOf(point.x, point.y)))
                        } })
                    } })
                    .put("audio", JSONArray().apply { dream.audio.forEach { clip ->
                        put(JSONObject().put("file", clip.file).put("seconds", clip.seconds))
                    } }))
            }
        }).toString(2)

    fun decode(json: String): List<DreamEntry> {
        val root = JSONObject(json)
        require(root.getInt("version") in 1..2) { "Unsupported notebook version" }
        val array = root.getJSONArray("entries")
        val entries = List(array.length()) { index ->
            val d = array.getJSONObject(index)
            DreamEntry(
                id = d.getString("id").also { require(it.isNotBlank()) },
                date = LocalDate.parse(d.getString("date")),
                title = d.getString("title"), body = d.getString("body"),
                lucid = if (d.isNull("lucid")) null else d.getBoolean("lucid"),
                bookmarked = d.optBoolean("bookmarked"),
                symbols = d.strings("symbols"), people = d.strings("people"), places = d.strings("places"),
                awareness = d.optInt("awareness").coerceIn(0, 5), control = d.optInt("control").coerceIn(0, 5),
                technique = d.optString("technique"), lucidMinutes = d.optString("lucidMinutes"),
                strokes = d.optJSONArray("strokes")?.let { strokes -> List(strokes.length()) { s ->
                    val points = strokes.getJSONArray(s)
                    InkStroke(List(points.length()) { p -> points.getJSONArray(p).let {
                        InkPoint(it.getDouble(0).toFloat().coerceIn(0f, 1f), it.getDouble(1).toFloat().coerceIn(0f, 1f))
                    } })
                } } ?: emptyList(),
                audio = d.optJSONArray("audio")?.let { clips -> List(clips.length()) { i ->
                    val c = clips.getJSONObject(i)
                    AudioClip(c.getString("file").also { require(it.matches(Regex("[a-zA-Z0-9_-]+\\.m4a"))) }, c.getInt("seconds").coerceAtLeast(0))
                } } ?: emptyList(),
            )
        }
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate dream IDs" }
        return entries
    }
    private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a ->
        List(a.length()) { a.getString(it) }
    } ?: emptyList()
}
