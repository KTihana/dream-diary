package com.afterglow.diary

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One writer in DiaryViewModel; AtomicFile keeps the previous file on interrupted writes. */
class JournalStore(directory: File) {
    private val file = AtomicFile(File(directory, "dreams.json"))

    suspend fun load(): List<DreamEntry> = withContext(Dispatchers.IO) {
        val bytes = try {
            file.openRead().use { it.readBytes() }
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists()) throw missing
            return@withContext emptyList()
        }
        JournalCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    suspend fun save(entries: List<DreamEntry>) = withContext(Dispatchers.IO) {
        val bytes = JournalCodec.encode(entries).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
