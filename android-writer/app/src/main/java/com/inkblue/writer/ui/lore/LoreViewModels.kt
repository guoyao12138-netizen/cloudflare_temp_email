package com.inkblue.writer.ui.lore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.ai.AiClient
import com.inkblue.writer.ai.AiConfig
import com.inkblue.writer.ai.GeneratedLore
import com.inkblue.writer.ai.LoreGenerator
import com.inkblue.writer.ai.LorePrompts
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.LoreCategory
import com.inkblue.writer.data.LoreEntry
import com.inkblue.writer.data.NovelRepository
import com.inkblue.writer.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LoreAiState {
    data object Hidden : LoreAiState
    data class Loading(val generator: LoreGenerator) : LoreAiState
    data class Preview(val generator: LoreGenerator, val entries: List<GeneratedLore>) : LoreAiState
    data class Failure(val generator: LoreGenerator, val message: String) : LoreAiState
}

class LoreListViewModel(
    private val repo: NovelRepository,
    private val settingsRepo: SettingsRepository,
    private val bookId: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val entries: StateFlow<List<LoreEntry>> = repo.observeLore(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiState = MutableStateFlow<LoreAiState>(LoreAiState.Hidden)
    val aiState: StateFlow<LoreAiState> = _aiState

    private var aiJob: Job? = null

    fun addEntry(category: LoreCategory, name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createLoreEntry(bookId, category, name)) }
    }

    fun deleteEntry(entry: LoreEntry) {
        viewModelScope.launch { repo.deleteLoreEntry(entry) }
    }

    fun runGenerator(generator: LoreGenerator, option: String, extra: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = LoreAiState.Loading(generator)
            val settings = settingsRepo.settings.first()
            if (settings.aiApiKey.isBlank()) {
                _aiState.value = LoreAiState.Failure(
                    generator,
                    "尚未配置 AI 服务。请前往「设置 → AI 写作」填写 API Key。",
                )
                return@launch
            }
            val config = AiConfig(
                provider = settings.aiProvider,
                baseUrl = settings.effectiveAiBaseUrl(),
                apiKey = settings.aiApiKey,
                model = settings.effectiveAiModel(),
            )
            val result = runCatching {
                val raw = AiClient.generate(
                    config = config,
                    system = buildSystemPrompt(),
                    userPrompt = LorePrompts.buildUserPrompt(generator, option, extra),
                    maxTokens = 8192,
                )
                LorePrompts.parseGenerated(raw)
            }
            result.fold(
                onSuccess = { _aiState.value = LoreAiState.Preview(generator, it) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _aiState.value = LoreAiState.Failure(generator, e.message ?: "请求失败，请稍后重试")
                },
            )
        }
    }

    fun saveGenerated(generated: List<GeneratedLore>) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                generated.forEach {
                    repo.createLoreEntry(bookId, it.category, it.name, it.content)
                }
            }
            _aiState.value = LoreAiState.Hidden
        }
    }

    fun dismissAi() {
        aiJob?.cancel()
        _aiState.value = LoreAiState.Hidden
    }

    /** Book info + existing lore: new generations must stay consistent with both. */
    private suspend fun buildSystemPrompt(): String {
        val book = repo.getBook(bookId)
        val lore = repo.listLore(bookId)
        return buildString {
            appendLine("你是一位资深的中文网络小说世界观架构师，擅长设计自洽、有冲突张力、可持续展开剧情的设定。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("以下是作品已有的世界观设定，新生成的内容必须与它们保持一致并相互呼应：")
                lore.take(30).forEach {
                    val detail = it.content.take(200).replace('\n', ' ')
                    appendLine("- [${it.loreCategory.label}] ${it.name}：$detail")
                }
            } else {
                appendLine("该作品目前还没有任何世界观设定，你生成的内容将成为它的基础。")
            }
        }.trim()
    }

    companion object {
        fun factory(bookId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InkApp
                LoreListViewModel(app.repository, app.settings, bookId)
            }
        }
    }
}

class LoreEditorViewModel(
    private val repo: NovelRepository,
    private val entryId: Long,
) : ViewModel() {

    private val _entry = MutableStateFlow<LoreEntry?>(null)

    /** Loaded once; the editor owns the buffer afterwards. */
    val entry: StateFlow<LoreEntry?> = _entry

    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved

    init {
        viewModelScope.launch { _entry.value = repo.getLoreEntry(entryId) }
    }

    fun markDirty() {
        _saved.value = false
    }

    fun save(name: String, content: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repo.saveLoreEntry(entryId, name, content)
            }
            _saved.value = true
        }
    }

    companion object {
        fun factory(entryId: Long) = viewModelFactory {
            initializer {
                LoreEditorViewModel((this[APPLICATION_KEY] as InkApp).repository, entryId)
            }
        }
    }
}
