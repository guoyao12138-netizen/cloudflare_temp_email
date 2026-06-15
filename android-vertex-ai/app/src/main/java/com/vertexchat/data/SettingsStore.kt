package com.vertexchat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Vertex AI target + generation settings the user can edit on the Settings screen. */
data class AppSettings(
    val projectId: String = "",
    val location: String = "us-central1",
    val model: String = "gemini-2.0-flash-001",
    val systemInstruction: String = "",
    val temperature: Float = 0.7f,
) {
    val isComplete: Boolean get() = projectId.isNotBlank() && location.isNotBlank() && model.isNotBlank()
}

private val Context.dataStore by preferencesDataStore(name = "vertexchat_settings")

class SettingsStore(context: Context) {
    private val ds = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = ds.data.map { prefs ->
        AppSettings(
            projectId = prefs[KEY_PROJECT] ?: "",
            location = prefs[KEY_LOCATION] ?: "us-central1",
            model = prefs[KEY_MODEL] ?: "gemini-2.0-flash-001",
            systemInstruction = prefs[KEY_SYSTEM] ?: "",
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f,
        )
    }

    suspend fun update(settings: AppSettings) {
        ds.edit { prefs ->
            prefs[KEY_PROJECT] = settings.projectId.trim()
            prefs[KEY_LOCATION] = settings.location.trim()
            prefs[KEY_MODEL] = settings.model.trim()
            prefs[KEY_SYSTEM] = settings.systemInstruction
            prefs[KEY_TEMPERATURE] = settings.temperature
        }
    }

    private companion object {
        val KEY_PROJECT = stringPreferencesKey("project_id")
        val KEY_LOCATION = stringPreferencesKey("location")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_SYSTEM = stringPreferencesKey("system_instruction")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
    }
}
