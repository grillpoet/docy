package com.grillpoet.docy.app.ui.pending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grillpoet.docy.app.di.PendingOp

@Composable
fun PendingOpsBar(
    pending: PendingOp?,
    canExecute: Boolean,
    onExecute: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pending == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (pending) {
                    is PendingOp.Move -> Icons.Default.DriveFileMove
                    is PendingOp.Copy -> Icons.Default.ContentCopy
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${pending.sources.size} 项待${if (pending is PendingOp.Move) "移动" else "复制"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!canExecute) {
                    Text(
                        text = "切到目标目录后点击执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onCancel) { Text("取消") }
            if (canExecute) {
                TextButton(onClick = onExecute) { Text("执行") }
            }
        }
    }
}