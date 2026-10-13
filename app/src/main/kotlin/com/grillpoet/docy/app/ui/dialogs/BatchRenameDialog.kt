package com.grillpoet.docy.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.docy.core.model.FileItem

enum class BatchRenameMode { SUFFIX, CUSTOM }

@Composable
fun BatchRenameDialog(
    items: List<FileItem>,
    modifier: Modifier = Modifier,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(BatchRenameMode.SUFFIX) }
    var startIndex by remember { mutableStateOf("1") }
    var digits by remember { mutableStateOf("3") }
    val customNames = remember(items) {
        mutableStateMapOf<String, String>().apply {
            items.forEach { put(it.path, it.name) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量重命名 · ${items.size} 项") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == BatchRenameMode.SUFFIX,
                        onClick = { mode = BatchRenameMode.SUFFIX }
                    )
                    Text("加后缀")
                }
                if (mode == BatchRenameMode.SUFFIX) {
                    Row(
                        Modifier.padding(start = 32.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startIndex,
                            onValueChange = { startIndex = it.filter(Char::isDigit).take(4) },
                            label = { Text("起始") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = digits,
                            onValueChange = { digits = it.filter(Char::isDigit).take(2) },
                            label = { Text("位数") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                    val si = startIndex.toIntOrNull() ?: 1
                    val dg = (digits.toIntOrNull() ?: 3).coerceIn(1, 6)
                    Text(
                        text = "预览：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                    )
                    items.take(3).forEachIndexed { i, item ->
                        val preview = buildSuffixName(item.name, si + i, dg)
                        Text(
                            text = "  ${item.name}  →  $preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (items.size > 3) {
                        Text(
                            text = "  …还有 ${items.size - 3} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == BatchRenameMode.CUSTOM,
                        onClick = { mode = BatchRenameMode.CUSTOM }
                    )
                    Text("自定义")
                }
                if (mode == BatchRenameMode.CUSTOM) {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(items, key = { it.path }) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customNames[item.path] ?: item.name,
                                    onValueChange = { customNames[item.path] = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val plans = when (mode) {
                    BatchRenameMode.SUFFIX -> {
                        val si = startIndex.toIntOrNull() ?: 1
                        val dg = (digits.toIntOrNull() ?: 3).coerceIn(1, 6)
                        items.mapIndexed { i, item ->
                            item.path to buildSuffixName(item.name, si + i, dg)
                        }.toMap()
                    }
                    BatchRenameMode.CUSTOM -> {
                        customNames.filter { (path, newName) ->
                            items.firstOrNull { it.path == path }?.name != newName
                        }
                    }
                }
                onConfirm(plans)
            }) { Text("重命名") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun buildSuffixName(name: String, index: Int, digits: Int): String {
    val ext = name.substringAfterLast('.', "")
    val base = if (ext.isEmpty()) name else name.removeSuffix(".$ext")
    val idx = index.toString().padStart(digits, '0')
    return if (ext.isEmpty()) "${base}_$idx" else "${base}_$idx.$ext"
}