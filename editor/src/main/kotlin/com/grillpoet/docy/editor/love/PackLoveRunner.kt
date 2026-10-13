package com.grillpoet.docy.editor.love

import android.content.Context
import com.grillpoet.docy.core.plugin.RunSpec
import com.grillpoet.docy.editor.plugin.RunContext
import com.grillpoet.docy.editor.plugin.Runner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PackLoveRunner(private val appCtx: Context) : Runner {

    override suspend fun run(spec: RunSpec, ctx: RunContext): Result<String> =
        withContext(Dispatchers.IO) {
            if (!LoveProject.isProject(ctx.dir)) {
                return@withContext Result.failure(
                    LoveException("当前目录没有 main.lua，不是 love2d 项目")
                )
            }

            val loveFile = runCatching { LovePacker().pack(ctx.dir) }
                .getOrElse {
                    return@withContext Result.failure(LoveException("打包失败：${it.message}"))
                }

            val pkg = LoveAndroid.find(appCtx)
                ?: return@withContext Result.failure(LoveNotInstalled(loveFile))

            val ok = LoveAndroid.launch(appCtx, loveFile, pkg)
            if (ok) Result.success("已启动 ${loveFile.name}")
            else Result.failure(LoveException("LÖVE 拒绝打开 .love"))
        }
}

class LoveNotInstalled(val loveFile: File) : Exception("未安装 LÖVE for Android")
class LoveException(msg: String) : Exception(msg)