package com.inkblue.writer.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.ai.AiClient
import com.inkblue.writer.ai.toAiConfig
import com.inkblue.writer.data.Chapter
import com.inkblue.writer.data.NovelRepository
import com.inkblue.writer.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SaveState { SAVED, EDITING }

enum class AiAction(val label: String, val description: String) {
    CONTINUE("续写", "结合世界观设定与上下文，自然续写约 300 字"),
    POLISH("润色", "润色选中的文字，使其更生动流畅"),
    IDEA("情节灵感", "给出 3 个后续情节走向建议"),
}

sealed interface AiUiState {
    data object Hidden : AiUiState
    data class Loading(val action: AiAction) : AiUiState
    data class Success(val action: AiAction, val text: String) : AiUiState
    data class Failure(val action: AiAction, val message: String) : AiUiState
}

class EditorViewModel(
    private val repo: NovelRepository,
    private val settingsRepo: SettingsRepository,
    private val chapterId: Long,
) : ViewModel() {

    private val _chapter = MutableStateFlow<Chapter?>(null)

    /** Loaded once; the editor owns the buffer afterwards. */
    val chapter: StateFlow<Chapter?> = _chapter

    private val _saveState = MutableStateFlow(SaveState.SAVED)
    val saveState: StateFlow<SaveState> = _saveState

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Hidden)
    val aiState: StateFlow<AiUiState> = _aiState

    private var aiJob: Job? = null

    init {
        viewModelScope.launch { _chapter.value = repo.getChapter(chapterId) }
    }

    fun markDirty() {
        _saveState.value = SaveState.EDITING
    }

    fun save(title: String, content: String) {
        viewModelScope.launch {
            // The final save fires from onDispose while navigating away; don't
            // let the ViewModel being cleared cancel the database write.
            withContext(NonCancellable) {
                repo.saveChapter(chapterId, title, content)
            }
            _saveState.value = SaveState.SAVED
        }
    }

    fun runAi(action: AiAction, chapterTitle: String, content: String, selection: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiUiState.Loading(action)
            val settings = settingsRepo.settings.first()
            val profile = settings.primaryProfile()
            if (profile == null || profile.apiKey.isBlank()) {
                _aiState.value = AiUiState.Failure(
                    action,
                    "尚未配置 AI 服务。请前往「设置 → AI 服务」添加并填写 API Key。",
                )
                return@launch
            }
            val result = runCatching {
                AiClient.generate(
                    config = profile.toAiConfig(),
                    system = buildSystemPrompt(),
                    userPrompt = buildUserPrompt(action, chapterTitle, content, selection),
                )
            }
            result.fold(
                onSuccess = { _aiState.value = AiUiState.Success(action, it) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _aiState.value = AiUiState.Failure(action, e.message ?: "请求失败，请稍后重试")
                },
            )
        }
    }

    fun dismissAi() {
        aiJob?.cancel()
        _aiState.value = AiUiState.Hidden
    }

    /** Book info + worldbuilding entries form the stable context for every AI call. */
    private suspend fun buildSystemPrompt(): String {
        val chapter = repo.getChapter(chapterId)
        val book = chapter?.let { repo.getBook(it.bookId) }
        val lore = chapter?.let { repo.listLore(it.bookId) } ?: emptyList()
        return buildString {
            appendLine("你是一位资深的中文网络小说写作助手，文笔自然流畅，擅长贴合作品既有的文风与节奏。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("以下是作品的世界观设定，创作时必须与这些设定保持一致：")
                lore.take(20).forEach {
                    val detail = it.content.take(200).replace('\n', ' ')
                    appendLine("- [${it.loreCategory.label}] ${it.name}：$detail")
                }
            }
        }.trim()
    }

    private fun buildUserPrompt(
        action: AiAction,
        title: String,
        content: String,
        selection: String,
    ): String = when (action) {
        AiAction.CONTINUE -> buildString {
            appendLine("以下是当前章节《$title》的结尾部分：")
            appendLine(content.takeLast(1500).ifBlank { "（本章尚无内容，请直接开篇。）" })
            appendLine()
            append("请自然地续写约 300 字。直接输出续写的正文，不要任何解释、标题或前缀。")
        }

        AiAction.POLISH -> buildString {
            appendLine("请润色以下网文片段，保持原意、人称与情节不变，使文字更生动流畅：")
            appendLine(selection)
            appendLine()
            append("直接输出润色后的文字，不要任何解释或前缀。")
        }

        AiAction.IDEA -> buildString {
            appendLine("以下是当前章节《$title》的结尾部分：")
            appendLine(content.takeLast(1200).ifBlank { "（本章尚无内容。）" })
            appendLine()
            append("请给出 3 个后续情节发展方向的建议，每个用 2-3 句话概括，按 1. 2. 3. 列出。")
        }
    }

    companion object {
        fun factory(chapterId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InkApp
                EditorViewModel(app.repository, app.settings, chapterId)
            }
        }
    }
}
