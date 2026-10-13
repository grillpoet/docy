package com.grillpoet.docy.editor.plugin

import com.grillpoet.docy.core.plugin.Completion
import com.grillpoet.docy.core.plugin.LanguagePlugin
import com.grillpoet.docy.core.plugin.LibraryPlugin
import com.grillpoet.docy.core.plugin.RunSpec

/**
 * 一个文件当前的活跃上下文：语言 + 挂载的库。
 */
data class ActiveContext(
    val language: LanguagePlugin?,
    val libraries: List<LibraryPlugin> = emptyList()
) {
    val allCompletions: List<Completion>
        get() {
            val fromLang = language?.completions ?: emptyList()
            val fromLibs = libraries.flatMap { lib ->
                lib.completions + lib.api.map { Completion(it, it, "") }
            }
            return fromLang + fromLibs
        }

    /** 从库里找 run，没有再退回语言的 */
    val runSpec: RunSpec?
        get() = libraries.firstNotNullOfOrNull { it.run } ?: language?.run

    /** 所有库声明的 API 全名，供 LibraryLinter 白名单用 */
    val allApis: Set<String>
        get() = libraries.flatMap { it.api }.toSet()

    companion object {
        val EMPTY = ActiveContext(null, emptyList())
    }
}
