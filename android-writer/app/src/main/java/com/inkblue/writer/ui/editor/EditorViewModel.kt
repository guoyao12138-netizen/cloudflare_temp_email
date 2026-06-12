package com.inkblue.writer.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.Chapter
import com.inkblue.writer.data.NovelRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SaveState { SAVED, EDITING }

class EditorViewModel(
    private val repo: NovelRepository,
    private val chapterId: Long,
) : ViewModel() {

    private val _chapter = MutableStateFlow<Chapter?>(null)

    /** Loaded once; the editor owns the buffer afterwards. */
    val chapter: StateFlow<Chapter?> = _chapter

    private val _saveState = MutableStateFlow(SaveState.SAVED)
    val saveState: StateFlow<SaveState> = _saveState

    init {
        viewModelScope.launch { _chapter.value = repo.getChapter(chapterId) }
    }

    fun markDirty() {
        _saveState.value = SaveState.EDITING
    }

    fun save(title: String, content: String) {
        viewModelScope.launch {
            // The final save fires from onDispose while navigating away; don't
            // let the ViewModel being cleared cancel the database write.
            withContext(NonCancellable) {
                repo.saveChapter(chapterId, title, content)
            }
            _saveState.value = SaveState.SAVED
        }
    }

    companion object {
        fun factory(chapterId: Long) = viewModelFactory {
            initializer {
                EditorViewModel((this[APPLICATION_KEY] as InkApp).repository, chapterId)
            }
        }
    }
}
