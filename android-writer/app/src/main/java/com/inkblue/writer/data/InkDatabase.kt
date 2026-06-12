package com.inkblue.writer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Book::class, Chapter::class], version = 1, exportSchema = false)
abstract class InkDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        fun build(context: Context): InkDatabase =
            Room.databaseBuilder(context, InkDatabase::class.java, "inkblue.db").build()
    }
}
