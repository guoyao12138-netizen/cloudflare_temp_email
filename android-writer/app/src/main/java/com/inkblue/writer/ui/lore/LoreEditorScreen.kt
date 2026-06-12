package com.inkblue.writer.ui.lore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@OptIn(FlowPreview::class)
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
            modifier = Modifier.padding(horizontal = 24.dp),
            color = colors.outline,
        )

        BasicTextField(
            value = content,
            onValueChange = {
                if (it != content) vm.markDirty()
                content = it
            },
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
    }
}
