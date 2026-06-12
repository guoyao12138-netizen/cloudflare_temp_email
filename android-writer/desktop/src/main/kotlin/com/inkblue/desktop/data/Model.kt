package com.inkblue.desktop.data

import kotlinx.serialization.Serializable

enum class LoreCategory(val label: String) {
    CHARACTER("人物"),
    LOCATION("地点"),
    ITEM("物品"),
    FACTION("势力"),
    OTHER("设定"),
}

enum class AiProvider(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    ANTHROPIC("Anthropic 协议（Claude 官方 / 中转）", "https://api.anthropic.com", "claude-opus-4-8"),
    OPENAI("OpenAI 兼容协议（DeepSeek / Kimi / 中转 / 代理）", "https://api.deepseek.com", "deepseek-chat"),
}

@Serializable
data class AiProfile(
    val id: Long,
    val name: String,
    val provider: AiProvider,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    fun effectiveBaseUrl(): String = baseUrl.ifBlank { provider.defaultBaseUrl }
    fun effectiveModel(): String = model.ifBlank { provider.defaultModel }
}

@Serializable
data class Book(
    val id: Long,
    val title: String,
    val description: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Chapter(
    val id: Long,
    val bookId: Long,
    val title: String,
    val content: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class LoreEntry(
    val id: Long,
    val bookId: Long,
    val category: LoreCategory = LoreCategory.OTHER,
    val name: String,
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class OutlineNode(
    val id: Long,
    val bookId: Long,
    val title: String,
    val content: String = "",
    val sortOrder: Int = 0,
)

@Serializable
data class StyleProfile(
    val id: Long,
    val name: String,
    val analysis: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DesktopSettings(
    val dark: Boolean = false,
    val editorFontSize: Int = 17,
    val profiles: List<AiProfile> = emptyList(),
    val primaryAiId: Long = 0L,
    val reviewerAiId: Long = 0L,
) {
    fun primaryProfile(): AiProfile? =
        profiles.firstOrNull { it.id == primaryAiId } ?: profiles.firstOrNull()

    fun reviewerProfile(): AiProfile? =
        profiles.firstOrNull { it.id == reviewerAiId && it.apiKey.isNotBlank() }
}

@Serializable
data class AppData(
    val books: List<Book> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val lore: List<LoreEntry> = emptyList(),
    val outline: List<OutlineNode> = emptyList(),
    val styles: List<StyleProfile> = emptyList(),
    val settings: DesktopSettings = DesktopSettings(),
)

fun countWords(text: String): Int = text.count { !it.isWhitespace() }

fun formatWordCount(count: Int): String =
    if (count >= 10000) String.format("%.1f 万字", count / 10000.0) else "$count 字"
