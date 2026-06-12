package com.inkblue.writer

import android.app.Application
import com.inkblue.writer.data.InkDatabase
import com.inkblue.writer.data.NovelRepository
import com.inkblue.writer.data.SettingsRepository

class InkApp : Application() {
    val database: InkDatabase by lazy { InkDatabase.build(this) }
    val repository: NovelRepository by lazy {
        NovelRepository(database.bookDao(), database.chapterDao())
    }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
}
