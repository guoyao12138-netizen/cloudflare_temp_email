package com.vertexchat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vertexchat.data.model.McpServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mcpDs by preferencesDataStore("aihub_mcp")

class McpStore(context: Context) {
    private val ds = context.applicationContext.mcpDs
    private val gson = Gson()
    private val KEY = stringPreferencesKey("servers")

    val servers: Flow<List<McpServerConfig>> = ds.data.map { prefs ->
        val json = prefs[KEY] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<McpServerConfig>>() {}.type
            gson.fromJson<List<McpServerConfig>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(servers: List<McpServerConfig>) {
        ds.edit { prefs -> prefs[KEY] = gson.toJson(servers) }
    }

    suspend fun upsert(server: McpServerConfig, current: List<McpServerConfig>) {
        val updated = if (current.any { it.id == server.id }) {
            current.map { if (it.id == server.id) server else it }
        } else {
            current + server
        }
        save(updated)
    }

    suspend fun delete(id: String, current: List<McpServerConfig>) {
        save(current.filter { it.id != id })
    }
}
