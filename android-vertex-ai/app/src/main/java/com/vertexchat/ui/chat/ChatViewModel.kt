package com.vertexchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vertexchat.data.ChatRepository
import com.vertexchat.data.GlobalSettingsStore
import com.vertexchat.data.McpStore
import com.vertexchat.data.ProviderStore
import com.vertexchat.data.UiChatMessage
import com.vertexchat.data.model.GlobalSettings
import com.vertexchat.data.model.McpServerConfig
import com.vertexchat.data.model.ProviderConfig
import com.vertexchat.data.model.ProviderPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<UiChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val providerStore: ProviderStore,
    private val mcpStore: McpStore,
    private val globalStore: GlobalSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val providers: StateFlow<List<ProviderConfig>> = providerStore.providers
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderPresets.all)

    val globalSettings: StateFlow<GlobalSettings> = globalStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())

    val mcpServers: StateFlow<List<McpServerConfig>> = mcpStore.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProvider: StateFlow<ProviderConfig?> = combine(providers, globalSettings) { provs, gs ->
        provs.firstOrNull { it.id == gs.activeProviderId } ?: provs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return

        val provider = activeProvider.value ?: run {
            _state.value = _state.value.copy(error = "No provider configured. Add one in Settings → Providers.")
            return
        }
        val gs = globalSettings.value
        val model = gs.activeModel.ifBlank { provider.activeModel.ifBlank { provider.models.firstOrNull() ?: "" } }

        if (provider.type.name != "VERTEX" && provider.apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "API key missing for ${provider.name}. Edit the provider in Settings.")
            return
        }

        val userMsg = UiChatMessage(role = "user", content = trimmed)
        val streamingMsg = UiChatMessage(role = "assistant", content = "", isStreaming = true)
        val updatedHistory = _state.value.messages + userMsg

        _state.value = _state.value.copy(
            messages = updatedHistory + streamingMsg,
            isSending = true,
            error = null,
        )

        val servers = mcpServers.value
        chatRepository.updateMcpServers(servers)

        viewModelScope.launch {
            runCatching {
                chatRepository.chat(
                    history = updatedHistory,
                    provider = provider,
                    model = model,
                    settings = gs,
                    mcpServers = servers,
                ).collect { accumulated ->
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.dropLast(1) +
                            streamingMsg.copy(content = accumulated, isStreaming = true),
                    )
                }
                // Finalize streaming message
                val finalText = _state.value.messages.lastOrNull()?.content ?: ""
                _state.value = _state.value.copy(
                    messages = _state.value.messages.dropLast(1) +
                        UiChatMessage(role = "assistant", content = finalText),
                    isSending = false,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    messages = _state.value.messages.filterNot { it.isStreaming },
                    isSending = false,
                    error = e.message ?: "Request failed",
                )
            }
        }
    }

    fun switchProvider(providerId: String, model: String) {
        viewModelScope.launch {
            val gs = globalSettings.value.copy(activeProviderId = providerId, activeModel = model)
            globalStore.update(gs)
        }
    }

    fun clearConversation() { _state.value = ChatUiState() }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    class Factory(
        private val chatRepository: ChatRepository,
        private val providerStore: ProviderStore,
        private val mcpStore: McpStore,
        private val globalStore: GlobalSettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(chatRepository, providerStore, mcpStore, globalStore) as T
    }
}
