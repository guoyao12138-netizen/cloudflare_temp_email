package com.inkblue.writer.ui.lore

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkblue.writer.ai.GeneratedLore
import com.inkblue.writer.ai.LoreGenerator
import com.inkblue.writer.data.LoreCategory
import com.inkblue.writer.data.LoreEntry
import com.inkblue.writer.ui.components.ConfirmDialog
import com.inkblue.writer.ui.components.EmptyState
import com.inkblue.writer.ui.components.InkCard
import com.inkblue.writer.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoreListScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    vm: LoreListViewModel = viewModel(factory = LoreListViewModel.factory(bookId)),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf<LoreCategory?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LoreEntry?>(null) }

    // AI generator flow state.
    var aiSheetOpen by remember { mutableStateOf(false) }
    var generatorInput by remember { mutableStateOf<LoreGenerator?>(null) }
    var lastOption by remember { mutableStateOf("") }
    var lastExtra by remember { mutableStateOf("") }

    val visible = entries.filter { filter == null || it.loreCategory == filter }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
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
                            "世界观",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "《${book?.title ?: ""}》 · ${entries.size} 条设定",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { aiSheetOpen = true }) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "AI 构筑",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip("全部", filter == null) { filter = null }
                    LoreCategory.entries.forEach { category ->
                        CategoryChip(category.label, filter == category) { filter = category }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("新建设定")
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = if (entries.isEmpty()) "还没有设定" else "该分类下暂无设定",
                        hint = "人物、地点、物品、势力……\n把世界的骨架搭起来",
                    )
                }
            }
            items(visible, key = { it.id }) { entry ->
                LoreRow(
                    entry = entry,
                    onClick = { onOpenEntry(entry.id) },
                    onDelete = { deleting = entry },
                )
            }
        }
    }

    if (showCreate) {
        LoreCreateDialog(
            onDismiss = { showCreate = false },
            onConfirm = { category, name ->
                showCreate = false
                vm.addEntry(category, name) { id -> onOpenEntry(id) }
            },
            initialCategory = filter ?: LoreCategory.CHARACTER,
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = "删除「${entry.name}」？",
            message = "该条${entry.loreCategory.label}设定将被删除，且无法恢复。",
            onConfirm = {
                vm.deleteEntry(entry)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (aiSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { aiSheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "AI 构筑",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "生成结果会先预览，确认后才会保存到世界观",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LoreGenerator.entries.forEach { generator ->
                    Surface(
                        onClick = {
                            aiSheetOpen = false
                            generatorInput = generator
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            Text(
                                generator.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                generator.description,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    generatorInput?.let { generator ->
        GeneratorInputDialog(
            generator = generator,
            onDismiss = { generatorInput = null },
            onConfirm = { option, extra ->
                generatorInput = null
                lastOption = option
                lastExtra = extra
                vm.runGenerator(generator, option, extra)
            },
        )
    }

    when (val state = aiState) {
        LoreAiState.Hidden -> Unit

        is LoreAiState.Loading -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("AI 构筑 · ${state.generator.label}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        state.stage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("取消") }
            },
        )

        is LoreAiState.Preview -> GeneratedPreviewDialog(
            generator = state.generator,
            generated = state.entries,
            reviewed = state.reviewed,
            onSave = { vm.saveGenerated(state.entries) },
            onRetry = { vm.runGenerator(state.generator, lastOption, lastExtra) },
            onDismiss = { vm.dismissAi() },
        )

        is LoreAiState.Failure -> AlertDialog(
            onDismissRequest = { vm.dismissAi() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("${state.generator.label}生成失败", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.runGenerator(state.generator, lastOption, lastExtra) }) {
                    Text("重试")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAi() }) { Text("关闭") }
            },
        )
    }
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
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(generator.label, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    generator.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (generator.optionLabel != null) {
                    Text(
                        generator.optionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        generator.options.forEach { o ->
                            FilterChip(
                                selected = option == o,
                                onClick = { option = o },
                                label = { Text(o) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
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
        confirmButton = {
            TextButton(onClick = { onConfirm(option, extra.trim()) }) { Text("开始生成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun GeneratedPreviewDialog(
    generator: LoreGenerator,
    generated: List<GeneratedLore>,
    reviewed: Boolean,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "${generator.label} · ${generated.size} 条" + if (reviewed) " · 已协作审校" else "",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                generated.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                item.category.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("全部保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onDismiss) { Text("放弃") }
            }
        },
    )
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
private fun LoreRow(
    entry: LoreEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InkCard(onClick = onClick, contentPadding = 14) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            entry.loreCategory.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                Spacer(Modifier.height(6.dp))
                Text(
                    formatTime(entry.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "设定菜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("删除设定", color = MaterialTheme.colorScheme.error) },
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
private fun LoreCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (LoreCategory, String) -> Unit,
    initialCategory: LoreCategory,
) {
    var category by remember { mutableStateOf(initialCategory) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("新建设定", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoreCategory.entries.forEach { c ->
                        CategoryChip(c.label, category == c) { category = c }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：林惊羽 / 青云山 / 诛仙剑）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(category, name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("创建并编辑") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
