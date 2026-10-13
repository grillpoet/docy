package com.grillpoet.docy.editor.love

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object LoveAndroid {

    private val PACKAGES = listOf(
        "org.love2d.android",
        "com.love2d.android",
        "org.love2d.android.dev"
    )

    fun find(ctx: Context): String? {
        val pm = ctx.packageManager

        PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }?.let {
            return it
        }

        for (pkg in PACKAGES) {
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.isSuccess
            if (ok) return pkg
        }

        return null
    }

    fun isInstalled(ctx: Context): Boolean = find(ctx) != null

    fun launch(ctx: Context, loveFile: File, pkg: String): Boolean {
        if (pkg !in PACKAGES) return false

        val pm = ctx.packageManager
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", loveFile
        )

        val candidates = listOf(
            "application/x-love-game",
            "application/zip",
            "application/octet-stream"
        )

        for (mime in candidates) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(pkg)
            }

            // 两个重载都返回 ResolveInfo?
            val resolvedPackage: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
                    ?.activityInfo
                    ?.packageName
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0)
                    ?.activityInfo
                    ?.packageName
            }

            if (resolvedPackage != null && resolvedPackage in PACKAGES) {
                runCatching { ctx.startActivity(intent) }
                    .onSuccess { return true }
            }
        }
        return false
    }
}
