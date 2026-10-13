package com.grillpoet.docy.editor.lint

/**
 * Lua linter。只管语言层。
 *
 *   A 词法：未闭合字符串 / 长括号 / 块注释
 *   B 括号配对
 *   C 块结构（function / if / while / for / repeat 是否配对）
 *
 * 库 API 白名单（love.xxx.yyy）由 LibraryLinter 负责。
 */
class LuaLinter : Diagnostician {

    override suspend fun diagnose(source: SourceView): List<Diagnostic> {
        val result = mutableListOf<Diagnostic>()
        val tok = LuaTokenizer.tokenize(source.lines)

        result += tok.unclosed
        result += checkBrackets(tok.tokens)
        result += checkBlocks(tok.tokens)

        return result.sortedWith(compareBy({ it.line }, { it.col }))
    }

    // ---------- B 括号配对 ----------

    private fun checkBrackets(tokens: List<LuaTokenizer.Token>): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()
        data class Open(val c: Char, val tok: LuaTokenizer.Token)
        val stack = ArrayDeque<Open>()
        val pair = mapOf(')' to '(', ']' to '[', '}' to '{')

        for (t in tokens) {
            if (t.type != LuaTokenizer.Type.SYMBOL) continue
            val c = t.text.firstOrNull() ?: continue
            when (c) {
                '(', '[', '{' -> stack.addLast(Open(c, t))
                ')', ']', '}' -> {
                    val expect = pair[c]!!
                    if (stack.isEmpty()) {
                        out += Diagnostic(
                            t.line, t.col, t.line, t.col + 1,
                            Severity.ERROR, "多余的 $c"
                        )
                    } else {
                        val top = stack.last()
                        if (top.c == expect) {
                            stack.removeLast()
                        } else {
                            out += Diagnostic(
                                t.line, t.col, t.line, t.col + 1,
                                Severity.ERROR, "括号不匹配：期待 ${closeOf(top.c)}，遇到 $c"
                            )
                            stack.removeLast()
                        }
                    }
                }
            }
        }
        for (open in stack) {
            out += Diagnostic(
                open.tok.line, open.tok.col,
                open.tok.line, open.tok.col + 1,
                Severity.ERROR, "括号未闭合：${open.c}"
            )
        }
        return out
    }

    private fun closeOf(c: Char): String = when (c) {
        '(' -> ")"
        '[' -> "]"
        '{' -> "}"
        else -> "?"
    }

    // ---------- C 块结构 ----------

    private fun checkBlocks(tokens: List<LuaTokenizer.Token>): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()
        data class Frame(val kw: String, val tok: LuaTokenizer.Token)
        val stack = ArrayDeque<Frame>()

        // 注意：do 不入栈。
        // while ... do ... end / for ... do ... end 里 do 是附属的，
        // 只有一个 end，如果把 do 也压栈就会导致栈残留，误报缺 end。
        // 独立 do ... end 罕见，牺牲它的检测。
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.type != LuaTokenizer.Type.KEYWORD) { i++; continue }
            when (t.text) {
                "function", "for", "while", "repeat", "if" ->
                    stack.addLast(Frame(t.text, t))

                "end" -> {
                    if (stack.isEmpty()) {
                        out += Diagnostic(
                            t.line, t.col, t.line, t.col + 3,
                            Severity.ERROR, "多余的 end"
                        )
                    } else {
                        val top = stack.removeLast()
                        if (top.kw == "repeat") {
                            out += Diagnostic(
                                top.tok.line, top.tok.col,
                                top.tok.line, top.tok.col + 6,
                                Severity.WARNING, "repeat 应该用 until 结束"
                            )
                        }
                    }
                }

                "until" -> {
                    if (stack.isEmpty() || stack.last().kw != "repeat") {
                        out += Diagnostic(
                            t.line, t.col, t.line, t.col + 5,
                            Severity.ERROR, "多余的 until"
                        )
                    } else {
                        stack.removeLast()
                    }
                }
            }
            i++
        }

        for (frame in stack) {
            out += Diagnostic(
                frame.tok.line, frame.tok.col,
                frame.tok.line, frame.tok.col + frame.kw.length,
                Severity.ERROR, "${frame.kw} 缺少对应的 end"
            )
        }
        return out
    }
}
