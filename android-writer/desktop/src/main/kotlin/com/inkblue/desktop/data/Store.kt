package com.inkblue.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Single-file JSON persistence under ~/.inkblue-writer/. */
object Store {
    private val file = File(System.getProperty("user.home"), ".inkblue-writer/data.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val idSeed = AtomicLong(System.currentTimeMillis())

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    fun newId(): Long = idSeed.incrementAndGet()

    private fun load(): AppData = runCatching {
        json.decodeFromString<AppData>(file.readText())
    }.getOrDefault(AppData())

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(next))
        }
    }

    // ---- mutation helpers ----

    fun addBook(title: String, description: String): Long {
        val id = newId()
        update { it.copy(books = it.books + Book(id, title.trim(), description.trim())) }
        return id
    }

    fun updateBook(bookId: Long, title: String, description: String) = update { d ->
        d.copy(books = d.books.map {
            if (it.id == bookId) it.copy(title = title.trim(), description = description.trim(), updatedAt = now()) else it
        })
    }

    fun deleteBook(bookId: Long) = update { d ->
        d.copy(
            books = d.books.filterNot { it.id == bookId },
            chapters = d.chapters.filterNot { it.bookId == bookId },
            lore = d.lore.filterNot { it.bookId == bookId },
            outline = d.outline.filterNot { it.bookId == bookId },
        )
    }

    fun addChapter(bookId: Long, title: String): Long {
        val id = newId()
        update { d ->
            val order = (d.chapters.filter { it.bookId == bookId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
            d.copy(chapters = d.chapters + Chapter(id, bookId, title.trim(), sortOrder = order))
        }
        return id
    }

    fun saveChapter(chapterId: Long, title: String, content: String) = update { d ->
        d.copy(chapters = d.chapters.map {
            if (it.id == chapterId && (it.title != title || it.content != content)) {
                it.copy(title = title, content = content, updatedAt = now())
            } else it
        })
    }

    fun deleteChapter(chapterId: Long) = update { d ->
        d.copy(chapters = d.chapters.filterNot { it.id == chapterId })
    }

    fun addLore(bookId: Long, category: LoreCategory, name: String, content: String) = update { d ->
        d.copy(lore = d.lore + LoreEntry(newId(), bookId, category, name.trim(), content))
    }

    fun saveLore(loreId: Long, name: String, content: String) = update { d ->
        d.copy(lore = d.lore.map {
            if (it.id == loreId) it.copy(name = name.trim(), content = content, updatedAt = now()) else it
        })
    }

    fun deleteLore(loreId: Long) = update { d ->
        d.copy(lore = d.lore.filterNot { it.id == loreId })
    }

    fun addOutline(bookId: Long, title: String, content: String) = update { d ->
        val order = (d.outline.filter { it.bookId == bookId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
        d.copy(outline = d.outline + OutlineNode(newId(), bookId, title.trim(), content, order))
    }

    fun appendOutline(bookId: Long, nodes: List<Pair<String, String>>) = update { d ->
        var order = d.outline.filter { it.bookId == bookId }.maxOfOrNull { it.sortOrder } ?: 0
        d.copy(outline = d.outline + nodes.map { (t, c) -> order += 1; OutlineNode(newId(), bookId, t.trim(), c, order) })
    }

    fun saveOutline(nodeId: Long, title: String, content: String) = update { d ->
        d.copy(outline = d.outline.map {
            if (it.id == nodeId) it.copy(title = title.trim(), content = content) else it
        })
    }

    fun deleteOutline(nodeId: Long) = update { d ->
        d.copy(outline = d.outline.filterNot { it.id == nodeId })
    }

    fun swapOutline(a: OutlineNode, b: OutlineNode) = update { d ->
        d.copy(outline = d.outline.map {
            when (it.id) {
                a.id -> it.copy(sortOrder = b.sortOrder)
                b.id -> it.copy(sortOrder = a.sortOrder)
                else -> it
            }
        })
    }

    fun addStyle(name: String, analysis: String) = update { d ->
        d.copy(styles = d.styles + StyleProfile(newId(), name.trim(), analysis))
    }

    fun renameStyle(styleId: Long, name: String) = update { d ->
        d.copy(styles = d.styles.map { if (it.id == styleId) it.copy(name = name.trim()) else it })
    }

    fun deleteStyle(styleId: Long) = update { d ->
        d.copy(styles = d.styles.filterNot { it.id == styleId })
    }

    fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) = update { d ->
        d.copy(settings = transform(d.settings))
    }

    fun saveProfile(profile: AiProfile) = updateSettings { s ->
        val updated = if (s.profiles.any { it.id == profile.id }) {
            s.profiles.map { if (it.id == profile.id) profile else it }
        } else {
            s.profiles + profile
        }
        val primary = if (updated.size == 1 || updated.none { it.id == s.primaryAiId }) updated.first().id else s.primaryAiId
        s.copy(profiles = updated, primaryAiId = primary)
    }

    fun deleteProfile(id: Long) = updateSettings { s ->
        val updated = s.profiles.filterNot { it.id == id }
        s.copy(
            profiles = updated,
            primaryAiId = if (s.primaryAiId == id) updated.firstOrNull()?.id ?: 0L else s.primaryAiId,
            reviewerAiId = if (s.reviewerAiId == id) 0L else s.reviewerAiId,
        )
    }

    private fun now() = System.currentTimeMillis()
}
