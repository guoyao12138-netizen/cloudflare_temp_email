package com.vertexchat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.vertexchat.data.model.GlobalSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.globalDs by preferencesDataStore("aihub_global")

class GlobalSettingsStore(context: Context) {
    private val ds = context.applicationContext.globalDs
    private val gson = Gson()
    private val KEY = stringPreferencesKey("settings")

    val settings: Flow<GlobalSettings> = ds.data.map { prefs ->
        val json = prefs[KEY] ?: return@map GlobalSettings()
        try {
            gson.fromJson(json, GlobalSettings::class.java) ?: GlobalSettings()
        } catch (_: Exception) {
            GlobalSettings()
        }
    }

    suspend fun update(settings: GlobalSettings) {
        ds.edit { prefs -> prefs[KEY] = gson.toJson(settings) }
    }
}
