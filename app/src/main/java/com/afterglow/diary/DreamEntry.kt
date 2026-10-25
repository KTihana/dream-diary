package com.afterglow.diary

import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class InkPoint(val x: Float, val y: Float)
data class InkStroke(val points: List<InkPoint>, val color: Long = 0xFF292D30,
    val width: Float = .006f, val opacity: Float = 1f, val eraser: Boolean = false)
data class DiaryDrawing(
    val id: String = UUID.randomUUID().toString(), val page: Int = 0,
    val x: Float = .5f, val y: Float = .5f, val scale: Float = 1f, val rotation: Float = 0f,
    val aspect: Float = .75f, val opacity: Float = 1f, val behindText: Boolean = false,
    val background: Long? = null, val strokes: List<InkStroke> = emptyList(),
)
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
    val drawings: List<DiaryDrawing> = emptyList(),
    val audio: List<AudioClip> = emptyList(),
)

/** Old sketches stay readable and become editable page layers on the first drawing edit. */
val DreamEntry.pageDrawings: List<DiaryDrawing> get() =
    (if (strokes.isEmpty()) emptyList() else listOf(DiaryDrawing(id = "legacy-$id", strokes = strokes.map { stroke ->
        stroke.copy(points = stroke.points.map { InkPoint(it.x - .5f, (it.y - .5f) / .75f) })
    }))) + drawings
val DreamEntry.hasDrawing: Boolean get() = strokes.isNotEmpty() || drawings.any { it.strokes.isNotEmpty() }

/** Version 3 adds editable, transparent drawings; versions 1 and 2 remain readable. */
object JournalCodec {
    fun encode(entries: List<DreamEntry>): String = JSONObject()
        .put("version", 3)
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
                    .put("drawings", JSONArray().apply { dream.drawings.forEach { drawing ->
                        put(JSONObject().put("id", drawing.id).put("page", drawing.page)
                            .put("x", drawing.x).put("y", drawing.y).put("scale", drawing.scale)
                            .put("rotation", drawing.rotation).put("aspect", drawing.aspect)
                            .put("opacity", drawing.opacity).put("behindText", drawing.behindText)
                            .put("background", drawing.background ?: JSONObject.NULL)
                            .put("strokes", JSONArray().apply { drawing.strokes.forEach { stroke ->
                                put(JSONObject().put("color", stroke.color).put("width", stroke.width)
                                    .put("opacity", stroke.opacity).put("eraser", stroke.eraser)
                                    .put("points", JSONArray().apply { stroke.points.forEach {
                                        put(JSONArray(listOf(it.x, it.y)))
                                    } }))
                            } }))
                    } })
                    .put("audio", JSONArray().apply { dream.audio.forEach { clip ->
                        put(JSONObject().put("file", clip.file).put("seconds", clip.seconds))
                    } }))
            }
        }).toString(2)

    fun decode(json: String): List<DreamEntry> {
        val root = JSONObject(json)
        require(root.getInt("version") in 1..3) { "Unsupported notebook version" }
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
                drawings = d.optJSONArray("drawings")?.let { drawings -> List(drawings.length()) { index ->
                    val drawing = drawings.getJSONObject(index)
                    DiaryDrawing(id = drawing.getString("id").also { require(it.isNotBlank()) },
                        page = drawing.getInt("page").also { require(it in 0..10000) },
                        x = drawing.number("x", -10f, 10f), y = drawing.number("y", -10f, 10f),
                        scale = drawing.number("scale", .05f, 10f), rotation = drawing.number("rotation", -360f, 360f),
                        aspect = drawing.number("aspect", .05f, 20f), opacity = drawing.number("opacity", 0f, 1f),
                        behindText = drawing.optBoolean("behindText"),
                        background = if (drawing.isNull("background")) null else drawing.getLong("background"),
                        strokes = drawing.getJSONArray("strokes").let { strokes -> List(strokes.length()) { s ->
                            val stroke = strokes.getJSONObject(s)
                            InkStroke(points = stroke.getJSONArray("points").let { points -> List(points.length()) { p ->
                                val point = points.getJSONArray(p)
                                InkPoint(point.getDouble(0).toFloat().also { require(it.isFinite()) },
                                    point.getDouble(1).toFloat().also { require(it.isFinite()) })
                            } }, color = stroke.getLong("color"), width = stroke.number("width", .00001f, 20f),
                                opacity = stroke.number("opacity", 0f, 1f), eraser = stroke.optBoolean("eraser"))
                        } })
                } }.orEmpty().also { require(it.map { drawing -> drawing.id }.distinct().size == it.size) },
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
    private fun JSONObject.number(key: String, min: Float, max: Float) = getDouble(key).toFloat().also {
        require(it.isFinite() && it in min..max) { "Invalid drawing $key" }
    }
}
