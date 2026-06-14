package com.inkblue.writer.ui.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
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
import com.inkblue.writer.data.Book
import com.inkblue.writer.data.BookWithStats
import com.inkblue.writer.ui.components.BarPill
import com.inkblue.writer.ui.components.BottomPillBar
import com.inkblue.writer.ui.components.ConfirmDialog
import com.inkblue.writer.ui.components.EmptyState
import com.inkblue.writer.ui.components.InkCard
import com.inkblue.writer.ui.components.TextInputDialog
import com.inkblue.writer.util.formatTime
import com.inkblue.writer.util.formatWordCount

@Composable
fun BookshelfScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStyles: () -> Unit,
    vm: BookshelfViewModel = viewModel(factory = BookshelfViewModel.Factory),
) {
    val books by vm.books.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Book?>(null) }
    var deleting by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            BottomPillBar {
                BarPill("新建作品", Icons.Filled.Add, primary = true, onClick = { showCreate = true })
                BarPill("文风库", Icons.Filled.Brush, onClick = onOpenStyles)
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "书架",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            if (books.isEmpty()) "落笔成章，始于此页" else "共 ${books.size} 部作品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "还没有作品",
                        hint = "点击下方「新建作品」，开始你的第一部小说",
                    )
                }
            }

            items(books, key = { it.book.id }) { item ->
                BookCard(
                    item = item,
                    onClick = { onOpenBook(item.book.id) },
                    onEdit = { editing = item.book },
                    onDelete = { deleting = item.book },
                )
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "新建作品",
            primaryLabel = "书名",
            secondaryLabel = "简介（可选）",
            confirmText = "创建",
            onDismiss = { showCreate = false },
            onConfirm = { title, desc ->
                vm.createBook(title, desc)
                showCreate = false
            },
        )
    }

    editing?.let { book ->
        TextInputDialog(
            title = "编辑作品信息",
            primaryLabel = "书名",
            initialPrimary = book.title,
            secondaryLabel = "简介（可选）",
            initialSecondary = book.description,
            confirmText = "保存",
            onDismiss = { editing = null },
            onConfirm = { title, desc ->
                vm.updateBook(book, title, desc)
                editing = null
            },
        )
    }

    deleting?.let { book ->
        ConfirmDialog(
            title = "删除《${book.title}》？",
            message = "该作品下的所有章节将一并删除，且无法恢复。",
            onConfirm = {
                vm.deleteBook(book)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun BookCard(
    item: BookWithStats,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.book.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.book.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑信息") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除作品", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${item.chapterCount} 章 · ${formatWordCount(item.totalWords)} · 更新于 ${formatTime(item.book.updatedAt)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
