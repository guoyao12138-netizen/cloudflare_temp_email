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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkblue.writer.InkApp
import com.inkblue.writer.data.AiProfile
import com.inkblue.writer.data.AiProvider
import com.inkblue.writer.data.AppSettings
import com.inkblue.writer.data.ThemeMode
import com.inkblue.writer.ui.components.InkCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as InkApp
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var fontSizeDraft by remember { mutableFloatStateOf(settings.editorFontSize.toFloat()) }
    LaunchedEffect(settings.editorFontSize) {
        fontSizeDraft = settings.editorFontSize.toFloat()
    }

    var editingProfile by remember { mutableStateOf<AiProfile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

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

            SectionLabel("AI 服务")
            InkCard {
                Text(
                    "支持 Anthropic 协议与 OpenAI 兼容协议，Base URL 可填官方地址，也可填中转站 / API 代理。可添加多个服务用于多 AI 协作。密钥仅保存在本机。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                settings.aiProfiles.forEach { profile ->
                    Surface(
                        onClick = {
                            editingProfile = profile
                            editingIsNew = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${profile.provider.label.substringBefore("（")} · ${profile.effectiveModel()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (profile.id == settings.primaryAiId) RoleBadge("主笔")
                            if (profile.id == settings.reviewerAiId) {
                                Spacer(Modifier.padding(start = 4.dp))
                                RoleBadge("审校")
                            }
                        }
                    }
                }
                if (settings.aiProfiles.isEmpty()) {
                    Text(
                        "还没有配置 AI 服务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                TextButton(
                    onClick = {
                        editingProfile = AiProfile(
                            id = System.currentTimeMillis(),
                            name = "",
                            provider = AiProvider.ANTHROPIC,
                        )
                        editingIsNew = true
                    },
                ) { Text("＋ 添加 AI 服务") }
            }

            SectionLabel("AI 分工")
            InkCard {
                Text("主笔模型", style = MaterialTheme.typography.labelLarge)
                Text(
                    "负责续写、润色、修订与设定起草",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.aiProfiles.isEmpty()) {
                    Text(
                        "请先添加 AI 服务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                settings.aiProfiles.forEach { profile ->
                    RoleRadioRow(
                        label = profile.name,
                        selected = profile.id == settings.primaryAiId,
                        onSelect = { scope.launch { app.settings.setPrimaryAi(profile.id) } },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                Text("审校模型（多 AI 协作）", style = MaterialTheme.typography.labelLarge)
                Text(
                    "开启后，世界观生成由主笔起草、审校模型复核定稿",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RoleRadioRow(
                    label = "不启用",
                    selected = settings.reviewerAiId == 0L,
                    onSelect = { scope.launch { app.settings.setReviewerAi(0L) } },
                )
                settings.aiProfiles.forEach { profile ->
                    RoleRadioRow(
                        label = profile.name,
                        selected = profile.id == settings.reviewerAiId,
                        onSelect = { scope.launch { app.settings.setReviewerAi(profile.id) } },
                    )
                }
            }

            SectionLabel("关于")
            InkCard {
                Text("墨蓝写作", style = MaterialTheme.typography.titleLarge)
                Text(
                    "一款安静的网文写作应用。沉浸编辑、自动保存、世界观构筑、AI 辅助写作与多 AI 协作。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editingProfile?.let { profile ->
        ProfileEditDialog(
            initial = profile,
            isNew = editingIsNew,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                scope.launch { app.settings.saveAiProfile(updated) }
                editingProfile = null
            },
            onDelete = if (editingIsNew) {
                null
            } else {
                {
                    scope.launch { app.settings.deleteAiProfile(profile.id) }
                    editingProfile = null
                }
            },
        )
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

@Composable
private fun RoleBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RoleRadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = onSelect)
    }
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
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                if (isNew) "添加 AI 服务" else "编辑 AI 服务",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("如：Claude 官方 / DeepSeek 中转") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AiProvider.entries.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = provider == p, onClick = { provider = p }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
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
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
