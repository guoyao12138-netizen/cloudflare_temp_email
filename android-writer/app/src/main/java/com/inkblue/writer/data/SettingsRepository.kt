package com.inkblue.writer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("ink_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * AI service protocol. ANTHROPIC talks to the native Claude Messages API;
 * OPENAI talks to any OpenAI-compatible /chat/completions endpoint
 * (DeepSeek, Kimi, OpenRouter, ...).
 */
enum class AiProvider(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    ANTHROPIC("Claude（Anthropic 官方）", "https://api.anthropic.com", "claude-opus-4-8"),
    OPENAI("OpenAI 兼容（DeepSeek / Kimi 等）", "https://api.deepseek.com", "deepseek-chat"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val editorFontSize: Int = 18,
    val autoIndent: Boolean = true,
    val aiProvider: AiProvider = AiProvider.ANTHROPIC,
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
) {
    fun effectiveAiBaseUrl(): String = aiBaseUrl.ifBlank { aiProvider.defaultBaseUrl }
    fun effectiveAiModel(): String = aiModel.ifBlank { aiProvider.defaultModel }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val AUTO_INDENT = booleanPreferencesKey("auto_indent")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM,
            editorFontSize = p[Keys.FONT_SIZE] ?: 18,
            autoIndent = p[Keys.AUTO_INDENT] ?: true,
            aiProvider = p[Keys.AI_PROVIDER]
                ?.let { value -> AiProvider.entries.firstOrNull { it.name == value } }
                ?: AiProvider.ANTHROPIC,
            aiBaseUrl = p[Keys.AI_BASE_URL] ?: "",
            aiApiKey = p[Keys.AI_API_KEY] ?: "",
            aiModel = p[Keys.AI_MODEL] ?: "",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setEditorFontSize(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size }
    }

    suspend fun setAutoIndent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_INDENT] = enabled }
    }

    suspend fun setAiProvider(provider: AiProvider) {
        context.dataStore.edit { it[Keys.AI_PROVIDER] = provider.name }
    }

    suspend fun setAiBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.AI_BASE_URL] = url.trim() }
    }

    suspend fun setAiApiKey(key: String) {
        context.dataStore.edit { it[Keys.AI_API_KEY] = key.trim() }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[Keys.AI_MODEL] = model.trim() }
    }
}
