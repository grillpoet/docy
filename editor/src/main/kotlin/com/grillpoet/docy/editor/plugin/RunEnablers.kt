package com.grillpoet.docy.editor.plugin

import com.grillpoet.docy.editor.love.LoveProject

/**
 * enabledBy 名 -> 检查函数。运行按钮渲染时调用。
 */
object RunEnablers {
    fun check(name: String?, ctx: RunContext): Boolean = when (name) {
        null -> true
        "loveProject" -> LoveProject.isProject(ctx.dir)
        else -> true
    }
}