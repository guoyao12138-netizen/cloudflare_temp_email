package com.inkblue.writer.ui.styles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.inkblue.writer.ai.StylePrompts
import com.inkblue.writer.ai.toAiConfig
import com.inkblue.writer.data.NovelRepository
import com.inkblue.writer.data.SettingsRepository
import com.inkblue.writer.data.StyleProfile
import com.inkblue.writer.ui.components.ConfirmDialog
import com.inkblue.writer.ui.components.EmptyState
import com.inkblue.writer.ui.components.InkCard
import com.inkblue.writer.ui.components.TextInputDialog
import com.inkblue.writer.util.formatTime
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

sealed interface StyleAiState {
    data object Hidden : StyleAiState
    data object Loading : StyleAiState
    data class Preview(val name: String, val analysis: String) : StyleAiState
    data class Failure(val message: String) : StyleAiState
}

class StyleLibraryViewModel(
    private val repo: NovelRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val styles: StateFlow<List<StyleProfile>> = repo.observeStyles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiState = MutableStateFlow<StyleAiState>(StyleAiState.Hidden)
    val aiState: StateFlow<StyleAiState> = _aiState

    private var aiJob: Job? = null

    /** The style agent reads the sample and distills an imitation guide. */
    fun analyze(name: String, sample: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = StyleAiState.Loading
            val settings = settingsRepo.settings.first()
            val profile = settings.primaryProfile()
            if (profile == null || profile.apiKey.isBlank()) {
                _aiState.value =
                    StyleAiState.Failure("尚未配置 AI 服务。请前往「设置 → AI 服务」添加并填写 API Key。")
                return@launch
            }
            val result = runCatching {
                AiClient.generate(
                    config = profile.toAiConfig(),
                    system = StylePrompts.ANALYSIS_SYSTEM,
                    userPrompt = StylePrompts.buildAnalysisPrompt(sample.take(8000)),
                    maxTokens = 4096,
                )
            }
            result.fold(
                onSuccess = { _aiState.value = StyleAiState.Preview(name, it.trim()) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _aiState.value = StyleAiState.Failure(e.message ?: "请求失败，请稍后重试")
                },
            )
        }
    }

    fun saveAnalyzed(name: String, analysis: String) {
        viewModelScope.launch {
            withContext(NonCancellable) { repo.createStyle(name, analysis) }
            _aiState.value = StyleAiState.Hidden
        }
    }

    fun rename(style: StyleProfile, name: String) {
        viewModelScope.launch { repo.renameStyle(style, name) }
    }

    fun delete(style: StyleProfile) {
        viewModelScope.launch { repo.deleteStyle(style) }
    }

    fun dismissAi() {
        aiJob?.cancel()
        _aiState.value = StyleAiState.Hidden
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InkApp
                StyleLibraryViewModel(app.repository, app.settings)
            }
        }
    }
}

/** Decode TXT bytes as UTF-8; fall back to GBK when the text looks garbled. */
private fun decodeSmart(bytes: ByteArray): String {
    val utf8 = String(bytes, Charsets.UTF_8)
    val bad = utf8.count { it == '�' }
    if (bad == 0 || utf8.isEmpty() || bad.toFloat() / utf8.length < 0.02f) return utf8
    return runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
}

@Composable
fun StyleLibraryScreen(
    onBack: () -> Unit,
    vm: StyleLibraryViewModel = viewModel(factory = StyleLibraryViewModel.Factory),
) {
    val styles by vm.styles.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<StyleProfile?>(null) }
    var renaming by remember { mutableStateOf<StyleProfile?>(null) }
    var deleting by remember { mutableStateOf<StyleProfile?>(null) }
    var lastName by remember { mutableStateOf("") }
    var lastSample by remember { mutableStateOf("") }

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
                        "文风库",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "上传小说文本，AI 提炼文风指南，写作时一键仿写",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                Text("新建文风")
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (styles.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "还没有文风档案",
                        hint = "粘贴或导入一段小说文本（建议 2000 字以上），\nAI 会研读并提炼可模仿的文风指南",
                    )
                }
            }
            items(styles, key = { it.id }) { style ->
                StyleRow(
                    style = style,
                    onClick = { viewing = style },
                    onRename = { renaming = style },
                    onDelete = { deleting = style },
                )
            }
        }
    }

    if (creating) {
        StyleCreateDialog(
            onDismiss = { creating = false },
            onConfirm = { name, sample ->
                creating = false
                lastName = name
                lastSample = sample
                vm.analyze(name, sample)
            },
        )
    }

    viewing?.let { style ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(style.name, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        style.analysis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewing = null }) { Text("关闭") }
            },
        )
    }

    renaming?.let { style ->
        TextInputDialog(
            title = "重命名文风",
            primaryLabel = "名称",
            initialPrimary = style.name,
            confirmText = "保存",
            onDismiss = { renaming = null },
            onConfirm = { name, _ ->
                vm.rename(style, name)
                renaming = null
            },
        )
    }

    deleting?.let { style ->
        ConfirmDialog(
            title = "删除「${style.name}」？",
            message = "该文风档案将被删除，且无法恢复。",
            onConfirm = {
                vm.delete(style)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    when (val state = aiState) {
        StyleAiState.Hidden -> Unit

        StyleAiState.Loading -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("文风分析", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "文风 Agent 正在研读文本…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("取消") }
            },
        )

        is StyleAiState.Preview -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("文风指南 · ${state.name}", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        state.analysis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveAnalyzed(state.name, state.analysis) }) {
                    Text("保存到文风库")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.analyze(lastName, lastSample) }) { Text("重试") }
                    TextButton(onClick = { vm.dismissAi() }) { Text("放弃") }
                }
            },
        )

        is StyleAiState.Failure -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("分析失败", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.analyze(lastName, lastSample) }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun StyleRow(
    style: StyleProfile,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InkCard(onClick = onClick, contentPadding = 14) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    style.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    style.analysis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatTime(style.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = { Text("查看指南") },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menuOpen = false
                            onRename()
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
private fun StyleCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, sample: String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            }.fold(
                onSuccess = { bytes ->
                    importError = null
                    sample = decodeSmart(bytes).take(20000)
                },
                onFailure = { importError = "文件读取失败，请改用粘贴文本" },
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("新建文风档案", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文风名称") },
                    placeholder = { Text("如：某大神的热血流 / 古典仙侠风") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it.take(20000) },
                    label = { Text("小说样本（${sample.length} 字）") },
                    placeholder = { Text("粘贴一段有代表性的原文，建议 2000 字以上") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            picker.launch(
                                arrayOf("text/plain", "text/*", "application/octet-stream")
                            )
                        },
                    ) { Text("导入 TXT 文件") }
                    Text(
                        importError ?: "支持 UTF-8 / GBK 编码，超长自动截取",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (importError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), sample.trim()) },
                enabled = name.isNotBlank() && sample.trim().length >= 200,
            ) { Text("开始分析") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
