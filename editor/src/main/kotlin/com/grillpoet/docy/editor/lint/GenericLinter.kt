package com.grillpoet.docy.editor.lint

import com.grillpoet.docy.core.plugin.LanguagePlugin

class GenericLinter(private val plugin: LanguagePlugin) : Diagnostician {

    override suspend fun diagnose(source: SourceView): List<Diagnostic> {
        val result = GenericTokenizer.tokenize(source.lines, plugin)
        val out = mutableListOf<Diagnostic>()
        out += result.unclosed
        out += checkBrackets(result.symbols)
        return out.sortedWith(compareBy({ it.line }, { it.col }))
    }

    private fun checkBrackets(symbols: List<GenericTokenizer.Symbol>): List<Diagnostic> {
        if (plugin.brackets.isEmpty()) return emptyList()

        val out = mutableListOf<Diagnostic>()
        val closeToOpen = mutableMapOf<Char, Char>()
        val openChars = mutableSetOf<Char>()
        val closeChars = mutableSetOf<Char>()

        for (pair in plugin.brackets) {
            val open = pair.getOrNull(0)?.firstOrNull() ?: continue
            val close = pair.getOrNull(1)?.firstOrNull() ?: continue
            closeToOpen[close] = open
            openChars += open
            closeChars += close
        }

        val stack = ArrayDeque<GenericTokenizer.Symbol>()
        for (sym in symbols) {
            when (sym.c) {
                in openChars -> stack.addLast(sym)
                in closeChars -> {
                    if (stack.isEmpty()) {
                        out += Diagnostic(
                            line = sym.line, col = sym.col, endLine = sym.line, endCol = sym.col + 1,
                            severity = Severity.ERROR, message = "多余的 ${sym.c}"
                        )
                    } else {
                        val top = stack.last()
                        val expected = closeToOpen[sym.c]
                        if (top.c == expected) {
                            stack.removeLast()
                        } else {
                            out += Diagnostic(
                                line = sym.line, col = sym.col, endLine = sym.line, endCol = sym.col + 1,
                                severity = Severity.ERROR,
                                message = "括号不匹配：期待 ${closeOf(top.c)}，遇到 ${sym.c}"
                            )
                            stack.removeLast()
                        }
                    }
                }
            }
        }

        for (open in stack) {
            out += Diagnostic(
                line = open.line, col = open.col, endLine = open.line, endCol = open.col + 1,
                severity = Severity.ERROR, message = "括号未闭合：${open.c}"
            )
        }
        return out
    }

    private fun closeOf(c: Char): String = when (c) {
        '(' -> ")"
        '[' -> "]"
        '{' -> "}"
        '<' -> ">"
        else -> "?"
    }
}
