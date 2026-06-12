package com.inkblue.writer.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.inkblue.writer.data.Chapter
import com.inkblue.writer.ui.components.ConfirmDialog
import com.inkblue.writer.ui.components.EmptyState
import com.inkblue.writer.ui.components.InkCard
import com.inkblue.writer.ui.components.TextInputDialog
import com.inkblue.writer.util.formatTime
import com.inkblue.writer.util.formatWordCount

@Composable
fun ChapterListScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit,
    vm: ChapterListViewModel = viewModel(factory = ChapterListViewModel.factory(bookId)),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var bookMenuOpen by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf(false) }
    var deletingBook by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Chapter?>(null) }
    var deleting by remember { mutableStateOf<Chapter?>(null) }

    val totalWords = chapters.sumOf { it.wordCount }

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
                        book?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${chapters.size} 章 · ${formatWordCount(totalWords)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { bookMenuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "作品菜单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = bookMenuOpen, onDismissRequest = { bookMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑作品信息") },
                            onClick = {
                                bookMenuOpen = false
                                editingBook = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除作品", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                bookMenuOpen = false
                                deletingBook = true
                            },
                        )
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
                Text("新建章节")
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
            if (chapters.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有章节",
                        hint = "点击「新建章节」，写下第一章",
                    )
                }
            }
            items(chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    onClick = { onOpenChapter(chapter.id) },
                    onRename = { renaming = chapter },
                    onDelete = { deleting = chapter },
                )
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "新建章节",
            primaryLabel = "章节名",
            initialPrimary = "第${chapters.size + 1}章 ",
            confirmText = "创建并开始写作",
            onDismiss = { showCreate = false },
            onConfirm = { title, _ ->
                showCreate = false
                vm.addChapter(title) { id -> onOpenChapter(id) }
            },
        )
    }

    renaming?.let { chapter ->
        TextInputDialog(
            title = "重命名章节",
            primaryLabel = "章节名",
            initialPrimary = chapter.title,
            confirmText = "保存",
            onDismiss = { renaming = null },
            onConfirm = { title, _ ->
                vm.renameChapter(chapter, title)
                renaming = null
            },
        )
    }

    deleting?.let { chapter ->
        ConfirmDialog(
            title = "删除「${chapter.title}」？",
            message = "${formatWordCount(chapter.wordCount)}的内容将被删除，且无法恢复。",
            onConfirm = {
                vm.deleteChapter(chapter)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (editingBook) {
        book?.let { b ->
            TextInputDialog(
                title = "编辑作品信息",
                primaryLabel = "书名",
                initialPrimary = b.title,
                secondaryLabel = "简介（可选）",
                initialSecondary = b.description,
                confirmText = "保存",
                onDismiss = { editingBook = false },
                onConfirm = { title, desc ->
                    vm.updateBook(b, title, desc)
                    editingBook = false
                },
            )
        }
    }

    if (deletingBook) {
        book?.let { b ->
            ConfirmDialog(
                title = "删除《${b.title}》？",
                message = "该作品下的所有章节将一并删除，且无法恢复。",
                onConfirm = {
                    deletingBook = false
                    vm.deleteBook(b) { onBack() }
                },
                onDismiss = { deletingBook = false },
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InkCard(onClick = onClick, contentPadding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatWordCount(chapter.wordCount)} · ${formatTime(chapter.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "章节菜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除章节", color = MaterialTheme.colorScheme.error) },
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
