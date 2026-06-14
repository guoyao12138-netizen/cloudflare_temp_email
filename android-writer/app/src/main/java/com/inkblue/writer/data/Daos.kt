package com.inkblue.writer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT books.*, COUNT(chapters.id) AS chapterCount,
               IFNULL(SUM(chapters.wordCount), 0) AS totalWords
        FROM books
        LEFT JOIN chapters ON chapters.bookId = books.id
        GROUP BY books.id
        ORDER BY books.updatedAt DESC
        """
    )
    fun observeBooksWithStats(): Flow<List<BookWithStats>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("UPDATE books SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    fun observeChapters(bookId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapter(id: Long): Chapter?

    @Query("SELECT IFNULL(MAX(sortOrder), 0) FROM chapters WHERE bookId = :bookId")
    suspend fun maxSortOrder(bookId: Long): Int

    @Insert
    suspend fun insert(chapter: Chapter): Long

    @Update
    suspend fun update(chapter: Chapter)

    @Delete
    suspend fun delete(chapter: Chapter)
}

@Dao
interface LoreDao {
    @Query("SELECT * FROM lore_entries WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeEntries(bookId: Long): Flow<List<LoreEntry>>

    @Query("SELECT * FROM lore_entries WHERE id = :id")
    suspend fun getEntry(id: Long): LoreEntry?

    @Query("SELECT * FROM lore_entries WHERE bookId = :bookId ORDER BY updatedAt DESC")
    suspend fun listEntries(bookId: Long): List<LoreEntry>

    @Insert
    suspend fun insert(entry: LoreEntry): Long

    @Update
    suspend fun update(entry: LoreEntry)

    @Delete
    suspend fun delete(entry: LoreEntry)
}

@Dao
interface OutlineDao {
    @Query("SELECT * FROM outline_nodes WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    fun observeNodes(bookId: Long): Flow<List<OutlineNode>>

    @Query("SELECT * FROM outline_nodes WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    suspend fun listNodes(bookId: Long): List<OutlineNode>

    @Query("SELECT IFNULL(MAX(sortOrder), 0) FROM outline_nodes WHERE bookId = :bookId")
    suspend fun maxSortOrder(bookId: Long): Int

    @Insert
    suspend fun insert(node: OutlineNode): Long

    @Update
    suspend fun update(node: OutlineNode)

    @Delete
    suspend fun delete(node: OutlineNode)
}

@Dao
interface StyleDao {
    @Query("SELECT * FROM style_profiles ORDER BY createdAt DESC")
    fun observeStyles(): Flow<List<StyleProfile>>

    @Query("SELECT * FROM style_profiles WHERE id = :id")
    suspend fun getStyle(id: Long): StyleProfile?

    @Insert
    suspend fun insert(style: StyleProfile): Long

    @Update
    suspend fun update(style: StyleProfile)

    @Delete
    suspend fun delete(style: StyleProfile)
}
