package com.grillpoet.docy.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.grillpoet.docy.core.archive.ArchiveFormat

@Composable
fun CompressDialog(
    defaultName: String,
    modifier: Modifier = Modifier,
    onConfirm: (name: String, format: ArchiveFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var format by remember { mutableStateOf(ArchiveFormat.ZIP) }
    val invalid = name.isBlank() || name.contains('/')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("压缩") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    isError = invalid && name.isNotEmpty(),
                    modifier = Modifier
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "格式",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    FilterChip(
                        selected = format == ArchiveFormat.ZIP,
                        onClick = { format = ArchiveFormat.ZIP },
                        label = { Text("zip") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = format == ArchiveFormat.SEVEN_Z,
                        onClick = { format = ArchiveFormat.SEVEN_Z },
                        label = { Text("7z") }
                    )
                }
                Text(
                    text = "rar 不支持写入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, format) },
                enabled = !invalid
            ) { Text("开始") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}