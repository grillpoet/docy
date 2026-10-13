package com.grillpoet.docy.core.model

import java.net.URLConnection

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
) {
    val extension: String =
        if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    val kind: FileKind = FileKind.of(extension, isDirectory)

    /** 懒计算。首次访问时算一次，之后缓存。 */
    val mimeType: String by lazy {
        if (isDirectory) ""
        else runCatching {
            URLConnection.guessContentTypeFromName(name)
        }.getOrNull() ?: "application/octet-stream"
    }
}
