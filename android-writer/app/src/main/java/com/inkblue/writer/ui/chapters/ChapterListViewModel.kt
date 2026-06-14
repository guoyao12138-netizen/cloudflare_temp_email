package com.inkblue.writer.ui.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.Chapter
import com.inkblue.writer.data.NovelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChapterListViewModel(
    private val repo: NovelRepository,
    private val bookId: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<Chapter>> = repo.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addChapter(title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createChapter(bookId, title)) }
    }

    fun renameChapter(chapter: Chapter, title: String) {
        viewModelScope.launch { repo.renameChapter(chapter, title) }
    }

    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch { repo.deleteChapter(chapter) }
    }

    fun updateBook(book: Book, title: String, description: String) {
        viewModelScope.launch { repo.updateBook(book, title, description) }
    }

    fun deleteBook(book: Book, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteBook(book)
            onDone()
        }
    }

    companion object {
        fun factory(bookId: Long) = viewModelFactory {
            initializer {
                ChapterListViewModel((this[APPLICATION_KEY] as InkApp).repository, bookId)
            }
        }
    }
}
