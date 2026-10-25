package com.afterglow.diary

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val LibraryPaper: Color @Composable get() = if (LocalNotebookPreferences.current.night) PanelPaper else Color(0xFFF5F2EC)
private val LucidLibraryInk: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFFD2BCED) else Color(0xFF71568E)

@Composable
internal fun LibraryHeading(compact: Boolean = false, onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 16.dp, bottom = if (compact) 20.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Your dreams", fontFamily = DiarySerif, fontSize = if (compact) 28.sp else 36.sp,
                lineHeight = if (compact) 36.sp else 44.sp, letterSpacing = (-1).sp, color = Ink)
            Text("Dreams, drawings & voice notes", Modifier.padding(top = 6.dp), color = Pencil,
                fontSize = 12.sp, lineHeight = 18.sp)
        }
        IconButton(onClick = onNew, modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(ControlPaper).border(.7.dp, Hairline, CircleShape).testTag("library-new-dream")) {
            Icon(DreamIcons.Pen, "Write a new dream", Modifier.size(20.dp), tint = Accent)
        }
    }
}

@Composable
internal fun LibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            listOf("Dreams", "Patterns", "Symbols", "Practice").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).selectable(index == selected, role = Role.Tab, onClick = { onSelect(index) })
                    .testTag("library-tab-$index"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(vertical = 12.dp), fontSize = 13.sp,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected == index) Ink else Pencil)
                    Box(Modifier.width(28.dp).height(2.dp).background(if (selected == index) Accent else Color.Transparent, CircleShape))
                }
            }
        }
        HorizontalDivider(color = Hairline, thickness = .7.dp)
    }
}

@Composable
internal fun LibrarySearch(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    TextField(query, onQuery, Modifier.fillMaxWidth().testTag("dream-search"),
        shape = RoundedCornerShape(14.dp), singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Pencil, modifier = Modifier.size(19.dp)) },
        trailingIcon = if (query.isEmpty()) null else ({ IconButton(onClick = { onQuery("") }) {
            Icon(Icons.Outlined.Close, "Clear search", Modifier.size(17.dp), tint = Pencil)
        } }),
        placeholder = { Text("Search your dreams", color = Pencil, fontSize = 14.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink),
        colors = TextFieldDefaults.colors(focusedContainerColor = Ink.copy(alpha = .045f), unfocusedContainerColor = Ink.copy(alpha = .045f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, cursorColor = Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFilters(filter: String, onFilter: (String) -> Unit, bookmarks: Boolean, onBookmarks: () -> Unit,
    dates: Boolean, onDates: () -> Unit, wrap: Boolean) {
    @Composable fun Choices() {
        listOf("All", "Lucid", "Non-lucid").forEach { choice ->
            LibraryFilter(choice, filter == choice, "filter-$choice", if (choice == "Lucid") DreamIcons.Moon else null) { onFilter(choice) }
        }
        LibraryFilter("Bookmarks", bookmarks, "library-bookmarks", DreamIcons.Bookmark, onBookmarks)
        LibraryFilter("Dates", dates, "library-dates", onClick = onDates)
    }
    if (wrap) FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) { Choices() }
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { Choices() }
}

@Composable
private fun LibraryFilter(label: String, selected: Boolean, tag: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(11.dp))
        .toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() }).testTag(tag)
        .background(if (selected) ControlPaper else Color.Transparent)
        .then(if (selected) Modifier.border(.7.dp, Hairline, RoundedCornerShape(11.dp)) else Modifier)
        .padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = if (selected) Accent else Pencil)
        Text(label, color = if (selected) Ink else Pencil, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
internal fun ColumnScope.LibraryEntries(results: List<DreamEntry>, currentId: String?, filtered: Boolean,
    compact: Boolean = false, onClear: () -> Unit, onOpen: (DreamEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${results.size} ${if (results.size == 1) "entry" else "entries"}", Modifier.weight(1f), color = Pencil, fontSize = 12.sp)
        if (filtered) TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
            Text("Clear filters", fontSize = 12.sp)
        } else Text("Newest first", color = Pencil, fontSize = 11.sp)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("dream-index"), contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (results.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(DreamIcons.Book, null, Modifier.size(32.dp), tint = Pencil.copy(alpha = .6f))
                Text("No matching dreams", Modifier.padding(top = 18.dp), fontFamily = DiarySerif, fontSize = 23.sp, color = Ink)
                Text("Try another word or clear the filters.", Modifier.padding(top = 8.dp), color = Pencil, fontSize = 13.sp)
            }
        }
        results.groupBy { it.date.withDayOfMonth(1) }.forEach { (month, dreams) ->
            item(key = "month-$month") {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.padding(top = 8.dp, bottom = 2.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            }
            items(dreams, key = { it.id }) { dream -> LibraryEntry(dream, dream.id == currentId, compact) { onOpen(dream) } }
        }
    }
}

@Composable
private fun LibraryEntry(dream: DreamEntry, current: Boolean, compact: Boolean, onOpen: () -> Unit) {
    val night = LocalNotebookPreferences.current.night
    val paper = if (dream.lucid == true) { if (night) Color(0xFF302B38) else Color(0xFFF2EBF7) }
        else if (night) ControlPaper else Color(0xFFFFFCF6)
    val lucidInk = LucidLibraryInk
    val blank = dream.title.isBlank() && dream.body.isBlank() && dream.audio.isEmpty() && !dream.hasDrawing
    Surface(Modifier.fillMaxWidth().testTag("library-entry-${dream.id}"), color = paper, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(.7.dp, if (current) Accent.copy(alpha = .28f) else Hairline)) {
        Row(Modifier.clickable(role = Role.Button, onClickLabel = "Open dream", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dream.date.dayOfMonth.toString().padStart(2, '0'), fontFamily = DiarySerif, fontSize = 27.sp, lineHeight = 33.sp, color = Ink)
                Text(dream.date.format(DateTimeFormatter.ofPattern("EEE")).uppercase(Locale.getDefault()), Modifier.padding(top = 2.dp),
                    fontSize = 9.sp, letterSpacing = 1.sp, color = Pencil)
            }
            Box(Modifier.width(.7.dp).fillMaxHeight().background(if (dream.lucid == true) lucidInk.copy(alpha = .18f) else Hairline))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dream.title.ifBlank { if (blank) "A blank page" else "Untitled dream" }, Modifier.weight(1f),
                        fontFamily = DiarySerif, fontSize = if (compact) 20.sp else 21.sp, lineHeight = if (compact) 26.sp else 27.sp, letterSpacing = (-.35).sp,
                        maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, color = Ink)
                    if (dream.bookmarked) Icon(DreamIcons.Bookmark, "Bookmarked", Modifier.padding(top = 3.dp).size(15.dp), tint = lucidInk)
                }
                if (dream.body.isNotBlank() || blank) Text(if (blank) "Tap to begin writing." else dream.body.replace('\u000C', '\n').replace(Regex("\\s+"), " ").trim(),
                    Modifier.padding(top = if (compact) 6.dp else 8.dp), fontSize = 13.sp, lineHeight = 20.sp, color = Pencil,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (dream.lucid == true || dream.audio.isNotEmpty() || dream.hasDrawing || current) {
                    Row(Modifier.padding(top = if (compact) 8.dp else 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (dream.lucid == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(5.dp).background(lucidInk, CircleShape))
                            Text("Lucid", color = lucidInk, fontSize = 11.sp)
                        }
                        if (dream.audio.isNotEmpty()) Icon(DreamIcons.Microphone, "${dream.audio.size} voice notes", Modifier.size(14.dp), tint = Pencil)
                        if (dream.hasDrawing) Icon(DreamIcons.Pen, "Drawing", Modifier.size(14.dp), tint = Pencil)
                        Spacer(Modifier.weight(1f))
                        if (current) Text("Last opened", color = Pencil, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
