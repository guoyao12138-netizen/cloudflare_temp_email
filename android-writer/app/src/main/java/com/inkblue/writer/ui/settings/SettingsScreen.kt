package com.inkblue.writer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.AiProvider
import com.inkblue.writer.data.AppSettings
import com.inkblue.writer.data.ThemeMode
import com.inkblue.writer.ui.components.InkCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as InkApp
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var fontSizeDraft by remember { mutableFloatStateOf(settings.editorFontSize.toFloat()) }
    LaunchedEffect(settings.editorFontSize) {
        fontSizeDraft = settings.editorFontSize.toFloat()
    }

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
                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel("外观")
            InkCard {
                ThemeModeRow("跟随系统", ThemeMode.SYSTEM, settings.themeMode) {
                    scope.launch { app.settings.setThemeMode(it) }
                }
                ThemeModeRow("浅色", ThemeMode.LIGHT, settings.themeMode) {
                    scope.launch { app.settings.setThemeMode(it) }
                }
                ThemeModeRow("深色", ThemeMode.DARK, settings.themeMode) {
                    scope.launch { app.settings.setThemeMode(it) }
                }
            }

            SectionLabel("显示")
            InkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("高刷新率", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "按设备支持的最高刷新率运行（90/120Hz 屏幕生效）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.highRefreshRate,
                        onCheckedChange = { scope.launch { app.settings.setHighRefreshRate(it) } },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("帧率显示", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "在屏幕右上角显示实时 FPS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.showFps,
                        onCheckedChange = { scope.launch { app.settings.setShowFps(it) } },
                    )
                }
            }

            SectionLabel("编辑器")
            InkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "正文字号",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${fontSizeDraft.toInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = fontSizeDraft,
                    onValueChange = { fontSizeDraft = it },
                    onValueChangeFinished = {
                        scope.launch { app.settings.setEditorFontSize(fontSizeDraft.toInt()) }
                    },
                    valueRange = 14f..26f,
                    steps = 11,
                )
                Text(
                    "　　夜色像一滴墨，落进了湖心。",
                    fontSize = fontSizeDraft.toInt().sp,
                    lineHeight = (fontSizeDraft.toInt() * 1.8f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("回车自动缩进", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "换行后自动插入两个全角空格",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.autoIndent,
                        onCheckedChange = { scope.launch { app.settings.setAutoIndent(it) } },
                    )
                }
            }

            SectionLabel("AI 写作")
            InkCard {
                Text(
                    "在编辑器中通过 ✨ 按钮使用续写、润色与情节灵感。需要你自己的 API Key，密钥仅保存在本机。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AiProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.aiProvider == provider,
                                onClick = { scope.launch { app.settings.setAiProvider(provider) } },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            provider.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(
                            selected = settings.aiProvider == provider,
                            onClick = { scope.launch { app.settings.setAiProvider(provider) } },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                AiConfigField(
                    label = "API Key",
                    storedValue = settings.aiApiKey,
                    placeholder = "必填",
                    secret = true,
                    onCommit = { scope.launch { app.settings.setAiApiKey(it) } },
                )
                Spacer(Modifier.height(8.dp))
                AiConfigField(
                    label = "Base URL（留空使用默认）",
                    storedValue = settings.aiBaseUrl,
                    placeholder = settings.aiProvider.defaultBaseUrl,
                    onCommit = { scope.launch { app.settings.setAiBaseUrl(it) } },
                )
                Spacer(Modifier.height(8.dp))
                AiConfigField(
                    label = "模型（留空使用默认）",
                    storedValue = settings.aiModel,
                    placeholder = settings.aiProvider.defaultModel,
                    onCommit = { scope.launch { app.settings.setAiModel(it) } },
                )
            }

            SectionLabel("关于")
            InkCard {
                Text("墨蓝写作", style = MaterialTheme.typography.titleLarge)
                Text(
                    "一款安静的网文写作应用。沉浸编辑、自动保存、撤销重做、自动缩进、世界观构筑与 AI 辅助写作。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/**
 * Text field for AI config values. Shows the stored value until the user
 * starts editing; afterwards the local draft wins (each change is persisted,
 * but slow DataStore round-trips can't clobber fast typing).
 */
@Composable
private fun AiConfigField(
    label: String,
    storedValue: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    secret: Boolean = false,
) {
    var draft by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = draft ?: storedValue,
        onValueChange = {
            draft = it
            onCommit(it)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ThemeModeRow(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = current == mode, onClick = { onSelect(mode) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
    }
}
