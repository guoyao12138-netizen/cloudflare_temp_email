package com.vertexchat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vertexchat.data.model.ProviderConfig
import com.vertexchat.data.model.ProviderPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.providerDs by preferencesDataStore("aihub_providers")

class ProviderStore(context: Context) {
    private val ds = context.applicationContext.providerDs
    private val gson = Gson()
    private val KEY = stringPreferencesKey("providers")

    val providers: Flow<List<ProviderConfig>> = ds.data.map { prefs ->
        val json = prefs[KEY] ?: return@map ProviderPresets.all
        try {
            val type = object : TypeToken<List<ProviderConfig>>() {}.type
            (gson.fromJson<List<ProviderConfig>>(json, type) ?: ProviderPresets.all)
                .ifEmpty { ProviderPresets.all }
        } catch (_: Exception) {
            ProviderPresets.all
        }
    }

    suspend fun save(providers: List<ProviderConfig>) {
        ds.edit { prefs -> prefs[KEY] = gson.toJson(providers) }
    }

    suspend fun upsert(provider: ProviderConfig, current: List<ProviderConfig>) {
        val updated = if (current.any { it.id == provider.id }) {
            current.map { if (it.id == provider.id) provider else it }
        } else {
            current + provider
        }
        save(updated)
    }

    suspend fun delete(id: String, current: List<ProviderConfig>) {
        save(current.filter { it.id != id })
    }
}
