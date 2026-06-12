package com.inkblue.writer.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.BookWithStats
import com.inkblue.writer.data.NovelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel(private val repo: NovelRepository) : ViewModel() {

    val books: StateFlow<List<BookWithStats>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createBook(title: String, description: String) {
        viewModelScope.launch { repo.createBook(title, description) }
    }

    fun updateBook(book: Book, title: String, description: String) {
        viewModelScope.launch { repo.updateBook(book, title, description) }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { repo.deleteBook(book) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BookshelfViewModel((this[APPLICATION_KEY] as InkApp).repository) }
        }
    }
}
