package com.grillpoet.docy.editor.plugin

import android.content.Context
import android.content.Intent
import com.grillpoet.docy.core.plugin.RunSpec
import java.io.File

/**
 * 运行按钮调度。
 * 注册表式：internal action -> Runner。
 */
class RunDispatcher(private val context: Context) {

    private val handlers = mutableMapOf<String, Runner>()

    fun register(action: String, runner: Runner) {
        handlers[action] = runner
    }

    suspend fun dispatch(spec: RunSpec, ctx: RunContext): Result<String> = when (spec.type) {
        "internal" -> handlers[spec.action]?.run(spec, ctx)
            ?: Result.failure(IllegalStateException("未注册的内置动作：${spec.action}"))
        "intent" -> runCatching {
            context.startActivity(Intent(spec.action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "已启动 ${spec.action}"
        }
        "shell" -> Result.failure(IllegalStateException("shell 动作默认禁用"))
        else -> Result.failure(IllegalArgumentException("未知 type: ${spec.type}"))
    }
}