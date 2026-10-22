package com.afterglow.diary

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class JournalCodecTest {
    @Test fun roundTripPreservesUserTextDatesAndEveryLucidityState() {
        val dreams = listOf(
            DreamEntry(id = "one", date = LocalDate.of(2026, 10, 21), title = "Sea of \"stars\" ✧", body = "Prvi san\n\nČudno nebo 🌙", lucid = true, bookmarked = true, symbols = listOf("sea", "stars"), people = listOf("Ana"), places = listOf("home"),
                awareness = 4, control = 2, technique = "Reality check", lucidMinutes = "3.5",
                strokes = listOf(InkStroke(listOf(InkPoint(.1f, .2f), InkPoint(.8f, .9f)))), audio = listOf(AudioClip("note-one.m4a", 12))),
            DreamEntry(id = "two", title = "", body = "", lucid = false),
            DreamEntry(id = "three", title = "Not decided", lucid = null),
        )
        assertEquals(dreams, JournalCodec.decode(JournalCodec.encode(dreams)))
    }

    @Test fun versionOneMigratesWithoutChangingText() {
        val migrated = JournalCodec.decode("""{"version":1,"entries":[{"id":"old","date":"2026-10-21","title":"Čudan san","body":"  line one\nline two  ","lucid":null}]}""")
        assertEquals("  line one\nline two  ", migrated.single().body)
        assertEquals(emptyList<String>(), migrated.single().symbols)
        assertEquals(migrated, JournalCodec.decode(JournalCodec.encode(migrated)))
    }

    @Test fun unsafeAudioPathsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalCodec.decode(JournalCodec.encode(listOf(DreamEntry(audio = listOf(AudioClip("../outside.m4a", 1))))))
        }
    }

    @Test fun emptyNotebookRoundTrips() {
        assertEquals(emptyList<DreamEntry>(), JournalCodec.decode(JournalCodec.encode(emptyList())))
    }

    @Test fun unknownVersionIsRejectedRatherThanDiscardingDreams() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalCodec.decode("""{"version":3,"entries":[]}""")
        }
    }

    @Test fun duplicateIdsAreRejected() {
        val dream = DreamEntry(id = "duplicate")
        assertThrows(IllegalArgumentException::class.java) {
            JournalCodec.decode(JournalCodec.encode(listOf(dream, dream)))
        }
    }

    @Test fun brokenFilesDoNotBecomeEmptyNotebooks() {
        assertThrows(Exception::class.java) { JournalCodec.decode("{broken") }
        assertThrows(Exception::class.java) { JournalCodec.decode("""{"version":1}""") }
    }
}
