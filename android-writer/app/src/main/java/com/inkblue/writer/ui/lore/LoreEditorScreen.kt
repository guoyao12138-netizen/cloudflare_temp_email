package com.inkblue.writer.ui.lore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.AppSettings
import com.inkblue.writer.data.LoreEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@Composable
fun LoreEditorScreen(
    entryId: Long,
    onBack: () -> Unit,
    vm: LoreEditorViewModel = viewModel(factory = LoreEditorViewModel.factory(entryId)),
) {
    val app = LocalContext.current.applicationContext as InkApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val entry by vm.entry.collectAsStateWithLifecycle()

    val loaded = entry
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    key(loaded.id) {
        LoreEditorContent(entry = loaded, vm = vm, settings = settings, onBack = onBack)
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
private fun LoreEditorContent(
    entry: LoreEntry,
    vm: LoreEditorViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var content by remember { mutableStateOf(entry.content) }
    val saved by vm.saved.collectAsStateWithLifecycle()
    val revisionState by vm.revisionState.collectAsStateWithLifecycle()

    var revisionSheetOpen by remember { mutableStateOf(false) }
    var customDialogOpen by remember { mutableStateOf(false) }
    var lastRevision by remember { mutableStateOf<LoreRevision?>(null) }
    var lastInstruction by remember { mutableStateOf("") }

    fun startRevision(revision: LoreRevision, instruction: String) {
        revisionSheetOpen = false
        customDialogOpen = false
        lastRevision = revision
        lastInstruction = instruction
        vm.runRevision(revision, instruction, name, content)
    }

    fun retryRevision() {
        lastRevision?.let { vm.runRevision(it, lastInstruction, name, content) }
    }

    // Debounced auto-save while typing.
    LaunchedEffect(Unit) {
        snapshotFlow { name to content }
            .drop(1)
            .debounce(600)
            .collectLatest { (n, c) -> vm.save(n, c) }
    }

    // Final save when leaving the editor.
    DisposableEffect(Unit) {
        onDispose { vm.save(name, content) }
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
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.secondaryContainer,
            ) {
                Text(
                    entry.loreCategory.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                if (saved) "  已保存" else "  编辑中",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        BasicTextField(
            value = name,
            onValueChange = {
                if (it != name) vm.markDirty()
                name = it
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
                    if (name.isEmpty()) {
                        Text(
                            "名称",
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

        BasicTextField(
            value = content,
            onValueChange = {
                if (it != content) vm.markDirty()
                content = it
            },
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
                    if (content.isEmpty()) {
                        Text(
                            "写下这条设定的细节：外貌、性格、来历、规则……",
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.8f).sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )

        // AI revision entry, bottom-center within thumb reach.
        Surface(color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                HorizontalDivider(color = colors.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { revisionSheetOpen = true }) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "AI 修订",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("AI 修订", color = colors.primary)
                    }
                }
            }
        }
    }

    if (revisionSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { revisionSheetOpen = false },
            containerColor = colors.surface,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                Text(
                    "AI 修订",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )
                Text(
                    "修订以全书设定为依据，结果可预览后替换",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 12.dp))
                LoreRevision.entries.forEach { revision ->
                    Surface(
                        onClick = {
                            if (revision == LoreRevision.CUSTOM) {
                                revisionSheetOpen = false
                                customDialogOpen = true
                            } else {
                                startRevision(revision, "")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = colors.surface,
                    ) {
                        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            Text(
                                revision.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                revision.description,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (customDialogOpen) {
        var instruction by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customDialogOpen = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surface,
            title = { Text("自定义修订", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("修订指令") },
                    placeholder = { Text("例如：把这个势力改写得更神秘，并加入一段与主角宗门的旧怨") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { startRevision(LoreRevision.CUSTOM, instruction.trim()) },
                    enabled = instruction.isNotBlank(),
                ) { Text("开始修订") }
            },
            dismissButton = {
                TextButton(onClick = { customDialogOpen = false }) { Text("取消") }
            },
        )
    }

    when (val state = revisionState) {
        RevisionState.Hidden -> Unit

        is RevisionState.Loading -> AlertDialog(
            onDismissRequest = { vm.dismissRevision() },
            containerColor = colors.surface,
            title = { Text("AI ${state.revision.label}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "正在修订，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissRevision() }) { Text("取消") }
            },
        )

        is RevisionState.Success -> AlertDialog(
            onDismissRequest = { vm.dismissRevision() },
            containerColor = colors.surface,
            title = { Text("${state.revision.label}结果", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
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
                TextButton(
                    onClick = {
                        content = state.text
                        vm.markDirty()
                        vm.dismissRevision()
                    },
                ) { Text("替换内容") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { retryRevision() }) { Text("重试") }
                    TextButton(onClick = { vm.dismissRevision() }) { Text("关闭") }
                }
            },
        )

        is RevisionState.Failure -> AlertDialog(
            onDismissRequest = { vm.dismissRevision() },
            containerColor = colors.surface,
            title = { Text("${state.revision.label}失败", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { retryRevision() }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissRevision() }) { Text("关闭") }
            },
        )
    }
}
