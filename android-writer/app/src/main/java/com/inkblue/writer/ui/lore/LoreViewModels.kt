package com.inkblue.writer.ui.lore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.LoreCategory
import com.inkblue.writer.data.LoreEntry
import com.inkblue.writer.data.NovelRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoreListViewModel(
    private val repo: NovelRepository,
    private val bookId: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val entries: StateFlow<List<LoreEntry>> = repo.observeLore(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addEntry(category: LoreCategory, name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createLoreEntry(bookId, category, name)) }
    }

    fun deleteEntry(entry: LoreEntry) {
        viewModelScope.launch { repo.deleteLoreEntry(entry) }
    }

    companion object {
        fun factory(bookId: Long) = viewModelFactory {
            initializer {
                LoreListViewModel((this[APPLICATION_KEY] as InkApp).repository, bookId)
            }
        }
    }
}

class LoreEditorViewModel(
    private val repo: NovelRepository,
    private val entryId: Long,
) : ViewModel() {

    private val _entry = MutableStateFlow<LoreEntry?>(null)

    /** Loaded once; the editor owns the buffer afterwards. */
    val entry: StateFlow<LoreEntry?> = _entry

    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved

    init {
        viewModelScope.launch { _entry.value = repo.getLoreEntry(entryId) }
    }

    fun markDirty() {
        _saved.value = false
    }

    fun save(name: String, content: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repo.saveLoreEntry(entryId, name, content)
            }
            _saved.value = true
        }
    }

    companion object {
        fun factory(entryId: Long) = viewModelFactory {
            initializer {
                LoreEditorViewModel((this[APPLICATION_KEY] as InkApp).repository, entryId)
            }
        }
    }
}
