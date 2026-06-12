package com.inkblue.writer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("ink_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Wire protocol of an AI service. ANTHROPIC speaks the native Claude Messages
 * API; OPENAI speaks any OpenAI-compatible /chat/completions endpoint.
 * Both accept custom base URLs, so relays / API proxies work on either.
 */
enum class AiProvider(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    ANTHROPIC("Anthropic 协议（Claude 官方 / 中转）", "https://api.anthropic.com", "claude-opus-4-8"),
    OPENAI("OpenAI 兼容协议（DeepSeek / Kimi / 中转 / 代理）", "https://api.deepseek.com", "deepseek-chat"),
}

/** One configured AI service endpoint. Several can coexist and collaborate. */
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

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val editorFontSize: Int = 18,
    val autoIndent: Boolean = true,
    val highRefreshRate: Boolean = true,
    val showFps: Boolean = false,
    val aiProfiles: List<AiProfile> = emptyList(),
    val primaryAiId: Long = 0L,
    val reviewerAiId: Long = 0L,
) {
    /** The model that writes: continue/polish/generation drafts. */
    fun primaryProfile(): AiProfile? =
        aiProfiles.firstOrNull { it.id == primaryAiId } ?: aiProfiles.firstOrNull()

    /** Optional second model that reviews generated worldbuilding (multi-AI collaboration). */
    fun reviewerProfile(): AiProfile? =
        aiProfiles.firstOrNull { it.id == reviewerAiId && it.apiKey.isNotBlank() }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val AUTO_INDENT = booleanPreferencesKey("auto_indent")
        val HIGH_REFRESH_RATE = booleanPreferencesKey("high_refresh_rate")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val AI_PROFILES = stringPreferencesKey("ai_profiles")
        val AI_PRIMARY_ID = longPreferencesKey("ai_primary_id")
        val AI_REVIEWER_ID = longPreferencesKey("ai_reviewer_id")

        // Legacy single-service keys (pre-0.5.0), kept for migration.
        val LEGACY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val LEGACY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val LEGACY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        val LEGACY_AI_MODEL = stringPreferencesKey("ai_model")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val profilesJson = p[Keys.AI_PROFILES]
        val profiles = if (profilesJson != null) {
            parseProfiles(profilesJson)
        } else {
            migrateLegacyProfile(
                provider = p[Keys.LEGACY_AI_PROVIDER],
                baseUrl = p[Keys.LEGACY_AI_BASE_URL],
                apiKey = p[Keys.LEGACY_AI_API_KEY],
                model = p[Keys.LEGACY_AI_MODEL],
            )
        }
        AppSettings(
            themeMode = p[Keys.THEME]
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM,
            editorFontSize = p[Keys.FONT_SIZE] ?: 18,
            autoIndent = p[Keys.AUTO_INDENT] ?: true,
            highRefreshRate = p[Keys.HIGH_REFRESH_RATE] ?: true,
            showFps = p[Keys.SHOW_FPS] ?: false,
            aiProfiles = profiles,
            primaryAiId = p[Keys.AI_PRIMARY_ID] ?: profiles.firstOrNull()?.id ?: 0L,
            reviewerAiId = p[Keys.AI_REVIEWER_ID] ?: 0L,
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

    suspend fun setHighRefreshRate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIGH_REFRESH_RATE] = enabled }
    }

    suspend fun setShowFps(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FPS] = enabled }
    }

    /** Insert or replace a profile by id. The first profile becomes the primary. */
    suspend fun saveAiProfile(profile: AiProfile) {
        context.dataStore.edit { p ->
            val current = currentProfiles(p[Keys.AI_PROFILES], p)
            val updated = if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) profile else it }
            } else {
                current + profile
            }
            p[Keys.AI_PROFILES] = profilesToJson(updated)
            if (updated.size == 1 || updated.none { it.id == (p[Keys.AI_PRIMARY_ID] ?: 0L) }) {
                p[Keys.AI_PRIMARY_ID] = updated.first().id
            }
        }
    }

    suspend fun deleteAiProfile(id: Long) {
        context.dataStore.edit { p ->
            val updated = currentProfiles(p[Keys.AI_PROFILES], p).filterNot { it.id == id }
            p[Keys.AI_PROFILES] = profilesToJson(updated)
            if (p[Keys.AI_PRIMARY_ID] == id) {
                p[Keys.AI_PRIMARY_ID] = updated.firstOrNull()?.id ?: 0L
            }
            if (p[Keys.AI_REVIEWER_ID] == id) {
                p[Keys.AI_REVIEWER_ID] = 0L
            }
        }
    }

    suspend fun setPrimaryAi(id: Long) {
        context.dataStore.edit { it[Keys.AI_PRIMARY_ID] = id }
    }

    /** 0 disables the reviewer (single-model generation). */
    suspend fun setReviewerAi(id: Long) {
        context.dataStore.edit { it[Keys.AI_REVIEWER_ID] = id }
    }

    private fun currentProfiles(
        json: String?,
        p: androidx.datastore.preferences.core.Preferences,
    ): List<AiProfile> =
        if (json != null) {
            parseProfiles(json)
        } else {
            migrateLegacyProfile(
                provider = p[Keys.LEGACY_AI_PROVIDER],
                baseUrl = p[Keys.LEGACY_AI_BASE_URL],
                apiKey = p[Keys.LEGACY_AI_API_KEY],
                model = p[Keys.LEGACY_AI_MODEL],
            )
        }

    private fun migrateLegacyProfile(
        provider: String?,
        baseUrl: String?,
        apiKey: String?,
        model: String?,
    ): List<AiProfile> {
        if (apiKey.isNullOrBlank()) return emptyList()
        val p = AiProvider.entries.firstOrNull { it.name == provider } ?: AiProvider.ANTHROPIC
        return listOf(
            AiProfile(
                id = 1L,
                name = "默认服务",
                provider = p,
                baseUrl = baseUrl ?: "",
                apiKey = apiKey,
                model = model ?: "",
            )
        )
    }

    private fun parseProfiles(json: String): List<AiProfile> = try {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val provider = AiProvider.entries
                    .firstOrNull { it.name == obj.optString("provider") }
                    ?: AiProvider.ANTHROPIC
                add(
                    AiProfile(
                        id = obj.optLong("id"),
                        name = obj.optString("name"),
                        provider = provider,
                        baseUrl = obj.optString("baseUrl"),
                        apiKey = obj.optString("apiKey"),
                        model = obj.optString("model"),
                    )
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun profilesToJson(profiles: List<AiProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("provider", profile.provider.name)
                    put("baseUrl", profile.baseUrl)
                    put("apiKey", profile.apiKey)
                    put("model", profile.model)
                }
            )
        }
        return array.toString()
    }
}
