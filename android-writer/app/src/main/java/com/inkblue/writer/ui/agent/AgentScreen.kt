package com.inkblue.writer.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inkblue.writer.InkApp
import com.inkblue.writer.ai.AiClient
import com.inkblue.writer.ai.ChatTurn
import com.inkblue.writer.ai.TavilyClient
import com.inkblue.writer.ai.toAiConfig
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.NovelRepository
import com.inkblue.writer.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AgentMessage(val role: String, val text: String)

class AgentViewModel(
    private val repo: NovelRepository,
    private val settingsRepo: SettingsRepository,
    private val bookId: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private var job: Job? = null

    fun send(text: String, useSearch: Boolean) {
        if (text.isBlank() || _busy.value) return
        _messages.value = _messages.value + AgentMessage("user", text.trim())
        job?.cancel()
        job = viewModelScope.launch {
            _busy.value = true
            try {
                val settings = settingsRepo.settings.first()
                val profile = settings.primaryProfile()
                if (profile == null || profile.apiKey.isBlank()) {
                    _messages.value = _messages.value +
                        AgentMessage("assistant", "尚未配置 AI 服务。请前往「设置 → AI 服务」添加并填写 API Key。")
                    return@launch
                }
                val history = _messages.value.takeLast(20).map { ChatTurn(it.role, it.text) }
                var system = buildSystemPrompt()
                var nativeSearch = false
                if (useSearch) {
                    val tavily = settings.tavilyApiKey
                    if (tavily.isNotBlank()) {
                        val ctx = runCatching { TavilyClient.search(tavily, text.trim()) }.getOrNull()
                        if (!ctx.isNullOrBlank()) {
                            system += "\n\n【联网搜索资料（Tavily）】可参考以下资料回答：\n$ctx"
                        }
                    } else {
                        nativeSearch = true
                    }
                }
                val reply = runCatching {
                    AiClient.chat(
                        config = profile.toAiConfig(),
                        system = system,
                        history = history,
                        maxTokens = 4096,
                        enableSearch = nativeSearch,
                    )
                }.getOrElse { e ->
                    if (e is CancellationException) throw e
                    "（请求失败：${e.message ?: "未知错误"}，可重新发送）"
                }
                _messages.value = _messages.value + AgentMessage("assistant", reply)
            } finally {
                _busy.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        _busy.value = false
    }

    fun clear() {
        job?.cancel()
        _busy.value = false
        _messages.value = emptyList()
    }

    /** Agent persona + the entire creative context of this book. */
    private suspend fun buildSystemPrompt(): String {
        val book = repo.getBook(bookId)
        val lore = repo.listLore(bookId)
        val outline = repo.listOutline(bookId)
        val chapters = repo.observeChapters(bookId).first()
        val lastChapter = chapters.lastOrNull()
        return buildString {
            appendLine("你是「墨蓝写作」内置的网文创作助手 Agent，身兼资深策划编辑与代笔写手。")
            appendLine("你的职责：与作者讨论剧情走向、解答创作问题、按要求产出可直接使用的正文片段或修改稿、为角色和招式起名。回答一律使用中文。")
            appendLine("输出正文片段时直接给出成稿，不要附加解释；讨论剧情时给出明确观点和理由，并主动指出设定或大纲中的漏洞。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("世界观设定（所有产出必须与其一致）：")
                lore.take(30).forEach {
                    appendLine("- [${it.loreCategory.label}] ${it.name}：${it.content.take(200).replace('\n', ' ')}")
                }
            }
            if (outline.isNotEmpty()) {
                appendLine("故事大纲：")
                outline.take(30).forEachIndexed { i, n ->
                    appendLine("${i + 1}. ${n.title}：${n.content.take(150).replace('\n', ' ')}")
                }
            }
            if (chapters.isNotEmpty()) {
                appendLine("已有章节：${chapters.joinToString("、") { it.title }.take(500)}")
            }
            if (lastChapter != null && lastChapter.content.isNotBlank()) {
                appendLine("最新章节《${lastChapter.title}》的结尾：")
                appendLine(lastChapter.content.takeLast(2000))
            }
        }.trim()
    }

    companion object {
        fun factory(bookId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InkApp
                AgentViewModel(app.repository, app.settings, bookId)
            }
        }
    }
}

@Composable
fun AgentScreen(
    bookId: Long,
    onBack: () -> Unit,
    vm: AgentViewModel = viewModel(factory = AgentViewModel.factory(bookId)),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var useSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (busy) 1 else 0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "创作助手",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                )
                Text(
                    "《${book?.title ?: ""}》 · 熟知本书设定与大纲",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { vm.clear() }, enabled = messages.isNotEmpty()) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "清空对话",
                    tint = if (messages.isNotEmpty()) colors.onSurfaceVariant else colors.outline,
                )
            }
        }
        HorizontalDivider(color = colors.outline)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "和创作助手聊聊这本书",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onBackground,
                        )
                        Text(
                            "「下一章怎么写？」「帮我写一段主角突破的高潮戏」\n「给这个反派起十个名字」「这段大纲有什么漏洞？」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages.size) { index ->
                val msg = messages[index]
                MessageBubble(msg)
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "助手正在思考…",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Input bar — bottom, within thumb reach.
        Surface(color = colors.surface) {
            Column {
                HorizontalDivider(color = colors.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = { useSearch = !useSearch }) {
                        Icon(
                            Icons.Filled.TravelExplore,
                            contentDescription = "联网搜索",
                            tint = if (useSearch) colors.primary else colors.outline,
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(if (useSearch) "已开启联网搜索（Tavily / 原生）…" else "和助手聊聊剧情、要一段正文…")
                        },
                        minLines = 1,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            vm.send(input, useSearch)
                            input = ""
                        },
                        enabled = input.isNotBlank() && !busy,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (input.isNotBlank() && !busy) colors.primary else colors.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: AgentMessage) {
    val colors = MaterialTheme.colorScheme
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) colors.primary else colors.surface,
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            SelectionContainer {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) colors.onPrimary else colors.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
