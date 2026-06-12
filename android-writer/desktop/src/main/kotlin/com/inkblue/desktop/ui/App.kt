package com.inkblue.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkblue.desktop.ai.AiClient
import com.inkblue.desktop.ai.EditorAction
import com.inkblue.desktop.ai.EditorPrompts
import com.inkblue.desktop.ai.GeneratedLore
import com.inkblue.desktop.ai.LoreGenerator
import com.inkblue.desktop.ai.LorePrompts
import com.inkblue.desktop.ai.OutlinePrompts
import com.inkblue.desktop.ai.StylePrompts
import com.inkblue.desktop.ai.toAiConfig
import com.inkblue.desktop.data.AiProfile
import com.inkblue.desktop.data.AiProvider
import com.inkblue.desktop.data.AppData
import com.inkblue.desktop.data.LoreCategory
import com.inkblue.desktop.data.Store
import com.inkblue.desktop.data.StyleProfile
import com.inkblue.desktop.data.countWords
import com.inkblue.desktop.data.formatWordCount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val NO_AI_MESSAGE = "尚未配置 AI 服务。请前往「设置 → AI 服务」添加并填写 API Key。"

private sealed interface Nav {
    data object Shelf : Nav
    data class BookView(val bookId: Long) : Nav
    data class Editor(val bookId: Long, val chapterId: Long) : Nav
    data object Styles : Nav
    data object Settings : Nav
}

private enum class BookTab(val label: String) { CHAPTERS("章节"), OUTLINE("大纲"), LORE("世界观") }

@Composable
fun App() {
    val data by Store.data.collectAsState()
    var nav by remember { mutableStateOf<Nav>(Nav.Shelf) }
    Row(Modifier.fillMaxSize()) {
        SideRail(nav = nav, onNav = { nav = it })
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val n = nav) {
                Nav.Shelf -> ShelfScreen(data) { nav = Nav.BookView(it) }
                is Nav.BookView -> BookScreen(
                    data = data,
                    bookId = n.bookId,
                    onOpenChapter = { nav = Nav.Editor(n.bookId, it) },
                    onBookDeleted = { nav = Nav.Shelf },
                )
                is Nav.Editor -> EditorScreen(
                    data = data,
                    bookId = n.bookId,
                    chapterId = n.chapterId,
                    onBack = { nav = Nav.BookView(n.bookId) },
                )
                Nav.Styles -> StylesScreen(data)
                Nav.Settings -> SettingsScreen(data)
            }
        }
    }
}

@Composable
private fun SideRail(nav: Nav, onNav: (Nav) -> Unit) {
    val data by Store.data.collectAsState()
    Column(Modifier.width(200.dp).fillMaxHeight().padding(16.dp)) {
        Text(
            "墨蓝写作",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "InkBlue Writer",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        RailItem("书架", Icons.Filled.MenuBook, nav is Nav.Shelf || nav is Nav.BookView || nav is Nav.Editor) {
            onNav(Nav.Shelf)
        }
        RailItem("文风库", Icons.Filled.Brush, nav is Nav.Styles) { onNav(Nav.Styles) }
        RailItem("设置", Icons.Filled.Settings, nav is Nav.Settings) { onNav(Nav.Settings) }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("深色模式", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = data.settings.dark,
                onCheckedChange = { dark -> Store.updateSettings { it.copy(dark = dark) } },
            )
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Bottom-center pill bar hosting AI and create actions. */
@Composable
private fun BoxScope.BottomActions(content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = if (primary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label)
    }
}

// ============================== Shelf ==============================

@Composable
private fun ShelfScreen(data: AppData, onOpenBook: (Long) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("书架", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (data.books.isEmpty()) "落笔成章，始于此页" else "共 ${data.books.size} 部作品",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (data.books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState("还没有作品", "点击下方「新建作品」，开始你的第一部小说")
                    }
                }
                items(data.books.sortedByDescending { it.updatedAt }, key = { it.id }) { book ->
                    val chapterCount = data.chapters.count { it.bookId == book.id }
                    val totalWords = data.chapters.filter { it.bookId == book.id }.sumOf { countWords(it.content) }
                    var menuOpen by remember { mutableStateOf(false) }
                    InkCard(onClick = { onOpenBook(book.id) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    book.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (book.description.isNotBlank()) {
                                    Text(
                                        book.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "$chapterCount 章 · ${formatWordCount(totalWords)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("编辑信息") }, onClick = { menuOpen = false; editingId = book.id })
                                    DropdownMenuItem(
                                        text = { Text("删除作品", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; deletingId = book.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        BottomActions {
            PillButton("新建作品", Icons.Filled.Add, primary = true) { creating = true }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "新建作品",
            primaryLabel = "书名",
            secondaryLabel = "简介（可选）",
            confirmText = "创建",
            onDismiss = { creating = false },
            onConfirm = { t, d -> Store.addBook(t, d); creating = false },
        )
    }
    editingId?.let { id ->
        val book = data.books.firstOrNull { it.id == id } ?: return@let
        TextInputDialog(
            title = "编辑作品信息",
            primaryLabel = "书名",
            initialPrimary = book.title,
            secondaryLabel = "简介（可选）",
            initialSecondary = book.description,
            confirmText = "保存",
            onDismiss = { editingId = null },
            onConfirm = { t, d -> Store.updateBook(id, t, d); editingId = null },
        )
    }
    deletingId?.let { id ->
        val book = data.books.firstOrNull { it.id == id } ?: return@let
        ConfirmDialog(
            title = "删除《${book.title}》？",
            message = "该作品下的所有章节、大纲与设定将一并删除，且无法恢复。",
            onConfirm = { Store.deleteBook(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
}

// ============================== Book view ==============================

private sealed interface GenState {
    data object Hidden : GenState
    data class Loading(val label: String) : GenState
    data class LorePreview(val generator: LoreGenerator, val entries: List<GeneratedLore>, val reviewed: Boolean) : GenState
    data class PlanPreview(val nodes: List<Pair<String, String>>) : GenState
    data class Failure(val title: String, val message: String, val retry: () -> Unit) : GenState
}

@Composable
private fun BookScreen(
    data: AppData,
    bookId: Long,
    onOpenChapter: (Long) -> Unit,
    onBookDeleted: () -> Unit,
) {
    val book = data.books.firstOrNull { it.id == bookId }
    if (book == null) {
        LaunchedEffect(Unit) { onBookDeleted() }
        return
    }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(BookTab.CHAPTERS) }
    var genState by remember { mutableStateOf<GenState>(GenState.Hidden) }
    var aiJob by remember { mutableStateOf<Job?>(null) }

    // dialogs
    var creatingChapter by remember { mutableStateOf(false) }
    var creatingNode by remember { mutableStateOf(false) }
    var editingNodeId by remember { mutableStateOf<Long?>(null) }
    var creatingLore by remember { mutableStateOf(false) }
    var editingLoreId by remember { mutableStateOf<Long?>(null) }
    var loreFilter by remember { mutableStateOf<LoreCategory?>(null) }
    var generatorChooserOpen by remember { mutableStateOf(false) }
    var generatorInput by remember { mutableStateOf<LoreGenerator?>(null) }
    var plannerOpen by remember { mutableStateOf(false) }
    var lastRun by remember { mutableStateOf({}) }

    fun dismissAi() {
        aiJob?.cancel()
        genState = GenState.Hidden
    }

    fun runLoreGenerator(generator: LoreGenerator, option: String, extra: String) {
        aiJob?.cancel()
        lastRun = { runLoreGenerator(generator, option, extra) }
        aiJob = scope.launch {
            val current = Store.data.value
            val primary = current.settings.primaryProfile()
            if (primary == null || primary.apiKey.isBlank()) {
                genState = GenState.Failure(generator.label, NO_AI_MESSAGE) { runLoreGenerator(generator, option, extra) }
                return@launch
            }
            genState = GenState.Loading("「${primary.name}」正在起草…")
            val system = EditorPrompts.buildLoreSystem(current, bookId)
            val result = runCatching {
                val draft = LorePrompts.parseGenerated(
                    AiClient.generate(primary.toAiConfig(), system, LorePrompts.buildUserPrompt(generator, option, extra), 8192)
                )
                val reviewer = current.settings.reviewerProfile()
                if (reviewer != null) {
                    genState = GenState.Loading("「${reviewer.name}」正在审校…")
                    runCatching {
                        LorePrompts.parseGenerated(
                            AiClient.generate(
                                reviewer.toAiConfig(), system,
                                LorePrompts.buildReviewPrompt(generator, option, extra, LorePrompts.toJson(draft)), 8192,
                            )
                        ) to true
                    }.getOrElse { draft to false }
                } else draft to false
            }
            genState = result.fold(
                onSuccess = { (entries, reviewed) -> GenState.LorePreview(generator, entries, reviewed) },
                onFailure = { e -> GenState.Failure(generator.label, e.message ?: "请求失败") { runLoreGenerator(generator, option, extra) } },
            )
        }
    }

    fun runPlanner(count: Int, extra: String) {
        aiJob?.cancel()
        aiJob = scope.launch {
            val current = Store.data.value
            val primary = current.settings.primaryProfile()
            if (primary == null || primary.apiKey.isBlank()) {
                genState = GenState.Failure("AI 规划", NO_AI_MESSAGE) { runPlanner(count, extra) }
                return@launch
            }
            genState = GenState.Loading("正在排布剧情…")
            val hasExisting = current.outline.any { it.bookId == bookId }
            val result = runCatching {
                OutlinePrompts.parsePlan(
                    AiClient.generate(
                        primary.toAiConfig(),
                        EditorPrompts.buildOutlineSystem(current, bookId),
                        OutlinePrompts.buildPlanPrompt(count, extra, hasExisting),
                        8192,
                    )
                )
            }
            genState = result.fold(
                onSuccess = { GenState.PlanPreview(it) },
                onFailure = { e -> GenState.Failure("AI 规划", e.message ?: "请求失败") { runPlanner(count, extra) } },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val chapters = data.chapters.filter { it.bookId == bookId }
                    Text(
                        "${chapters.size} 章 · ${formatWordCount(chapters.sumOf { countWords(it.content) })}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                BookTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                BookTab.CHAPTERS -> ChaptersTab(data, bookId, onOpenChapter)
                BookTab.OUTLINE -> OutlineTab(
                    data, bookId,
                    onEdit = { editingNodeId = it },
                )
                BookTab.LORE -> LoreTab(
                    data, bookId, loreFilter,
                    onFilter = { loreFilter = it },
                    onEdit = { editingLoreId = it },
                )
            }
        }
        BottomActions {
            when (tab) {
                BookTab.CHAPTERS -> PillButton("新建章节", Icons.Filled.Add, primary = true) { creatingChapter = true }
                BookTab.OUTLINE -> {
                    PillButton("AI 规划", Icons.Filled.AutoAwesome, primary = true) { plannerOpen = true }
                    PillButton("新建阶段", Icons.Filled.Add, primary = false) { creatingNode = true }
                }
                BookTab.LORE -> {
                    PillButton("AI 构筑", Icons.Filled.AutoAwesome, primary = true) { generatorChooserOpen = true }
                    PillButton("新建设定", Icons.Filled.Add, primary = false) { creatingLore = true }
                }
            }
        }
    }

    // ---- dialogs ----
    if (creatingChapter) {
        val nextIndex = data.chapters.count { it.bookId == bookId } + 1
        TextInputDialog(
            title = "新建章节",
            primaryLabel = "章节名",
            initialPrimary = "第${nextIndex}章 ",
            confirmText = "创建并开始写作",
            onDismiss = { creatingChapter = false },
            onConfirm = { t, _ ->
                creatingChapter = false
                onOpenChapter(Store.addChapter(bookId, t))
            },
        )
    }
    if (creatingNode) {
        TextInputDialog(
            title = "新建阶段",
            primaryLabel = "阶段标题",
            secondaryLabel = "剧情概要",
            confirmText = "保存",
            onDismiss = { creatingNode = false },
            onConfirm = { t, c -> Store.addOutline(bookId, t, c); creatingNode = false },
        )
    }
    editingNodeId?.let { id ->
        val node = data.outline.firstOrNull { it.id == id } ?: return@let
        TextInputDialog(
            title = "编辑阶段",
            primaryLabel = "阶段标题",
            initialPrimary = node.title,
            secondaryLabel = "剧情概要",
            initialSecondary = node.content,
            confirmText = "保存",
            secondaryMinLines = 4,
            onDismiss = { editingNodeId = null },
            onConfirm = { t, c -> Store.saveOutline(id, t, c); editingNodeId = null },
        )
    }
    if (creatingLore) {
        LoreEditDialog(
            title = "新建设定",
            initialCategory = loreFilter ?: LoreCategory.CHARACTER,
            initialName = "",
            initialContent = "",
            onDismiss = { creatingLore = false },
            onConfirm = { cat, n, c -> Store.addLore(bookId, cat, n, c); creatingLore = false },
        )
    }
    editingLoreId?.let { id ->
        val entry = data.lore.firstOrNull { it.id == id } ?: return@let
        LoreEditDialog(
            title = "编辑设定",
            initialCategory = entry.category,
            initialName = entry.name,
            initialContent = entry.content,
            categoryEditable = false,
            onDismiss = { editingLoreId = null },
            onConfirm = { _, n, c -> Store.saveLore(id, n, c); editingLoreId = null },
            onDelete = { Store.deleteLore(id); editingLoreId = null },
        )
    }
    if (generatorChooserOpen) {
        AlertDialog(
            onDismissRequest = { generatorChooserOpen = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("AI 构筑", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    LoreGenerator.entries.forEach { g ->
                        Surface(
                            onClick = { generatorChooserOpen = false; generatorInput = g },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(g.label, style = MaterialTheme.typography.titleMedium)
                                Text(g.description, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { generatorChooserOpen = false }) { Text("取消") } },
        )
    }
    generatorInput?.let { generator ->
        GeneratorInputDialog(
            generator = generator,
            onDismiss = { generatorInput = null },
            onConfirm = { option, extra ->
                generatorInput = null
                runLoreGenerator(generator, option, extra)
            },
        )
    }
    if (plannerOpen) {
        PlannerDialog(
            onDismiss = { plannerOpen = false },
            onConfirm = { count, extra ->
                plannerOpen = false
                runPlanner(count, extra)
            },
        )
    }

    when (val state = genState) {
        GenState.Hidden -> Unit
        is GenState.Loading -> LoadingDialog("AI 构筑", state.label, onCancel = ::dismissAi)
        is GenState.Failure -> AlertDialog(
            onDismissRequest = ::dismissAi,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("${state.title}失败", style = MaterialTheme.typography.titleLarge) },
            text = { Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { state.retry() }) { Text("重试") } },
            dismissButton = { TextButton(onClick = ::dismissAi) { Text("关闭") } },
        )
        is GenState.LorePreview -> AlertDialog(
            onDismissRequest = ::dismissAi,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "${state.generator.label} · ${state.entries.size} 条" + if (state.reviewed) " · 已协作审校" else "",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(Modifier.widthIn(min = 480.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    state.entries.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
                        Text("[${item.category.label}] ${item.name}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(item.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.entries.forEach { Store.addLore(bookId, it.category, it.name, it.content) }
                    genState = GenState.Hidden
                }) { Text("全部保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { lastRun() }) { Text("重试") }
                    TextButton(onClick = ::dismissAi) { Text("放弃") }
                }
            },
        )
        is GenState.PlanPreview -> AlertDialog(
            onDismissRequest = ::dismissAi,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("大纲方案 · ${state.nodes.size} 个阶段", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.widthIn(min = 480.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    state.nodes.forEachIndexed { i, (t, c) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
                        Text("${i + 1}. $t", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(c, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.appendOutline(bookId, state.nodes)
                    genState = GenState.Hidden
                }) { Text("追加保存") }
            },
            dismissButton = { TextButton(onClick = ::dismissAi) { Text("放弃") } },
        )
    }
}

@Composable
private fun ChaptersTab(data: AppData, bookId: Long, onOpenChapter: (Long) -> Unit) {
    var deletingId by remember { mutableStateOf<Long?>(null) }
    val chapters = data.chapters.filter { it.bookId == bookId }.sortedBy { it.sortOrder }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chapters.isEmpty()) item { EmptyState("还没有章节", "点击下方「新建章节」，写下第一章") }
        items(chapters, key = { it.id }) { chapter ->
            var menuOpen by remember { mutableStateOf(false) }
            InkCard(onClick = { onOpenChapter(chapter.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(chapter.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatWordCount(countWords(chapter.content)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("删除章节", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; deletingId = chapter.id },
                            )
                        }
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        val chapter = data.chapters.firstOrNull { it.id == id } ?: return@let
        ConfirmDialog(
            title = "删除「${chapter.title}」？",
            message = "${formatWordCount(countWords(chapter.content))}的内容将被删除，且无法恢复。",
            onConfirm = { Store.deleteChapter(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun OutlineTab(data: AppData, bookId: Long, onEdit: (Long) -> Unit) {
    var deletingId by remember { mutableStateOf<Long?>(null) }
    val nodes = data.outline.filter { it.bookId == bookId }.sortedBy { it.sortOrder }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (nodes.isEmpty()) item { EmptyState("还没有大纲", "手动添加剧情阶段，或用下方「AI 规划」一键排布") }
        items(nodes, key = { it.id }) { node ->
            val index = nodes.indexOfFirst { it.id == node.id }
            InkCard(onClick = { onEdit(node.id) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${node.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    IconButton(onClick = { if (index > 0) Store.swapOutline(node, nodes[index - 1]) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "上移", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (index < nodes.size - 1) Store.swapOutline(node, nodes[index + 1]) }, enabled = index < nodes.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, "下移", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deletingId = node.id }) {
                        Icon(Icons.Filled.MoreVert, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    deletingId?.let { id ->
        val node = data.outline.firstOrNull { it.id == id } ?: return@let
        ConfirmDialog(
            title = "删除「${node.title}」？",
            message = "该大纲阶段将被删除，且无法恢复。",
            onConfirm = { Store.deleteOutline(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun LoreTab(
    data: AppData,
    bookId: Long,
    filter: LoreCategory?,
    onFilter: (LoreCategory?) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val entries = data.lore.filter { it.bookId == bookId && (filter == null || it.category == filter) }
        .sortedByDescending { it.updatedAt }
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip("全部", filter == null) { onFilter(null) }
            LoreCategory.entries.forEach { c -> CategoryChip(c.label, filter == c) { onFilter(c) } }
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("暂无设定", "用下方「AI 构筑」一键生成，或手动新建")
                }
            }
            items(entries, key = { it.id }) { entry ->
                InkCard(onClick = { onEdit(entry.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                entry.category.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (entry.content.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            entry.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun LoreEditDialog(
    title: String,
    initialCategory: LoreCategory,
    initialName: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (LoreCategory, String, String) -> Unit,
    categoryEditable: Boolean = true,
    onDelete: (() -> Unit)? = null,
) {
    var category by remember { mutableStateOf(initialCategory) }
    var name by remember { mutableStateOf(initialName) }
    var content by remember { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.widthIn(min = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (categoryEditable) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoreCategory.entries.forEach { c -> CategoryChip(c.label, category == c) { category = c } }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("设定内容") },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(category, name.trim(), content) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun GeneratorInputDialog(
    generator: LoreGenerator,
    onDismiss: () -> Unit,
    onConfirm: (option: String, extra: String) -> Unit,
) {
    var option by remember { mutableStateOf(generator.options.firstOrNull() ?: "") }
    var extra by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(generator.label, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.widthIn(min = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(generator.description, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (generator.optionLabel != null) {
                    Text(generator.optionLabel, style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        generator.options.forEach { o -> CategoryChip(o, option == o) { option = o } }
                    }
                }
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("作者补充") },
                    placeholder = { Text(generator.extraHint) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(option, extra.trim()) }) { Text("开始生成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PlannerDialog(onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var count by remember { mutableStateOf(10) }
    var extra by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("AI 规划大纲", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.widthIn(min = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "基于世界观与已有大纲排布剧情阶段；已有大纲时自动往后续排",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { n -> CategoryChip("$n 个阶段", count == n) { count = n } }
                }
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("作者补充（可选）") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(count, extra.trim()) }) { Text("开始规划") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ============================== Editor ==============================

private sealed interface EditorAiState {
    data object Hidden : EditorAiState
    data class Loading(val action: EditorAction) : EditorAiState
    data class Success(val action: EditorAction, val text: String) : EditorAiState
    data class Failure(val action: EditorAction, val message: String) : EditorAiState
}

@OptIn(FlowPreview::class)
@Composable
private fun EditorScreen(data: AppData, bookId: Long, chapterId: Long, onBack: () -> Unit) {
    val chapter = data.chapters.firstOrNull { it.id == chapterId }
    if (chapter == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    var titleValue by remember(chapterId) { mutableStateOf(TextFieldValue(chapter.title)) }
    var contentValue by remember(chapterId) {
        mutableStateOf(TextFieldValue(chapter.content, TextRange(chapter.content.length)))
    }
    var dirty by remember(chapterId) { mutableStateOf(false) }
    var aiState by remember { mutableStateOf<EditorAiState>(EditorAiState.Hidden) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    var aiTargetRange by remember { mutableStateOf<TextRange?>(null) }
    var aiSelectionText by remember { mutableStateOf("") }
    var aiLastAction by remember { mutableStateOf<EditorAction?>(null) }
    var aiLastStyle by remember { mutableStateOf<StyleProfile?>(null) }
    var stylePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(chapterId) {
        snapshotFlow { titleValue.text to contentValue.text }
            .drop(1)
            .debounce(600)
            .collect { (t, c) ->
                Store.saveChapter(chapterId, t, c)
                dirty = false
            }
    }

    fun dismissAi() {
        aiJob?.cancel()
        aiState = EditorAiState.Hidden
    }

    fun runAi(action: EditorAction, style: StyleProfile? = null) {
        stylePickerOpen = false
        aiLastAction = action
        aiLastStyle = style
        aiTargetRange = contentValue.selection
        aiSelectionText = if (contentValue.selection.collapsed) "" else
            contentValue.text.substring(contentValue.selection.min, contentValue.selection.max)
        aiJob?.cancel()
        aiJob = scope.launch {
            val current = Store.data.value
            val primary = current.settings.primaryProfile()
            if (primary == null || primary.apiKey.isBlank()) {
                aiState = EditorAiState.Failure(action, NO_AI_MESSAGE)
                return@launch
            }
            aiState = EditorAiState.Loading(action)
            val result = runCatching {
                AiClient.generate(
                    primary.toAiConfig(),
                    EditorPrompts.buildSystem(current, bookId, style),
                    EditorPrompts.buildUser(action, titleValue.text, contentValue.text, aiSelectionText),
                )
            }
            aiState = result.fold(
                onSuccess = { EditorAiState.Success(action, it) },
                onFailure = { e -> EditorAiState.Failure(action, e.message ?: "请求失败") },
            )
        }
    }

    fun applyAiText(text: String) {
        val length = contentValue.text.length
        val range = aiTargetRange
        val start = (range?.min ?: length).coerceIn(0, length)
        val end = (range?.max ?: length).coerceIn(start, length)
        contentValue = TextFieldValue(
            contentValue.text.replaceRange(start, end, text),
            TextRange(start + text.length),
        )
        dirty = true
        dismissAi()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    Store.saveChapter(chapterId, titleValue.text, contentValue.text)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.onSurfaceVariant)
                }
                BasicTextField(
                    value = titleValue,
                    onValueChange = { if (it.text != titleValue.text) dirty = true; titleValue = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = colors.onBackground,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    singleLine = true,
                )
                Text(
                    formatWordCount(countWords(contentValue.text)) + if (dirty) " · 编辑中" else " · 已保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outline)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val fontSize = data.settings.editorFontSize
                BasicTextField(
                    value = contentValue,
                    onValueChange = { if (it.text != contentValue.text) dirty = true; contentValue = it },
                    modifier = Modifier
                        .widthIn(max = 860.dp)
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.8f).sp,
                        color = colors.onBackground,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                )
            }
        }
        BottomActions {
            val hasSelection = !contentValue.selection.collapsed
            PillButton("续写", Icons.Filled.AutoAwesome, primary = true) { runAi(EditorAction.CONTINUE) }
            PillButton("润色", null, primary = false, enabled = hasSelection) { runAi(EditorAction.POLISH) }
            PillButton("文风仿写", null, primary = false) { stylePickerOpen = true }
            PillButton("灵感", null, primary = false) { runAi(EditorAction.IDEA) }
        }
    }

    if (stylePickerOpen) {
        AlertDialog(
            onDismissRequest = { stylePickerOpen = false },
            containerColor = colors.surface,
            title = { Text("选择文风档案", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (data.styles.isEmpty()) {
                    Text(
                        "文风库还是空的。请先到左侧「文风库」上传小说文本创建文风档案。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        data.styles.forEach { style ->
                            Surface(
                                onClick = { runAi(EditorAction.MIMIC, style) },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                            ) {
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Text(style.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        style.analysis.replace('\n', ' ').take(60),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { stylePickerOpen = false }) { Text("取消") } },
        )
    }

    when (val state = aiState) {
        EditorAiState.Hidden -> Unit
        is EditorAiState.Loading -> LoadingDialog("AI ${state.action.label}", "正在生成，请稍候…", onCancel = ::dismissAi)
        is EditorAiState.Failure -> AlertDialog(
            onDismissRequest = ::dismissAi,
            containerColor = colors.surface,
            title = { Text("${state.action.label}失败", style = MaterialTheme.typography.titleLarge) },
            text = { Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { aiLastAction?.let { runAi(it, aiLastStyle) } }) { Text("重试") }
            },
            dismissButton = { TextButton(onClick = ::dismissAi) { Text("关闭") } },
        )
        is EditorAiState.Success -> AlertDialog(
            onDismissRequest = ::dismissAi,
            shape = RoundedCornerShape(18.dp),
            containerColor = colors.surface,
            title = { Text("${state.action.label}结果", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.widthIn(min = 480.dp).heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(state.text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            },
            confirmButton = {
                when (state.action) {
                    EditorAction.IDEA -> TextButton(onClick = ::dismissAi) { Text("好的") }
                    EditorAction.POLISH -> TextButton(onClick = { applyAiText(state.text) }) { Text("替换选中") }
                    EditorAction.MIMIC -> TextButton(onClick = { applyAiText(state.text) }) {
                        Text(if (aiSelectionText.isNotBlank()) "替换选中" else "插入正文")
                    }
                    EditorAction.CONTINUE -> TextButton(onClick = { applyAiText(state.text) }) { Text("插入正文") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { aiLastAction?.let { runAi(it, aiLastStyle) } }) { Text("重试") }
                    TextButton(onClick = ::dismissAi) { Text("关闭") }
                }
            },
        )
    }
}

// ============================== Styles ==============================

private fun pickTextFile(): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择 TXT 文件", java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

private fun decodeSmart(bytes: ByteArray): String {
    val utf8 = String(bytes, Charsets.UTF_8)
    val bad = utf8.count { it == '�' }
    if (bad == 0 || utf8.isEmpty() || bad.toFloat() / utf8.length < 0.02f) return utf8
    return runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
}

private sealed interface StyleAiState {
    data object Hidden : StyleAiState
    data object Loading : StyleAiState
    data class Preview(val name: String, val analysis: String) : StyleAiState
    data class Failure(val message: String) : StyleAiState
}

@Composable
private fun StylesScreen(data: AppData) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var viewingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var aiState by remember { mutableStateOf<StyleAiState>(StyleAiState.Hidden) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    var lastName by remember { mutableStateOf("") }
    var lastSample by remember { mutableStateOf("") }

    fun dismissAi() {
        aiJob?.cancel()
        aiState = StyleAiState.Hidden
    }

    fun analyze(name: String, sample: String) {
        lastName = name
        lastSample = sample
        aiJob?.cancel()
        aiJob = scope.launch {
            val current = Store.data.value
            val primary = current.settings.primaryProfile()
            if (primary == null || primary.apiKey.isBlank()) {
                aiState = StyleAiState.Failure(NO_AI_MESSAGE)
                return@launch
            }
            aiState = StyleAiState.Loading
            val result = runCatching {
                AiClient.generate(primary.toAiConfig(), StylePrompts.ANALYSIS_SYSTEM, StylePrompts.buildAnalysisPrompt(sample.take(8000)), 4096)
            }
            aiState = result.fold(
                onSuccess = { StyleAiState.Preview(name, it.trim()) },
                onFailure = { e -> StyleAiState.Failure(e.message ?: "请求失败") },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("文风库", style = MaterialTheme.typography.headlineMedium)
            Text(
                "上传小说文本，AI 提炼文风指南，写作时一键仿写",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (data.styles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState("还没有文风档案", "粘贴或导入一段小说文本（建议 2000 字以上）")
                    }
                }
                items(data.styles.sortedByDescending { it.createdAt }, key = { it.id }) { style ->
                    var menuOpen by remember { mutableStateOf(false) }
                    InkCard(onClick = { viewingId = style.id }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(style.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    style.analysis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("查看指南") }, onClick = { menuOpen = false; viewingId = style.id })
                                    DropdownMenuItem(
                                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; deletingId = style.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        BottomActions {
            PillButton("新建文风", Icons.Filled.AutoAwesome, primary = true) { creating = true }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var sample by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("新建文风档案", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.widthIn(min = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("文风名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = sample,
                        onValueChange = { sample = it.take(20000) },
                        label = { Text("小说样本（${sample.length} 字）") },
                        placeholder = { Text("粘贴一段有代表性的原文，建议 2000 字以上") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        pickTextFile()?.let { f ->
                            runCatching { sample = decodeSmart(f.readBytes()).take(20000) }
                        }
                    }) { Text("导入 TXT 文件（支持 UTF-8 / GBK）") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { creating = false; analyze(name.trim(), sample.trim()) },
                    enabled = name.isNotBlank() && sample.trim().length >= 200,
                ) { Text("开始分析") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } },
        )
    }

    viewingId?.let { id ->
        val style = data.styles.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { viewingId = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(style.name, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.widthIn(min = 520.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(style.analysis, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { viewingId = null }) { Text("关闭") } },
        )
    }

    deletingId?.let { id ->
        val style = data.styles.firstOrNull { it.id == id } ?: return@let
        ConfirmDialog(
            title = "删除「${style.name}」？",
            message = "该文风档案将被删除，且无法恢复。",
            onConfirm = { Store.deleteStyle(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }

    when (val state = aiState) {
        StyleAiState.Hidden -> Unit
        StyleAiState.Loading -> LoadingDialog("文风分析", "文风 Agent 正在研读文本…", onCancel = ::dismissAi)
        is StyleAiState.Failure -> AlertDialog(
            onDismissRequest = ::dismissAi,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("分析失败", style = MaterialTheme.typography.titleLarge) },
            text = { Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { analyze(lastName, lastSample) }) { Text("重试") } },
            dismissButton = { TextButton(onClick = ::dismissAi) { Text("关闭") } },
        )
        is StyleAiState.Preview -> AlertDialog(
            onDismissRequest = ::dismissAi,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("文风指南 · ${state.name}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.widthIn(min = 520.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(state.analysis, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.addStyle(state.name, state.analysis)
                    aiState = StyleAiState.Hidden
                }) { Text("保存到文风库") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { analyze(lastName, lastSample) }) { Text("重试") }
                    TextButton(onClick = ::dismissAi) { Text("放弃") }
                }
            },
        )
    }
}

// ============================== Settings ==============================

@Composable
private fun SettingsScreen(data: AppData) {
    var editingProfile by remember { mutableStateOf<AiProfile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    val settings = data.settings

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        Text("AI 服务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InkCard {
            Text(
                "支持 Anthropic 协议与 OpenAI 兼容协议，Base URL 可填官方地址、中转站或 API 代理。可添加多个服务用于多 AI 协作。密钥仅保存在本机。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            settings.profiles.forEach { profile ->
                Surface(
                    onClick = { editingProfile = profile; editingIsNew = false },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${profile.provider.label.substringBefore("（")} · ${profile.effectiveModel()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (profile.id == settings.primaryAiId) Badge("主笔")
                        if (profile.id == settings.reviewerAiId) {
                            Spacer(Modifier.width(4.dp))
                            Badge("审校")
                        }
                    }
                }
            }
            TextButton(onClick = {
                editingProfile = AiProfile(id = Store.newId(), name = "", provider = AiProvider.ANTHROPIC)
                editingIsNew = true
            }) { Text("＋ 添加 AI 服务") }
        }

        Text("AI 分工", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InkCard {
            Text("主笔模型", style = MaterialTheme.typography.labelLarge)
            settings.profiles.forEach { p ->
                RadioRow(p.name, p.id == settings.primaryAiId) {
                    Store.updateSettings { it.copy(primaryAiId = p.id) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
            Text("审校模型（多 AI 协作）", style = MaterialTheme.typography.labelLarge)
            RadioRow("不启用", settings.reviewerAiId == 0L) {
                Store.updateSettings { it.copy(reviewerAiId = 0L) }
            }
            settings.profiles.forEach { p ->
                RadioRow(p.name, p.id == settings.reviewerAiId) {
                    Store.updateSettings { it.copy(reviewerAiId = p.id) }
                }
            }
        }

        Text("编辑器", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正文字号", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${settings.editorFontSize} sp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = settings.editorFontSize.toFloat(),
                onValueChange = { v -> Store.updateSettings { it.copy(editorFontSize = v.toInt()) } },
                valueRange = 13f..24f,
                steps = 10,
            )
        }

        Text("关于", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InkCard {
            Text("墨蓝写作 桌面版", style = MaterialTheme.typography.titleLarge)
            Text(
                "数据保存在 ~/.inkblue-writer/data.json，与安卓版功能同源：大纲规划、世界观构筑、AI 写作、文风仿写与多 AI 协作。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingProfile?.let { profile ->
        ProfileEditDialog(
            initial = profile,
            isNew = editingIsNew,
            onDismiss = { editingProfile = null },
            onSave = { Store.saveProfile(it); editingProfile = null },
            onDelete = if (editingIsNew) null else ({ Store.deleteProfile(profile.id); editingProfile = null }),
        )
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun ProfileEditDialog(
    initial: AiProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (AiProfile) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var provider by remember { mutableStateOf(initial.provider) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (isNew) "添加 AI 服务" else "编辑 AI 服务", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.widthIn(min = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AiProvider.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        RadioButton(selected = provider == p, onClick = { provider = p })
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（留空使用默认）") },
                    placeholder = { Text(provider.defaultBaseUrl) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型（留空使用默认）") },
                    placeholder = { Text(provider.defaultModel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { provider.label.substringBefore("（") },
                            provider = provider,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                        )
                    )
                },
                enabled = apiKey.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
