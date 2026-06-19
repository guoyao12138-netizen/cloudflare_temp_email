package com.vertexchat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vertexchat.data.model.GlobalSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()

    var systemPrompt by remember(saved.systemPrompt) { mutableStateOf(saved.systemPrompt) }
    var temperature by remember(saved.temperature) { mutableFloatStateOf(saved.temperature) }
    var maxTokens by remember(saved.maxTokens) { mutableIntStateOf(saved.maxTokens) }

    var proxyEnabled by remember(saved.proxyEnabled) { mutableStateOf(saved.proxyEnabled) }
    var proxyType by remember(saved.proxyType) { mutableStateOf(saved.proxyType) }
    var proxyHost by remember(saved.proxyHost) { mutableStateOf(saved.proxyHost) }
    var proxyPort by remember(saved.proxyPort) { mutableStateOf(saved.proxyPort.toString()) }
    var proxyUser by remember(saved.proxyUsername) { mutableStateOf(saved.proxyUsername) }
    var proxyPass by remember(saved.proxyPassword) { mutableStateOf(saved.proxyPassword) }

    var searchEnabled by remember(saved.searchEnabled) { mutableStateOf(saved.searchEnabled) }
    var searchApiKey by remember(saved.searchApiKey) { mutableStateOf(saved.searchApiKey) }
    var mcpEnabled by remember(saved.mcpEnabled) { mutableStateOf(saved.mcpEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Providers shortcut
            Button(onClick = onOpenProviders, modifier = Modifier.fillMaxWidth()) {
                Text("Manage Providers (API keys, models)")
            }

            SectionHeader("Generation")

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System prompt (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Temperature: ${"%.2f".format(temperature)}",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)
            }

            OutlinedTextField(
                value = maxTokens.toString(),
                onValueChange = { maxTokens = it.toIntOrNull() ?: maxTokens },
                label = { Text("Max output tokens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionHeader("Network Proxy")

            SwitchRow("Enable proxy", proxyEnabled) { proxyEnabled = it }

            if (proxyEnabled) {
                // Proxy type selector
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = proxyType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Proxy type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("HTTP", "SOCKS5").forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { proxyType = t; typeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = proxyHost,
                    onValueChange = { proxyHost = it },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { proxyPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = proxyUser,
                        onValueChange = { proxyUser = it },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                }
                OutlinedTextField(
                    value = proxyPass,
                    onValueChange = { proxyPass = it },
                    label = { Text("Password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()
            SectionHeader("Tools")

            SwitchRow("Web search (Brave Search API)", searchEnabled) { searchEnabled = it }
            if (searchEnabled) {
                OutlinedTextField(
                    value = searchApiKey,
                    onValueChange = { searchApiKey = it },
                    label = { Text("Brave Search API key") },
                    supportingText = { Text("api.search.brave.com — free tier: 2000 req/month") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SwitchRow("MCP servers (tools from remote servers)", mcpEnabled) { mcpEnabled = it }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    viewModel.save(
                        saved.copy(
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            maxTokens = maxTokens,
                            proxyEnabled = proxyEnabled,
                            proxyType = proxyType,
                            proxyHost = proxyHost.trim(),
                            proxyPort = proxyPort.toIntOrNull() ?: 8080,
                            proxyUsername = proxyUser.trim(),
                            proxyPassword = proxyPass,
                            searchEnabled = searchEnabled,
                            searchApiKey = searchApiKey.trim(),
                            mcpEnabled = mcpEnabled,
                        )
                    )
                    onBack()
                }) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
