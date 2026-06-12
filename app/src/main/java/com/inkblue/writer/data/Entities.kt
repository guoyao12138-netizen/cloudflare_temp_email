package com.inkblue.writer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    val content: String = "",
    val wordCount: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class BookWithStats(
    @Embedded val book: Book,
    val chapterCount: Int,
    val totalWords: Int,
)

enum class LoreCategory(val label: String) {
    CHARACTER("人物"),
    LOCATION("地点"),
    ITEM("物品"),
    FACTION("势力"),
    OTHER("设定"),
}

@Entity(
    tableName = "lore_entries",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class LoreEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val category: String = LoreCategory.CHARACTER.name,
    val name: String,
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val loreCategory: LoreCategory
        get() = LoreCategory.entries.firstOrNull { it.name == category } ?: LoreCategory.OTHER
}
