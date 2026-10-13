package com.grillpoet.docy.app.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionMoreSheet(
    canCompress: Boolean,
    canExtract: Boolean,
    canShare: Boolean,
    onCompress: () -> Unit,
    onExtractHere: () -> Unit,
    onExtractToNewFolder: () -> Unit,
    onShare: () -> Unit,
    onCopyPath: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (canCompress) {
                SheetItem(
                    icon = Icons.Default.FolderZip,
                    text = "压缩",
                    onClick = { onDismiss(); onCompress() }
                )
            }
            if (canExtract) {
                SheetItem(
                    icon = Icons.Default.Unarchive,
                    text = "解压到此处",
                    onClick = { onDismiss(); onExtractHere() }
                )
                SheetItem(
                    icon = Icons.Default.Unarchive,
                    text = "解压到新文件夹",
                    onClick = { onDismiss(); onExtractToNewFolder() }
                )
            }
            if (canShare) {
                SheetItem(
                    icon = Icons.Default.Share,
                    text = "分享",
                    onClick = { onDismiss(); onShare() }
                )
            }
            SheetItem(
                icon = Icons.Default.ContentCopy,
                text = "复制路径",
                onClick = { onDismiss(); onCopyPath() }
            )
            SheetItem(
                icon = Icons.Default.Info,
                text = "详细信息",
                onClick = { onDismiss(); onDetails() }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
