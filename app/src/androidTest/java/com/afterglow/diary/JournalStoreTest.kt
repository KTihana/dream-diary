package com.afterglow.diary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalStoreTest {
    @Test fun dreamsSurviveReopeningAndTruncatedFilesAreNotOverwritten() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "store-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            assertTrue(JournalStore(directory).load().isEmpty())
            val dreams = listOf(DreamEntry(title = "My own title", body = "The sea was full of stars.\nI could fly.", lucid = true))
            JournalStore(directory).save(dreams)
            assertEquals(dreams, JournalStore(directory).load())
            val updated = dreams + DreamEntry(title = "Another night", lucid = false)
            JournalStore(directory).save(updated)
            assertEquals(updated, JournalStore(directory).load())
            val file = File(directory, "dreams.json")
            file.writeText("{truncated")
            val result = runCatching { JournalStore(directory).load() }
            assertTrue(result.isFailure)
            assertEquals("{truncated", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
