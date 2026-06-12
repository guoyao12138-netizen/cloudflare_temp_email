package com.inkblue.writer.data

import com.inkblue.writer.util.countWords
import kotlinx.coroutines.flow.Flow

class NovelRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val loreDao: LoreDao,
    private val outlineDao: OutlineDao,
    private val styleDao: StyleDao,
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

    suspend fun getBook(id: Long): Book? = bookDao.getBook(id)

    fun observeLore(bookId: Long): Flow<List<LoreEntry>> = loreDao.observeEntries(bookId)

    suspend fun listLore(bookId: Long): List<LoreEntry> = loreDao.listEntries(bookId)

    suspend fun getLoreEntry(id: Long): LoreEntry? = loreDao.getEntry(id)

    suspend fun createLoreEntry(
        bookId: Long,
        category: LoreCategory,
        name: String,
        content: String = "",
    ): Long {
        val id = loreDao.insert(
            LoreEntry(bookId = bookId, category = category.name, name = name.trim(), content = content)
        )
        bookDao.touch(bookId, now())
        return id
    }

    /** Persist the lore editor buffer; skips the write when nothing changed. */
    suspend fun saveLoreEntry(id: Long, name: String, content: String) {
        val entry = loreDao.getEntry(id) ?: return
        if (entry.name == name && entry.content == content) return
        loreDao.update(entry.copy(name = name, content = content, updatedAt = now()))
        bookDao.touch(entry.bookId, now())
    }

    suspend fun deleteLoreEntry(entry: LoreEntry) {
        loreDao.delete(entry)
        bookDao.touch(entry.bookId, now())
    }

    fun observeOutline(bookId: Long): Flow<List<OutlineNode>> = outlineDao.observeNodes(bookId)

    suspend fun listOutline(bookId: Long): List<OutlineNode> = outlineDao.listNodes(bookId)

    suspend fun createOutlineNode(bookId: Long, title: String, content: String): Long {
        val order = outlineDao.maxSortOrder(bookId) + 1
        return outlineDao.insert(
            OutlineNode(bookId = bookId, title = title.trim(), content = content, sortOrder = order)
        )
    }

    suspend fun appendOutlineNodes(bookId: Long, nodes: List<Pair<String, String>>) {
        var order = outlineDao.maxSortOrder(bookId)
        nodes.forEach { (title, content) ->
            order += 1
            outlineDao.insert(
                OutlineNode(bookId = bookId, title = title.trim(), content = content, sortOrder = order)
            )
        }
    }

    suspend fun updateOutlineNode(node: OutlineNode, title: String, content: String) =
        outlineDao.update(node.copy(title = title.trim(), content = content, updatedAt = now()))

    suspend fun deleteOutlineNode(node: OutlineNode) = outlineDao.delete(node)

    /** Swap display order of two adjacent outline nodes. */
    suspend fun swapOutlineOrder(a: OutlineNode, b: OutlineNode) {
        outlineDao.update(a.copy(sortOrder = b.sortOrder, updatedAt = now()))
        outlineDao.update(b.copy(sortOrder = a.sortOrder, updatedAt = now()))
    }

    fun observeStyles(): Flow<List<StyleProfile>> = styleDao.observeStyles()

    suspend fun getStyle(id: Long): StyleProfile? = styleDao.getStyle(id)

    suspend fun createStyle(name: String, analysis: String): Long =
        styleDao.insert(StyleProfile(name = name.trim(), analysis = analysis))

    suspend fun renameStyle(style: StyleProfile, name: String) =
        styleDao.update(style.copy(name = name.trim()))

    suspend fun deleteStyle(style: StyleProfile) = styleDao.delete(style)

    private fun now() = System.currentTimeMillis()
}
