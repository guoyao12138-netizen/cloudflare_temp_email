package com.vertexchat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vertexchat.data.GlobalSettingsStore
import com.vertexchat.data.model.GlobalSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: GlobalSettingsStore) : ViewModel() {

    val settings: StateFlow<GlobalSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())

    fun save(settings: GlobalSettings) {
        viewModelScope.launch { store.update(settings) }
    }

    class Factory(private val store: GlobalSettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(store) as T
    }
}
