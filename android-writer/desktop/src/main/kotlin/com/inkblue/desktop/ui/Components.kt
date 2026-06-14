package com.inkblue.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Int = 14,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val color = MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding.dp), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding.dp), content = content)
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "删除",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    primaryLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (primary: String, secondary: String) -> Unit,
    initialPrimary: String = "",
    secondaryLabel: String? = null,
    initialSecondary: String = "",
    confirmText: String = "确定",
    secondaryMinLines: Int = 2,
) {
    var primary by remember { mutableStateOf(initialPrimary) }
    var secondary by remember { mutableStateOf(initialSecondary) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    label = { Text(primaryLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().width(420.dp),
                )
                if (secondaryLabel != null) {
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it },
                        label = { Text(secondaryLabel) },
                        minLines = secondaryMinLines,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(primary.trim(), secondary.trim()) }, enabled = primary.isNotBlank()) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun LoadingDialog(title: String, message: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
