package com.inkblue.writer.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.AppSettings
import com.inkblue.writer.data.Chapter
import com.inkblue.writer.data.StyleProfile
import com.inkblue.writer.util.countWords
import com.inkblue.writer.util.formatWordCount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/** Full-width indent inserted at the start of each new paragraph (纯纯写作 style). */
private const val INDENT = "　　"

/** Quick punctuation bar: symbol to cursor offset after insertion. */
private val SYMBOLS = listOf(
    "「」" to 1, "『』" to 1, "“”" to 1, "（）" to 1,
    "——" to 2, "……" to 2,
    "，" to 1, "。" to 1, "？" to 1, "！" to 1, "：" to 1, "；" to 1, "、" to 1,
)

@Composable
fun EditorScreen(
    chapterId: Long,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(chapterId)),
) {
    val app = LocalContext.current.applicationContext as InkApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val chapter by vm.chapter.collectAsStateWithLifecycle()

    val loaded = chapter
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    key(loaded.id) {
        EditorContent(chapter = loaded, vm = vm, settings = settings, onBack = onBack)
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    chapter: Chapter,
    vm: EditorViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    var titleValue by remember { mutableStateOf(TextFieldValue(chapter.title)) }
    var contentValue by remember {
        mutableStateOf(TextFieldValue(chapter.content, TextRange(chapter.content.length)))
    }
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()

    // Undo / redo: snapshots grouped by a 1s typing window.
    val undoStack = remember { ArrayDeque<TextFieldValue>() }
    val redoStack = remember { ArrayDeque<TextFieldValue>() }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var lastSnapshotAt by remember { mutableLongStateOf(0L) }

    // AI assistant state owned by the screen.
    var aiSheetOpen by remember { mutableStateOf(false) }
    var aiTargetRange by remember { mutableStateOf<TextRange?>(null) }
    var aiSelectionText by remember { mutableStateOf("") }
    var aiLastAction by remember { mutableStateOf<AiAction?>(null) }
    var aiLastStyle by remember { mutableStateOf<StyleProfile?>(null) }
    var stylePickerOpen by remember { mutableStateOf(false) }
    val styles by vm.styles.collectAsStateWithLifecycle()

    fun pushUndo(snapshot: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (undoStack.isEmpty() || now - lastSnapshotAt > 1000) {
            undoStack.addLast(snapshot)
            if (undoStack.size > 200) undoStack.removeFirst()
            lastSnapshotAt = now
        }
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(contentValue)
        contentValue = prev
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        lastSnapshotAt = 0L
        vm.markDirty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(contentValue)
        contentValue = next
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        lastSnapshotAt = 0L
        vm.markDirty()
    }

    fun onContentChange(new: TextFieldValue) {
        if (new.text != contentValue.text) {
            pushUndo(contentValue)
            contentValue = applyAutoIndent(contentValue, new, settings.autoIndent)
            vm.markDirty()
        } else {
            contentValue = new
        }
    }

    fun insertSymbol(symbol: String, cursorOffset: Int) {
        pushUndo(contentValue)
        val start = contentValue.selection.min
        val end = contentValue.selection.max
        val text = contentValue.text.replaceRange(start, end, symbol)
        contentValue = TextFieldValue(text, TextRange(start + cursorOffset))
        vm.markDirty()
    }

    fun startAi(action: AiAction, style: StyleProfile? = null) {
        aiSheetOpen = false
        stylePickerOpen = false
        aiLastAction = action
        aiLastStyle = style
        aiTargetRange = contentValue.selection
        aiSelectionText = if (contentValue.selection.collapsed) {
            ""
        } else {
            contentValue.text.substring(contentValue.selection.min, contentValue.selection.max)
        }
        vm.runAi(action, titleValue.text, contentValue.text, aiSelectionText, style)
    }

    fun retryAi() {
        aiLastAction?.let { action ->
            vm.runAi(action, titleValue.text, contentValue.text, aiSelectionText, aiLastStyle)
        }
    }

    /** Insert at the captured cursor, or replace the captured selection. */
    fun applyAiText(text: String) {
        pushUndo(contentValue)
        lastSnapshotAt = 0L
        val length = contentValue.text.length
        val range = aiTargetRange
        val start = (range?.min ?: length).coerceIn(0, length)
        val end = (range?.max ?: length).coerceIn(start, length)
        val newText = contentValue.text.replaceRange(start, end, text)
        contentValue = TextFieldValue(newText, TextRange(start + text.length))
        canUndo = true
        vm.markDirty()
        vm.dismissAi()
    }

    // Debounced auto-save while typing.
    LaunchedEffect(Unit) {
        snapshotFlow { titleValue.text to contentValue.text }
            .drop(1)
            .debounce(600)
            .collectLatest { (title, content) -> vm.save(title, content) }
    }

    // Final save when leaving the editor.
    DisposableEffect(Unit) {
        onDispose { vm.save(titleValue.text, contentValue.text) }
    }

    val colors = MaterialTheme.colorScheme
    val fontSize = settings.editorFontSize

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Slim top bar: back · word count + save state · AI · undo / redo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.onSurfaceVariant,
                )
            }
            Text(
                buildString {
                    append(formatWordCount(countWords(contentValue.text)))
                    append(if (saveState == SaveState.SAVED) " · 已保存" else " · 编辑中")
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { aiSheetOpen = true }) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "AI 写作",
                    tint = colors.primary,
                )
            }
            IconButton(onClick = ::undo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    tint = if (canUndo) colors.onSurfaceVariant else colors.outline,
                )
            }
            IconButton(onClick = ::redo, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "重做",
                    tint = if (canRedo) colors.onSurfaceVariant else colors.outline,
                )
            }
        }

        // Chapter title — serif, like a Claude heading.
        BasicTextField(
            value = titleValue,
            onValueChange = {
                if (it.text != titleValue.text) vm.markDirty()
                titleValue = it
            },
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = colors.onBackground,
            ),
            cursorBrush = SolidColor(colors.primary),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (titleValue.text.isEmpty()) {
                        Text(
                            "章节标题",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )

        HorizontalDivider(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = colors.outline,
        )

        // Body — distraction-free writing area.
        BasicTextField(
            value = contentValue,
            onValueChange = ::onContentChange,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            textStyle = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.8f).sp,
                color = colors.onBackground,
            ),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Box {
                    if (contentValue.text.isEmpty()) {
                        Text(
                            "开始书写你的故事……",
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.8f).sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )

        // Quick punctuation bar above the keyboard.
        Surface(color = colors.surface) {
            Column {
                HorizontalDivider(color = colors.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SYMBOLS.forEach { (symbol, offset) ->
                        TextButton(onClick = { insertSymbol(symbol, offset) }) {
                            Text(
                                symbol,
                                fontSize = 16.sp,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        }
    }

    if (aiSheetOpen) {
        val hasSelection = !contentValue.selection.collapsed
        ModalBottomSheet(
            onDismissRequest = { aiSheetOpen = false },
            containerColor = colors.surface,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                Text(
                    "AI 写作",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                AiAction.entries.forEach { action ->
                    val enabled = action != AiAction.POLISH || hasSelection
                    AiActionRow(
                        action = action,
                        enabled = enabled,
                        hint = if (!enabled) "先在正文中选中要润色的文字" else action.description,
                        onClick = {
                            if (action == AiAction.MIMIC) {
                                aiSheetOpen = false
                                stylePickerOpen = true
                            } else {
                                startAi(action)
                            }
                        },
                    )
                }
            }
        }
    }

    if (stylePickerOpen) {
        AlertDialog(
            onDismissRequest = { stylePickerOpen = false },
            containerColor = colors.surface,
            title = { Text("选择文风档案", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (styles.isEmpty()) {
                    Text(
                        "文风库还是空的。请回到书架页，点击顶栏的画笔图标进入「文风库」，上传小说文本创建文风档案。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        styles.forEach { style ->
                            Surface(
                                onClick = { startAi(AiAction.MIMIC, style) },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                            ) {
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        style.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.onSurface,
                                    )
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
            confirmButton = {
                TextButton(onClick = { stylePickerOpen = false }) { Text("取消") }
            },
        )
    }

    when (val state = aiState) {
        AiUiState.Hidden -> Unit

        is AiUiState.Loading -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = colors.surface,
            title = { Text("AI ${state.action.label}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "正在生成，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("取消") }
            },
        )

        is AiUiState.Success -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = colors.surface,
            title = { Text("${state.action.label}结果", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        state.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                }
            },
            confirmButton = {
                when (state.action) {
                    AiAction.CONTINUE -> TextButton(onClick = { applyAiText(state.text) }) {
                        Text("插入正文")
                    }
                    AiAction.POLISH -> TextButton(onClick = { applyAiText(state.text) }) {
                        Text("替换选中")
                    }
                    AiAction.MIMIC -> TextButton(onClick = { applyAiText(state.text) }) {
                        Text(if (aiSelectionText.isNotBlank()) "替换选中" else "插入正文")
                    }
                    AiAction.IDEA -> TextButton(onClick = { vm.dismissAi() }) {
                        Text("好的")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { retryAi() }) { Text("重试") }
                    TextButton(onClick = { vm.dismissAi() }) { Text("关闭") }
                }
            },
        )

        is AiUiState.Failure -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = colors.surface,
            title = { Text("${state.action.label}失败", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { retryAi() }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun AiActionRow(
    action: AiAction,
    enabled: Boolean,
    hint: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                action.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) colors.onSurfaceVariant else colors.outline,
            )
        }
    }
}

/**
 * 纯纯写作 signature behavior: pressing Enter starts the new paragraph with a
 * two-full-width-space indent automatically.
 */
private fun applyAutoIndent(
    old: TextFieldValue,
    new: TextFieldValue,
    enabled: Boolean,
): TextFieldValue {
    if (!enabled) return new
    val cursor = new.selection.start
    val isSingleNewline = new.text.length == old.text.length + 1 &&
        cursor in 1..new.text.length &&
        new.text[cursor - 1] == '\n'
    if (!isSingleNewline) return new
    val indented = StringBuilder(new.text).insert(cursor, INDENT).toString()
    return TextFieldValue(indented, TextRange(cursor + INDENT.length))
}
