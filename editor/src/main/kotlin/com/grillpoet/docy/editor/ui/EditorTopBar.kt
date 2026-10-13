package com.grillpoet.docy.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTopBar(
    fileName: String,
    dirty: Boolean,
    readOnly: Boolean,
    showRun: Boolean,
    runEnabled: Boolean,
    running: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onFind: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onRunDisabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (dirty) {
                    Text(
                        text = "未保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showRun) {
                Box {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .combinedClickable(
                                    onClick = { if (runEnabled) onRun() else onRunDisabled() },
                                    onLongClick = { if (!runEnabled) onRunDisabled() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "运行",
                                tint = if (runEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onUndo, enabled = canUndo && !readOnly) {
                Icon(Icons.Default.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = onRedo, enabled = canRedo && !readOnly) {
                Icon(Icons.Default.Redo, contentDescription = "重做")
            }
            IconButton(onClick = onFind) {
                Icon(Icons.Default.Search, contentDescription = "查找")
            }
            IconButton(onClick = onToggleReadOnly) {
                Icon(
                    imageVector = if (readOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (readOnly) "只读" else "可编辑"
                )
            }
            IconButton(onClick = onSave, enabled = dirty && !readOnly) {
                Icon(Icons.Default.Save, contentDescription = "保存")
            }
        }
    }
}