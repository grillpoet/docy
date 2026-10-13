package com.grillpoet.docy.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.grillpoet.docy.core.model.FileKind
import java.io.File

/**
 * 文件图标。
 *
 * 按 [kind] 和 [path] 决定展示方式：
 *  - 图片 / 视频，且 [path] 非空：加载缩略图，加载中或失败时露出静态图标
 *  - 其他：直接显示静态图标
 *
 * 缩略图由 Coil 加载。内存和磁盘缓存由
 * [com.grillpoet.docy.designsystem.thumbnail.buildDocyImageLoader] 配置。
 *
 * 用 [AsyncImage] 而不是 `SubcomposeAsyncImage`：后者会给每个 cell 开
 * 独立 subcomposition，网格里同时可见几十个 cell 时会拖慢列表。
 *
 * @param kind 文件类型。决定兜底图标和是否走缩略图分支。
 * @param path 文件绝对路径。留空时强制走静态图标分支。
 *             调用方通常传 [com.grillpoet.docy.core.model.FileItem.path]。
 * @param modifier 外部修饰符。
 * @param size 图标边长。缩略图也按这个尺寸裁切。默认 40dp。
 */
@Composable
fun DocyFileIcon(
    kind: FileKind,
    path: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    if (path.isNotBlank() && kind.isThumbnailable()) {
        ThumbnailIcon(kind = kind, path = path, size = size, modifier = modifier)
    } else {
        StaticIcon(kind = kind, size = size, modifier = modifier)
    }
}

/** 静态图标。文件夹用主题色，其他用次级文字色。 */
@Composable
private fun StaticIcon(
    kind: FileKind,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = kind.vector(),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = kind.tint()
    )
}

/**
 * 缩略图。
 *
 * 两层叠加：
 *  - 底层：静态图标。加载中和加载失败时可见。
 *  - 顶层：[AsyncImage] 加载的缩略图。成功后覆盖底层。
 */
@Composable
private fun ThumbnailIcon(
    kind: FileKind,
    path: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val model = remember(path, kind) {
        ImageRequest.Builder(context)
            .data(File(path))
            .apply {
                // 视频：抽第一帧作为缩略图
                if (kind == FileKind.VIDEO) videoFrameMillis(0L)
            }
            .build()
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = kind.vector(),
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
            tint = kind.tint()
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 该类型是否有缩略图能力。目录、文本、代码等没有。 */
private fun FileKind.isThumbnailable(): Boolean =
    this == FileKind.IMAGE || this == FileKind.VIDEO

/** 类型对应的矢量图标。 */
private fun FileKind.vector(): ImageVector = when (this) {
    FileKind.FOLDER -> Icons.Default.Folder
    FileKind.TEXT -> Icons.Default.Description
    FileKind.CODE -> Icons.Default.Code
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.Movie
    FileKind.AUDIO -> Icons.Default.MusicNote
    FileKind.ARCHIVE -> Icons.Default.FolderZip
    FileKind.PDF -> Icons.Default.PictureAsPdf
    FileKind.APK -> Icons.Default.Android
    FileKind.UNKNOWN -> Icons.Default.InsertDriveFile
}

/** 类型对应的图标着色。文件夹用主题色，其他用次级文字色。 */
@Composable
private fun FileKind.tint(): Color = if (this == FileKind.FOLDER) {
    MaterialTheme.colorScheme.primary
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}
