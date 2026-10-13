package com.grillpoet.docy.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * 用系统应用打开 / 分享 / 安装文件。
 * 走 FileProvider，外部应用通过 content:// URI 读取。
 */
object FileOpener {

    fun guessMime(file: File): String {
        val ext = file.extension.lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    fun openWith(ctx: Context, file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /**
     * 安装 APK。
     * Android 8+ 需要"安装未知应用"权限，否则跳系统设置页。
     */
    fun installApk(ctx: Context, file: File): Result<InstallResult> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                // 引导到"安装未知应用"设置页
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { ctx.startActivity(intent) }
                    .onFailure {
                        // 部分 ROM 忽略 package: URI，回退到全局页
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    }
                return@runCatching InstallResult.NeedPermission
            }
        }
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        InstallResult.Started
    }

    fun shareOne(ctx: Context, file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = guessMime(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, file.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    }

    fun shareMany(ctx: Context, files: List<File>): Result<Unit> = runCatching {
        require(files.isNotEmpty()) { "没有文件可分享" }
        val uris = files.map {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it)
        }
        val allImages = files.all { guessMime(it).startsWith("image/") }
        val allVideos = files.all { guessMime(it).startsWith("video/") }
        val mime = when {
            allImages -> "image/*"
            allVideos -> "video/*"
            else -> "*/*"
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享 ${files.size} 个文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    }
}

enum class InstallResult {
    Started,
    NeedPermission
}
