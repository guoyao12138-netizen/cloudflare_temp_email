package com.inkblue.writer.ui.lore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.ai.AiClient
import com.inkblue.writer.ai.GeneratedLore
import com.inkblue.writer.ai.LoreGenerator
import com.inkblue.writer.ai.LorePrompts
import com.inkblue.writer.ai.toAiConfig
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

private const val NO_AI_MESSAGE = "尚未配置 AI 服务。请前往「设置 → AI 服务」添加并填写 API Key。"

sealed interface LoreAiState {
    data object Hidden : LoreAiState
    data class Loading(val generator: LoreGenerator, val stage: String) : LoreAiState
    data class Preview(
        val generator: LoreGenerator,
        val entries: List<GeneratedLore>,
        val reviewed: Boolean,
    ) : LoreAiState

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

    /**
     * Draft with the primary model; when a reviewer model is configured, run a
     * second collaboration pass that audits and refines the draft. Reviewer
     * failures fall back to the draft instead of failing the whole run.
     */
    fun runGenerator(generator: LoreGenerator, option: String, extra: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val primary = settings.primaryProfile()
            if (primary == null || primary.apiKey.isBlank()) {
                _aiState.value = LoreAiState.Failure(generator, NO_AI_MESSAGE)
                return@launch
            }
            val reviewer = settings.reviewerProfile()
            _aiState.value = LoreAiState.Loading(generator, "「${primary.name}」正在起草…")
            val system = buildSystemPrompt()
            val result = runCatching {
                val draftRaw = AiClient.generate(
                    config = primary.toAiConfig(),
                    system = system,
                    userPrompt = LorePrompts.buildUserPrompt(generator, option, extra),
                    maxTokens = 8192,
                )
                val draft = LorePrompts.parseGenerated(draftRaw)
                if (reviewer != null) {
                    _aiState.value = LoreAiState.Loading(generator, "「${reviewer.name}」正在审校…")
                    runCatching {
                        val reviewedRaw = AiClient.generate(
                            config = reviewer.toAiConfig(),
                            system = system,
                            userPrompt = LorePrompts.buildReviewPrompt(
                                generator, option, extra, LorePrompts.toJson(draft)
                            ),
                            maxTokens = 8192,
                        )
                        LorePrompts.parseGenerated(reviewedRaw) to true
                    }.getOrElse { draft to false }
                } else {
                    draft to false
                }
            }
            result.fold(
                onSuccess = { (entries, reviewed) ->
                    _aiState.value = LoreAiState.Preview(generator, entries, reviewed)
                },
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

enum class LoreRevision(val label: String, val description: String) {
    EXPAND("扩写完善", "补足细节与画面感，让设定更丰满"),
    CONDENSE("精简凝练", "压缩冗余表达，保留全部关键信息"),
    CONSISTENCY("一致性校对", "对照全书设定，修正本条目的矛盾之处"),
    CUSTOM("自定义修订", "按你的指令修订这条设定"),
}

sealed interface RevisionState {
    data object Hidden : RevisionState
    data class Loading(val revision: LoreRevision) : RevisionState
    data class Success(val revision: LoreRevision, val text: String) : RevisionState
    data class Failure(val revision: LoreRevision, val message: String) : RevisionState
}

class LoreEditorViewModel(
    private val repo: NovelRepository,
    private val settingsRepo: SettingsRepository,
    private val entryId: Long,
) : ViewModel() {

    private val _entry = MutableStateFlow<LoreEntry?>(null)

    /** Loaded once; the editor owns the buffer afterwards. */
    val entry: StateFlow<LoreEntry?> = _entry

    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved

    private val _revisionState = MutableStateFlow<RevisionState>(RevisionState.Hidden)
    val revisionState: StateFlow<RevisionState> = _revisionState

    private var revisionJob: Job? = null

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

    /** AI revision of this entry, grounded in the whole book's lore. */
    fun runRevision(revision: LoreRevision, instruction: String, name: String, content: String) {
        revisionJob?.cancel()
        revisionJob = viewModelScope.launch {
            _revisionState.value = RevisionState.Loading(revision)
            val settings = settingsRepo.settings.first()
            val profile = settings.primaryProfile()
            if (profile == null || profile.apiKey.isBlank()) {
                _revisionState.value = RevisionState.Failure(revision, NO_AI_MESSAGE)
                return@launch
            }
            val loaded = repo.getLoreEntry(entryId)
            val result = runCatching {
                AiClient.generate(
                    config = profile.toAiConfig(),
                    system = buildSystemPrompt(loaded?.bookId),
                    userPrompt = buildRevisionPrompt(
                        revision = revision,
                        instruction = instruction,
                        categoryLabel = loaded?.loreCategory?.label ?: "设定",
                        name = name,
                        content = content,
                    ),
                    maxTokens = 4096,
                )
            }
            result.fold(
                onSuccess = { _revisionState.value = RevisionState.Success(revision, it.trim()) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _revisionState.value =
                        RevisionState.Failure(revision, e.message ?: "请求失败，请稍后重试")
                },
            )
        }
    }

    fun dismissRevision() {
        revisionJob?.cancel()
        _revisionState.value = RevisionState.Hidden
    }

    private suspend fun buildSystemPrompt(bookId: Long?): String {
        val book = bookId?.let { repo.getBook(it) }
        val lore = bookId?.let { repo.listLore(it) } ?: emptyList()
        return buildString {
            appendLine("你是一位资深的中文网络小说设定主编，擅长在不破坏原意的前提下打磨世界观条目。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("以下是作品的全部世界观设定（修订时必须与其保持一致）：")
                lore.take(30).forEach {
                    val detail = it.content.take(200).replace('\n', ' ')
                    appendLine("- [${it.loreCategory.label}] ${it.name}：$detail")
                }
            }
        }.trim()
    }

    private fun buildRevisionPrompt(
        revision: LoreRevision,
        instruction: String,
        categoryLabel: String,
        name: String,
        content: String,
    ): String {
        val requirement = when (revision) {
            LoreRevision.EXPAND ->
                "扩写完善：补足细节与画面感，丰富这条设定的内涵与可展开的剧情钩子，篇幅可增加一倍左右，但不得偏离原意。"
            LoreRevision.CONDENSE ->
                "精简凝练：压缩冗余表达，保留全部关键信息，使行文干净利落。"
            LoreRevision.CONSISTENCY ->
                "一致性校对：逐句对照系统提示中的全书设定，修正本条目中与其它设定矛盾或脱节的地方，并在恰当处补上呼应。"
            LoreRevision.CUSTOM ->
                "按以下作者指令修订：$instruction"
        }
        return buildString {
            appendLine("这是作品世界观中一条「$categoryLabel」设定，名称「$name」，当前内容如下：")
            appendLine(content.ifBlank { "（暂无内容）" })
            appendLine()
            appendLine("修订要求：$requirement")
            appendLine()
            append("直接输出修订后的完整设定内容（纯文本），不要 JSON、不要任何解释或前缀。")
        }
    }

    companion object {
        fun factory(entryId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InkApp
                LoreEditorViewModel(app.repository, app.settings, entryId)
            }
        }
    }
}
