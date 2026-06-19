package com.vertexchat.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vertexchat.data.model.ProviderConfig
import com.vertexchat.data.model.ProviderPresets
import com.vertexchat.data.model.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    initial: ProviderConfig?,
    onBack: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
) {
    val isNew = initial == null
    val base = initial ?: ProviderPresets.OPENAI.copy(id = java.util.UUID.randomUUID().toString())

    var name by remember { mutableStateOf(base.name) }
    var type by remember { mutableStateOf(base.type) }
    var baseUrl by remember { mutableStateOf(base.baseUrl) }
    var apiKey by remember { mutableStateOf(base.apiKey) }
    var models by remember { mutableStateOf(base.models.joinToString(",")) }
    var activeModel by remember { mutableStateOf(base.activeModel) }
    var gcpProject by remember { mutableStateOf(base.gcpProjectId) }
    var gcpLocation by remember { mutableStateOf(base.gcpLocation) }

    // Update baseUrl when switching preset
    fun applyPreset(t: ProviderType) {
        type = t
        when (t) {
            ProviderType.OPENAI -> {
                if (baseUrl.isBlank() || baseUrl == "https://api.anthropic.com") {
                    baseUrl = "https://api.openai.com/v1"
                }
            }
            ProviderType.ANTHROPIC -> baseUrl = "https://api.anthropic.com"
            ProviderType.VERTEX -> baseUrl = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add Provider" else "Edit Provider") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Quick-add presets (new only)
            if (isNew) {
                Text("Quick setup", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "OpenAI" to ProviderPresets.OPENAI,
                        "Copilot" to ProviderPresets.COPILOT,
                        "Anthropic" to ProviderPresets.ANTHROPIC,
                        "Vertex" to ProviderPresets.VERTEX,
                    ).forEach { (label, preset) ->
                        Button(
                            onClick = {
                                name = preset.name
                                type = preset.type
                                baseUrl = preset.baseUrl
                                models = preset.models.joinToString(",")
                                activeModel = preset.activeModel
                                gcpProject = preset.gcpProjectId
                                gcpLocation = preset.gcpLocation
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Type selector
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = type.label(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    ProviderType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label()) },
                            onClick = { applyPreset(t); typeExpanded = false },
                        )
                    }
                }
            }

            if (type != ProviderType.VERTEX) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    supportingText = { Text("e.g. https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Token") },
                    supportingText = {
                        Text(
                            when (type) {
                                ProviderType.OPENAI -> "sk-... or GitHub PAT for Copilot"
                                ProviderType.ANTHROPIC -> "sk-ant-..."
                                else -> "API key"
                            }
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = gcpProject,
                    onValueChange = { gcpProject = it },
                    label = { Text("GCP Project ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gcpLocation,
                    onValueChange = { gcpLocation = it },
                    label = { Text("Location") },
                    supportingText = { Text("e.g. us-central1, europe-west4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = models,
                onValueChange = { models = it },
                label = { Text("Models (comma-separated)") },
                supportingText = { Text("e.g. gpt-4o,gpt-4o-mini") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = activeModel,
                onValueChange = { activeModel = it },
                label = { Text("Default model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    val modelList = models.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(
                        base.copy(
                            name = name.ifBlank { type.label() },
                            type = type,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            models = modelList,
                            activeModel = activeModel.trim().ifBlank { modelList.firstOrNull() ?: "" },
                            gcpProjectId = gcpProject.trim(),
                            gcpLocation = gcpLocation.trim(),
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

private fun ProviderType.label() = when (this) {
    ProviderType.OPENAI -> "OpenAI-compatible"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.VERTEX -> "Google Vertex AI"
}
