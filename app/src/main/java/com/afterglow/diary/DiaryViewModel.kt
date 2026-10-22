package com.afterglow.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SaveStatus { Saved, Saving, Failed }

data class DiaryState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val entries: List<DreamEntry> = emptyList(),
    val selectedId: String? = null,
    val saveStatus: SaveStatus = SaveStatus.Saved,
    val preferences: NotebookPreferences = NotebookPreferences(),
    val meanings: List<SymbolMeaning> = emptyList(),
) {
    val index: Int get() = entries.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val current: DreamEntry? get() = entries.getOrNull(index)
}

class DiaryViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application) {
    private val store = JournalStore(application.filesDir)
    private val preferencesStore = NotebookPreferencesStore(application)
    val lucid = LucidPracticeController(application)
    private val mutableState = MutableStateFlow(DiaryState())
    val state = mutableState.asStateFlow()
    // Every pending item is a complete snapshot, so conflation never drops an earlier dream's edit.
    private val writes = Channel<List<DreamEntry>>(Channel.CONFLATED)
    private val abandonedAudio = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            for (snapshot in writes) {
                try {
                    store.save(snapshot)
                    val kept = (snapshot + mutableState.value.entries).flatMap { it.audio }.map { it.file }.toSet()
                    abandonedAudio.filterNot { it in kept }.forEach { name ->
                        java.io.File(getApplication<Application>().filesDir, "audio/$name").delete()
                        abandonedAudio.remove(name)
                    }
                    if (mutableState.value.entries == snapshot) {
                        mutableState.value = mutableState.value.copy(saveStatus = SaveStatus.Saved)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(saveStatus = SaveStatus.Failed)
                }
            }
        }
        load()
    }

    fun load() {
        mutableState.value = mutableState.value.copy(loading = true, loadFailed = false)
        viewModelScope.launch {
            try {
                val loaded = store.load()
                val entries = loaded.ifEmpty { listOf(DreamEntry()) }
                val selected = listOfNotNull(savedState.get<String>("selectedDream"), preferencesStore.selectedDreamId())
                    .firstOrNull { id -> entries.any { it.id == id } } ?: entries.first().id
                rememberDream(selected)
                mutableState.value = DiaryState(loading = false, entries = entries, selectedId = selected,
                    preferences = preferencesStore.load(), meanings = preferencesStore.meanings())
                RecallReminder.schedule(getApplication(), mutableState.value.preferences)
                if (loaded.isEmpty()) enqueueSave()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = DiaryState(loading = false, loadFailed = true)
            }
        }
    }

    fun select(index: Int) {
        val entry = mutableState.value.entries.getOrNull(index) ?: return
        rememberDream(entry.id)
        mutableState.value = mutableState.value.copy(selectedId = entry.id)
    }

    fun editTitle(value: String) = update { it.copy(title = value.take(140).replace('\n', ' ')) }
    fun editBody(value: String) = update { it.copy(body = value) }
    fun setLucid(value: Boolean) = update { it.copy(lucid = value) }
    fun setDate(value: LocalDate) = update { it.copy(date = value) }
    fun toggleBookmark() = update { it.copy(bookmarked = !it.bookmarked) }
    fun setDetails(symbols: String, people: String, places: String, awareness: Int, control: Int, technique: String, minutes: String) = update {
        it.copy(symbols = parseLabels(symbols), people = parseLabels(people), places = parseLabels(places),
            awareness = awareness.coerceIn(0, 5), control = control.coerceIn(0, 5), technique = technique.take(140), lucidMinutes = minutes.take(12))
    }
    fun setSketch(strokes: List<InkStroke>) = update { it.copy(strokes = strokes) }
    fun addAudio(dreamId: String, clip: AudioClip) = updateById(dreamId) { it.copy(audio = it.audio + clip) }
    fun removeAudio(clip: AudioClip) {
        abandonedAudio += clip.file
        update { it.copy(audio = it.audio - clip) }
    }
    fun appendTranscript(dreamId: String, text: String) = updateById(dreamId) {
        it.copy(body = it.body + (if (it.body.isBlank()) "" else "\n\n") + text)
    }
    fun addWritingPage() = update { it.copy(body = it.body + "\u000C") }
    fun preferences(value: NotebookPreferences) {
        preferencesStore.save(value)
        mutableState.value = mutableState.value.copy(preferences = value)
        RecallReminder.schedule(getApplication(), value)
    }
    fun saveMeaning(name: String, meaning: String, previousName: String? = null) {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        val meanings = (mutableState.value.meanings.filterNot { it.name.equals(clean, true) || it.name.equals(previousName, true) } +
            SymbolMeaning(clean, meaning.take(4000))).sortedBy { it.name.lowercase() }
        preferencesStore.saveMeanings(meanings)
        val entries = mutableState.value.entries.map { dream ->
            if (previousName == null || previousName == clean) dream else dream.copy(symbols = dream.symbols.map {
                if (it.equals(previousName, true)) clean else it
            }.distinctBy { it.lowercase() })
        }
        val changed = entries != mutableState.value.entries
        mutableState.value = mutableState.value.copy(meanings = meanings, entries = entries)
        if (changed) enqueueSave()
    }
    fun deleteMeaning(name: String) {
        val meanings = mutableState.value.meanings.filterNot { it.name == name }
        preferencesStore.saveMeanings(meanings)
        mutableState.value = mutableState.value.copy(meanings = meanings)
    }

    fun addDream() {
        val current = mutableState.value
        if (current.loading || current.loadFailed) return
        val entry = DreamEntry()
        rememberDream(entry.id)
        mutableState.value = current.copy(entries = current.entries + entry, selectedId = entry.id)
        enqueueSave()
    }

    fun deleteCurrent() {
        val current = mutableState.value
        val selected = current.current ?: return
        abandonedAudio.addAll(selected.audio.map { it.file })
        val remaining = current.entries.filterNot { it.id == selected.id }.ifEmpty { listOf(DreamEntry()) }
        val nextId = remaining[current.index.coerceAtMost(remaining.lastIndex)].id
        rememberDream(nextId)
        mutableState.value = current.copy(entries = remaining, selectedId = nextId)
        enqueueSave()
    }

    fun retrySave() = enqueueSave()

    override fun onCleared() {
        lucid.close()
        super.onCleared()
    }

    private fun rememberDream(id: String) {
        savedState["selectedDream"] = id
        preferencesStore.selectDream(id)
    }

    private fun update(edit: (DreamEntry) -> DreamEntry) {
        val current = mutableState.value
        val entry = current.current ?: return
        updateById(entry.id, edit)
    }
    private fun updateById(id: String, edit: (DreamEntry) -> DreamEntry) {
        val current = mutableState.value
        val entry = current.entries.firstOrNull { it.id == id } ?: return
        val updated = edit(entry)
        if (entry == updated) return
        mutableState.value = current.copy(entries = current.entries.map { if (it.id == id) updated else it })
        enqueueSave()
    }

    private fun enqueueSave() {
        val current = mutableState.value
        if (current.loading || current.loadFailed) return
        mutableState.value = current.copy(saveStatus = SaveStatus.Saving)
        writes.trySend(current.entries)
    }
}
