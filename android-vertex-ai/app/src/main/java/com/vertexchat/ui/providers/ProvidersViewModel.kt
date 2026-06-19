package com.vertexchat.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vertexchat.data.ProviderStore
import com.vertexchat.data.model.ProviderConfig
import com.vertexchat.data.model.ProviderPresets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProvidersViewModel(private val store: ProviderStore) : ViewModel() {

    val providers: StateFlow<List<ProviderConfig>> = store.providers
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderPresets.all)

    fun save(provider: ProviderConfig) {
        viewModelScope.launch { store.upsert(provider, providers.value) }
    }

    fun toggleEnabled(provider: ProviderConfig, current: List<ProviderConfig>) {
        viewModelScope.launch { store.upsert(provider.copy(enabled = !provider.enabled), current) }
    }

    fun delete(id: String, current: List<ProviderConfig>) {
        viewModelScope.launch { store.delete(id, current) }
    }

    fun getById(id: String): ProviderConfig? = providers.value.firstOrNull { it.id == id }

    class Factory(private val store: ProviderStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProvidersViewModel(store) as T
    }
}
