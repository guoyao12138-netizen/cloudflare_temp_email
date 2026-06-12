package com.inkblue.writer.data

import com.inkblue.writer.util.countWords
import kotlinx.coroutines.flow.Flow

class NovelRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
) {
    fun observeBooks(): Flow<List<BookWithStats>> = bookDao.observeBooksWithStats()

    fun observeBook(id: Long): Flow<Book?> = bookDao.observeBook(id)

    fun observeChapters(bookId: Long): Flow<List<Chapter>> = chapterDao.observeChapters(bookId)

    suspend fun getChapter(id: Long): Chapter? = chapterDao.getChapter(id)

    suspend fun createBook(title: String, description: String): Long =
        bookDao.insert(Book(title = title.trim(), description = description.trim()))

    suspend fun updateBook(book: Book, title: String, description: String) =
        bookDao.update(book.copy(title = title.trim(), description = description.trim(), updatedAt = now()))

    suspend fun deleteBook(book: Book) = bookDao.delete(book)

    suspend fun createChapter(bookId: Long, title: String): Long {
        val order = chapterDao.maxSortOrder(bookId) + 1
        val id = chapterDao.insert(Chapter(bookId = bookId, title = title.trim(), sortOrder = order))
        bookDao.touch(bookId, now())
        return id
    }

    suspend fun renameChapter(chapter: Chapter, title: String) =
        chapterDao.update(chapter.copy(title = title.trim(), updatedAt = now()))

    suspend fun deleteChapter(chapter: Chapter) {
        chapterDao.delete(chapter)
        bookDao.touch(chapter.bookId, now())
    }

    /** Persist the editor buffer; skips the write when nothing changed. */
    suspend fun saveChapter(id: Long, title: String, content: String) {
        val chapter = chapterDao.getChapter(id) ?: return
        if (chapter.title == title && chapter.content == content) return
        chapterDao.update(
            chapter.copy(
                title = title,
                content = content,
                wordCount = countWords(content),
                updatedAt = now(),
            )
        )
        bookDao.touch(chapter.bookId, now())
    }

    private fun now() = System.currentTimeMillis()
}
