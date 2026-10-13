package com.grillpoet.docy.editor.lint

/**
 * Lua 词法分析。跳过字符串和注释，只产出有意义的 token。
 * 记录未闭合的块注释 / 字符串 / 引号，供 linter 报错。
 */
object LuaTokenizer {

    enum class Type { IDENTIFIER, NUMBER, STRING, KEYWORD, SYMBOL }

    data class Token(
        val line: Int,
        val col: Int,
        val type: Type,
        val text: String
    )

    data class Result(
        val tokens: List<Token>,
        val unclosed: List<Diagnostic>
    )

    private val KEYWORDS = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for",
        "function", "goto", "if", "in", "local", "nil", "not", "or",
        "repeat", "return", "then", "true", "until", "while"
    )

    fun tokenize(lines: List<String>): Result {
        val tokens = mutableListOf<Token>()
        val unclosed = mutableListOf<Diagnostic>()

        // 跨行状态
        var inBlockComment = false
        var inLongString = false
        var longStringOpenerLine = -1
        var longStringOpenerCol = -1
        var blockOpenerLine = -1
        var blockOpenerCol = -1

        for ((li, line) in lines.withIndex()) {
            var i = 0
            while (i < line.length) {
                // 1) 跨行状态
                if (inBlockComment) {
                    val idx = line.indexOf("]]", i)
                    if (idx < 0) { i = line.length; break }
                    inBlockComment = false
                    i = idx + 2
                    continue
                }
                if (inLongString) {
                    val idx = line.indexOf("]]", i)
                    if (idx < 0) { i = line.length; break }
                    inLongString = false
                    i = idx + 2
                    continue
                }

                val c = line[i]

                // 2) 空白
                if (c.isWhitespace()) { i++; continue }

                // 3) 注释
                if (c == '-' && i + 1 < line.length && line[i + 1] == '-') {
                    // 块注释
                    if (i + 3 < line.length && line[i + 2] == '[' && line[i + 3] == '[') {
                        val idx = line.indexOf("]]", i + 4)
                        if (idx < 0) {
                            inBlockComment = true
                            blockOpenerLine = li
                            blockOpenerCol = i
                            i = line.length
                            break
                        }
                        i = idx + 2
                        continue
                    }
                    // 行注释到行尾
                    break
                }

                // 4) 长字符串 [[
                if (c == '[' && i + 1 < line.length && line[i + 1] == '[') {
                    val idx = line.indexOf("]]", i + 2)
                    if (idx < 0) {
                        inLongString = true
                        longStringOpenerLine = li
                        longStringOpenerCol = i
                        tokens += Token(li, i, Type.STRING, line.substring(i))
                        i = line.length
                        break
                    }
                    val s = line.substring(i, idx + 2)
                    tokens += Token(li, i, Type.STRING, s)
                    i = idx + 2
                    continue
                }

                // 5) 引号字符串
                if (c == '"' || c == '\'') {
                    val startCol = i
                    var j = i + 1
                    var closed = false
                    while (j < line.length) {
                        val cj = line[j]
                        if (cj == '\\' && j + 1 < line.length) { j += 2; continue }
                        if (cj == c) { closed = true; break }
                        j++
                    }
                    if (!closed) {
                        unclosed += Diagnostic(
                            line = li,
                            col = startCol,
                            endLine = li,
                            endCol = line.length,
                            severity = Severity.ERROR,
                            message = "字符串未闭合"
                        )
                        tokens += Token(li, startCol, Type.STRING, line.substring(startCol))
                        i = line.length
                        break
                    }
                    tokens += Token(li, startCol, Type.STRING, line.substring(startCol, j + 1))
                    i = j + 1
                    continue
                }

                // 6) 数字
                if (c.isDigit()) {
                    var j = i
                    while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++
                    tokens += Token(li, i, Type.NUMBER, line.substring(i, j))
                    i = j
                    continue
                }

                // 7) 标识符 / 关键字
                if (c.isLetter() || c == '_') {
                    var j = i
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    val text = line.substring(i, j)
                    val type = if (text in KEYWORDS) Type.KEYWORD else Type.IDENTIFIER
                    tokens += Token(li, i, type, text)
                    i = j
                    continue
                }

                // 8) 符号
                tokens += Token(li, i, Type.SYMBOL, c.toString())
                i++
            }
        }

        // EOF 检查
        if (inBlockComment) {
            unclosed += Diagnostic(
                line = blockOpenerLine,
                col = blockOpenerCol,
                endLine = blockOpenerLine,
                endCol = blockOpenerCol + 4,
                severity = Severity.ERROR,
                message = "块注释未闭合"
            )
        }
        if (inLongString) {
            unclosed += Diagnostic(
                line = longStringOpenerLine,
                col = longStringOpenerCol,
                endLine = longStringOpenerLine,
                endCol = longStringOpenerCol + 2,
                severity = Severity.ERROR,
                message = "长字符串未闭合"
            )
        }

        return Result(tokens, unclosed)
    }
}