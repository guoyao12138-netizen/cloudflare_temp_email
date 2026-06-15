package com.vertexchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vertexchat.data.AppSettings
import com.vertexchat.data.ChatTurn
import com.vertexchat.data.SettingsStore
import com.vertexchat.data.VertexAiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Author { USER, MODEL }

data class ChatMessage(
    val author: Author,
    val text: String,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val repository: VertexAiRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return

        val updated = _state.value.messages + ChatMessage(Author.USER, trimmed)
        _state.value = _state.value.copy(messages = updated, isSending = true, error = null)

        viewModelScope.launch {
            try {
                val history = updated.map {
                    ChatTurn(
                        role = if (it.author == Author.USER) "user" else "model",
                        text = it.text,
                    )
                }
                val reply = repository.sendMessage(history, settings.value)
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage(Author.MODEL, reply),
                    isSending = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSending = false,
                    error = e.message ?: "Request failed",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearConversation() {
        _state.value = ChatUiState()
    }

    class Factory(
        private val repository: VertexAiRepository,
        private val settingsStore: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, settingsStore) as T
        }
    }
}
