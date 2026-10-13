package com.grillpoet.docy.editor.complete

import com.grillpoet.docy.core.plugin.Completion
import com.grillpoet.docy.editor.plugin.ActiveContext

object CompletionEngine {

    private val WORD_KINDS = setOf("keyword", "type", "builtin", "literal")

    fun candidates(ctx: ActiveContext, prefix: String): List<Completion> {
        if (prefix.isEmpty()) return emptyList()
        val all = ctx.allCompletions
        val matched = all.filter { it.label.startsWith(prefix, ignoreCase = false) }

        // 单字符前缀：只在有 snippet 匹配时返回 snippet，否则才回退到单词
        if (prefix.length == 1) {
            val snippets = matched.filter {
                it.detail.isNotEmpty() && it.detail !in WORD_KINDS
            }
            if (snippets.isNotEmpty()) {
                return snippets.distinctBy { it.label }.take(30)
            }
        }
        return matched.distinctBy { it.label }.take(30)
    }
}
