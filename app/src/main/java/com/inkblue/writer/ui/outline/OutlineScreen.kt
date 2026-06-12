package com.inkblue.writer.ui.outline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.inkblue.writer.ai.OutlinePrompts
import com.inkblue.writer.ai.toAiConfig
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.NovelRepository
import com.inkblue.writer.data.OutlineNode
import com.inkblue.writer.data.SettingsRepository
import com.inkblue.writer.ui.components.ConfirmDialog
import com.inkblue.writer.ui.components.EmptyState
import com.inkblue.writer.ui.components.InkCard
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

sealed interface OutlineAiState {
    data object Hidden : OutlineAiState
    data object Loading : OutlineAiState
    data class Preview(val nodes: List<Pair<String, String>>) : OutlineAiState
    data class Failure(val message: String) : OutlineAiState
}

class OutlineViewModel(
    private val repo: NovelRepository,
    private val settingsRepo: SettingsRepository,
    private val bookId: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nodes: StateFlow<List<OutlineNode>> = repo.observeOutline(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiState = MutableStateFlow<OutlineAiState>(OutlineAiState.Hidden)
    val aiState: StateFlow<OutlineAiState> = _aiState

    private var aiJob: Job? = null

    fun addNode(title: String, content: String) {
        viewModelScope.launch { repo.createOutlineNode(bookId, title, content) }
    }

    fun updateNode(node: OutlineNode, title: String, content: String) {
        viewModelScope.launch { repo.updateOutlineNode(node, title, content) }
    }

    fun deleteNode(node: OutlineNode) {
        viewModelScope.launch { repo.deleteOutlineNode(node) }
    }

    fun moveNode(node: OutlineNode, up: Boolean) {
        val list = nodes.value
        val index = list.indexOfFirst { it.id == node.id }
        val otherIndex = if (up) index - 1 else index + 1
        if (index < 0 || otherIndex !in list.indices) return
        viewModelScope.launch { repo.swapOutlineOrder(list[index], list[otherIndex]) }
    }

    fun runPlanner(count: Int, extra: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = OutlineAiState.Loading
            val settings = settingsRepo.settings.first()
            val profile = settings.primaryProfile()
            if (profile == null || profile.apiKey.isBlank()) {
                _aiState.value =
                    OutlineAiState.Failure("尚未配置 AI 服务。请前往「设置 → AI 服务」添加并填写 API Key。")
                return@launch
            }
            val hasExisting = nodes.value.isNotEmpty()
            val result = runCatching {
                val raw = AiClient.generate(
                    config = profile.toAiConfig(),
                    system = buildSystemPrompt(),
                    userPrompt = OutlinePrompts.buildPlanPrompt(count, extra, hasExisting),
                    maxTokens = 8192,
                )
                OutlinePrompts.parsePlan(raw)
            }
            result.fold(
                onSuccess = { _aiState.value = OutlineAiState.Preview(it) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _aiState.value = OutlineAiState.Failure(e.message ?: "请求失败，请稍后重试")
                },
            )
        }
    }

    fun savePlanned(planned: List<Pair<String, String>>) {
        viewModelScope.launch {
            withContext(NonCancellable) { repo.appendOutlineNodes(bookId, planned) }
            _aiState.value = OutlineAiState.Hidden
        }
    }

    fun dismissAi() {
        aiJob?.cancel()
        _aiState.value = OutlineAiState.Hidden
    }

    private suspend fun buildSystemPrompt(): String {
        val book = repo.getBook(bookId)
        val lore = repo.listLore(bookId)
        val outline = repo.listOutline(bookId)
        return buildString {
            appendLine("你是一位资深的中文网络小说策划编辑，擅长规划节奏明快、爽点密集且逻辑自洽的长篇大纲。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("作品的世界观设定（大纲必须与其一致）：")
                lore.take(20).forEach {
                    appendLine("- [${it.loreCategory.label}] ${it.name}：${it.content.take(150).replace('\n', ' ')}")
                }
            }
            if (outline.isNotEmpty()) {
                appendLine("已有大纲（按顺序）：")
                outline.forEachIndexed { i, n ->
                    appendLine("${i + 1}. ${n.title}：${n.content.take(120).replace('\n', ' ')}")
                }
            }
        }.trim()
    }

    companion object {
        fun factory(bookId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InkApp
                OutlineViewModel(app.repository, app.settings, bookId)
            }
        }
    }
}

@Composable
fun OutlineScreen(
    bookId: Long,
    onBack: () -> Unit,
    vm: OutlineViewModel = viewModel(factory = OutlineViewModel.factory(bookId)),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<OutlineNode?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<OutlineNode?>(null) }
    var plannerOpen by remember { mutableStateOf(false) }
    var lastCount by remember { mutableStateOf(10) }
    var lastExtra by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "大纲",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "《${book?.title ?: ""}》 · ${nodes.size} 个阶段",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { plannerOpen = true }) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI 规划大纲",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("新建阶段")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (nodes.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有大纲",
                        hint = "手动添加剧情阶段，或点右上角 ✨ 让 AI 一键规划",
                    )
                }
            }
            items(nodes, key = { it.id }) { node ->
                val index = nodes.indexOfFirst { it.id == node.id }
                OutlineRow(
                    index = index,
                    total = nodes.size,
                    node = node,
                    onEdit = { editing = node },
                    onDelete = { deleting = node },
                    onMoveUp = { vm.moveNode(node, up = true) },
                    onMoveDown = { vm.moveNode(node, up = false) },
                )
            }
        }
    }

    if (creating) {
        OutlineEditDialog(
            title = "新建阶段",
            initialTitle = "",
            initialContent = "",
            onDismiss = { creating = false },
            onConfirm = { t, c ->
                vm.addNode(t, c)
                creating = false
            },
        )
    }

    editing?.let { node ->
        OutlineEditDialog(
            title = "编辑阶段",
            initialTitle = node.title,
            initialContent = node.content,
            onDismiss = { editing = null },
            onConfirm = { t, c ->
                vm.updateNode(node, t, c)
                editing = null
            },
        )
    }

    deleting?.let { node ->
        ConfirmDialog(
            title = "删除「${node.title}」？",
            message = "该大纲阶段将被删除，且无法恢复。",
            onConfirm = {
                vm.deleteNode(node)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (plannerOpen) {
        PlannerDialog(
            onDismiss = { plannerOpen = false },
            onConfirm = { count, extra ->
                plannerOpen = false
                lastCount = count
                lastExtra = extra
                vm.runPlanner(count, extra)
            },
        )
    }

    when (val state = aiState) {
        OutlineAiState.Hidden -> Unit

        OutlineAiState.Loading -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("AI 规划大纲", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "正在排布剧情，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("取消") }
            },
        )

        is OutlineAiState.Preview -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("大纲方案 · ${state.nodes.size} 个阶段", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.nodes.forEachIndexed { index, (title, content) ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(
                            "${index + 1}. $title",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.savePlanned(state.nodes) }) { Text("追加保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.runPlanner(lastCount, lastExtra) }) { Text("重试") }
                    TextButton(onClick = { vm.dismissAi() }) { Text("放弃") }
                }
            },
        )

        is OutlineAiState.Failure -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("规划失败", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.runPlanner(lastCount, lastExtra) }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun OutlineRow(
    index: Int,
    total: Int,
    node: OutlineNode,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InkCard(onClick = onEdit, contentPadding = 14) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${index + 1}. ${node.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (node.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        node.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "下移",
                    tint = if (index < total - 1) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outline,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "菜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlineEditDialog(
    title: String,
    initialTitle: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var nodeTitle by remember { mutableStateOf(initialTitle) }
    var nodeContent by remember { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nodeTitle,
                    onValueChange = { nodeTitle = it },
                    label = { Text("阶段标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = nodeContent,
                    onValueChange = { nodeContent = it },
                    label = { Text("剧情概要") },
                    placeholder = { Text("核心冲突、关键事件、出场角色、结尾钩子……") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nodeTitle.trim(), nodeContent.trim()) },
                enabled = nodeTitle.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun PlannerDialog(
    onDismiss: () -> Unit,
    onConfirm: (count: Int, extra: String) -> Unit,
) {
    var count by remember { mutableStateOf(10) }
    var extra by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("AI 规划大纲", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "基于世界观与已有大纲排布剧情阶段；已有大纲时自动往后续排",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15).forEach { n ->
                        FilterChip(
                            selected = count == n,
                            onClick = { count = n },
                            label = { Text("$n 个阶段") },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("作者补充（可选）") },
                    placeholder = { Text("主线方向、想要的高潮节点、篇幅预期……") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count, extra.trim()) }) { Text("开始规划") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
