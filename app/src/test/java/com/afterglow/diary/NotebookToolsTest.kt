package com.afterglow.diary

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NotebookToolsTest {
    @Test fun paginationKeepsEveryCharacterIncludingWhitespaceAndEmoji() {
        val text = "  one\n\n two 🌙 three\n"
        val pages = paginatePaper(text) { 4 }
        assertEquals(text, pages.joinToString("") { text.substring(it.start, it.end) })
        assertFalse(pages.any { it.end < text.length && it.end > 0 && text[it.end - 1].isHighSurrogate() && text[it.end].isLowSurrogate() })
    }
    @Test fun deliberatePageBreaksAndEmptyPagesSurvive() {
        val text = "first\u000C\u000Cthird\u000C"
        val pages = paginatePaper(text) { it.length }
        assertEquals(listOf("first", "", "third", ""), pages.map { text.substring(it.start, it.end) })
        assertEquals(listOf(PaperSlice(0, 0)), paginatePaper("") { 0 })
    }
    @Test fun replacingAContinuationPageKeepsOtherPages() {
        val text = "first\u000Csecond\u000Clast"
        val page = paginatePaper(text) { it.length }[1]
        assertEquals("first\u000Cedited\u000Clast", text.substring(0, page.start) + "edited" + text.substring(page.end))
    }
    @Test fun phraseSearchAndFiltersUseInclusiveDatesAndMetadata() {
        val date = LocalDate.of(2026, 10, 21)
        val match = DreamEntry(id = "yes", date = date, body = "The BLUE door opened", symbols = listOf("water"), lucid = true, bookmarked = true)
        val other = DreamEntry(id = "no", date = date.minusDays(1), body = "blue and door", lucid = false)
        assertEquals(listOf(match), searchDreams(listOf(other, match), "\"blue door\" water", true, true, date, date))
        assertEquals(listOf(other), searchDreams(listOf(other, match), "door", false))
        assertEquals(emptyList<DreamEntry>(), searchDreams(listOf(match), "missing"))
    }
    @Test fun recurringPatternsCountDreamsRatherThanDuplicateLabels() {
        val entries = listOf(DreamEntry(id = "a", symbols = listOf("Sea", "sea", "Door")), DreamEntry(id = "b", symbols = listOf("SEA")))
        val result = recurringPatterns(entries) { it.symbols }
        assertEquals(1, result.size)
        assertEquals(2, result.single().count)
        assertEquals(listOf("a", "b"), result.single().dreamIds)
        assertEquals(listOf("Sea", "door"), parseLabels(" Sea, sea, door, "))
    }
}
