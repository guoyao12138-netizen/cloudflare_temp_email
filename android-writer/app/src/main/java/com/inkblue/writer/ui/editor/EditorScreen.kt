package com.inkblue.writer.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@OptIn(FlowPreview::class)
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

    // Undo / redo: snapshots grouped by a 1s typing window.
    val undoStack = remember { ArrayDeque<TextFieldValue>() }
    val redoStack = remember { ArrayDeque<TextFieldValue>() }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var lastSnapshotAt by remember { mutableLongStateOf(0L) }

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
    ) {
        // Slim top bar: back · word count + save state · undo / redo
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
            modifier = Modifier.padding(horizontal = 24.dp),
            color = colors.outline,
        )

        // Body — distraction-free writing area.
        BasicTextField(
            value = contentValue,
            onValueChange = ::onContentChange,
            modifier = Modifier
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
