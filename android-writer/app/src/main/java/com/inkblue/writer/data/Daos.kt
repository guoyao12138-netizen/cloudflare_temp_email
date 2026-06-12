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
