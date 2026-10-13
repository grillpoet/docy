package com.grillpoet.docy.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.docy.app.ui.common.FileOpener
import com.grillpoet.docy.core.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

@Composable
fun FileDetailsDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val rows = remember(item) { buildRows(item) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("详细信息") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                rows.forEach { (label, value, copyable) ->
                    DetailRow(
                        label = label,
                        value = value,
                        copyable = copyable,
                        onClick = {
                            if (copyable && value.isNotBlank()) {
                                copyToClipboard(ctx, value)
                                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyable: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (copyable) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (copyable) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class DetailTriple(
    val label: String,
    val value: String,
    val copyable: Boolean
)

private fun buildRows(item: FileItem): List<DetailTriple> {
    val rows = mutableListOf<DetailTriple>()
    rows.add(DetailTriple("名称", item.name, true))
    rows.add(DetailTriple("路径", item.path, true))

    rows.add(
        DetailTriple(
            "类型",
            if (item.isDirectory) "文件夹" else kindLabel(item.kind),
            false
        )
    )

    if (!item.isDirectory) {
        rows.add(DetailTriple("大小", humanSize(item.size), false))
        rows.add(DetailTriple("大小（字节）", item.size.toString(), true))
    }

    if (item.lastModified > 0) {
        rows.add(DetailTriple("修改时间", TIME_FMT.format(Date(item.lastModified)), false))
    }

    rows.add(DetailTriple("MIME", FileOpener.guessMime(File(item.path)), false))

    val file = File(item.path)
    rows.add(DetailTriple("可读", if (file.canRead()) "是" else "否", false))
    rows.add(DetailTriple("可写", if (file.canWrite()) "是" else "否", false))

    return rows
}

private fun kindLabel(kind: com.grillpoet.docy.core.model.FileKind): String = when (kind) {
    com.grillpoet.docy.core.model.FileKind.FOLDER -> "文件夹"
    com.grillpoet.docy.core.model.FileKind.TEXT -> "文本"
    com.grillpoet.docy.core.model.FileKind.CODE -> "代码"
    com.grillpoet.docy.core.model.FileKind.IMAGE -> "图片"
    com.grillpoet.docy.core.model.FileKind.VIDEO -> "视频"
    com.grillpoet.docy.core.model.FileKind.AUDIO -> "音频"
    com.grillpoet.docy.core.model.FileKind.ARCHIVE -> "压缩包"
    com.grillpoet.docy.core.model.FileKind.PDF -> "PDF"
    com.grillpoet.docy.core.model.FileKind.APK -> "APK"
    com.grillpoet.docy.core.model.FileKind.UNKNOWN -> "文件"
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("docy", text))
}
