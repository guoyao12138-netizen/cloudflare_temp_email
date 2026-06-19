package com.vertexchat.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vertexchat.data.McpStore
import com.vertexchat.data.model.McpServerConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class McpViewModel(private val store: McpStore) : ViewModel() {

    val servers: StateFlow<List<McpServerConfig>> = store.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(server: McpServerConfig, current: List<McpServerConfig>) {
        viewModelScope.launch { store.upsert(server, current) }
    }

    fun toggle(server: McpServerConfig, current: List<McpServerConfig>) {
        viewModelScope.launch { store.upsert(server.copy(enabled = !server.enabled), current) }
    }

    fun delete(id: String, current: List<McpServerConfig>) {
        viewModelScope.launch { store.delete(id, current) }
    }

    class Factory(private val store: McpStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = McpViewModel(store) as T
    }
}
