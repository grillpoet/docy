package com.grillpoet.docy.editor.lint

import com.grillpoet.docy.core.plugin.LanguagePlugin

object GenericTokenizer {

    data class Symbol(val line: Int, val col: Int, val c: Char)

    data class Result(
        val symbols: List<Symbol>,
        val unclosed: List<Diagnostic>
    )

    fun tokenize(lines: List<String>, plugin: LanguagePlugin): Result {
        val symbols = mutableListOf<Symbol>()
        val unclosed = mutableListOf<Diagnostic>()

        val lineComment = plugin.lineComment
        val blockStart = plugin.blockComment.getOrNull(0)
        val blockEnd = plugin.blockComment.getOrNull(1)
        val quotes = plugin.quotes
        val blockQuotes = plugin.blockQuotes

        val bracketChars = mutableSetOf<Char>()
        for (pair in plugin.brackets) {
            pair.getOrNull(0)?.firstOrNull()?.let { bracketChars += it }
            pair.getOrNull(1)?.firstOrNull()?.let { bracketChars += it }
        }

        var inBlockComment = false
        var inBlockQuoteEnd: String? = null
        var openerLine = -1
        var openerCol = -1

        for ((li, line) in lines.withIndex()) {
            var i = 0
            while (i < line.length) {
                if (inBlockComment) {
                    val end = blockEnd ?: break
                    val endIdx = line.indexOf(end, i)
                    if (endIdx < 0) { i = line.length; break }
                    inBlockComment = false
                    i = endIdx + end.length
                    continue
                }
                val bqEnd = inBlockQuoteEnd
                if (bqEnd != null) {
                    val endIdx = line.indexOf(bqEnd, i)
                    if (endIdx < 0) { i = line.length; break }
                    inBlockQuoteEnd = null
                    i = endIdx + bqEnd.length
                    continue
                }

                val c = line[i]
                if (c == ' ' || c == '\t' || c == '\r') { i++; continue }

                if (lineComment != null && line.startsWith(lineComment, i)) break

                if (blockStart != null && blockEnd != null && line.startsWith(blockStart, i)) {
                    val endIdx = line.indexOf(blockEnd, i + blockStart.length)
                    if (endIdx < 0) {
                        inBlockComment = true
                        openerLine = li
                        openerCol = i
                        i = line.length
                        break
                    }
                    i = endIdx + blockEnd.length
                    continue
                }

                var matchedBq = false
                for (pair in blockQuotes) {
                    val start = pair.getOrNull(0) ?: continue
                    val end = pair.getOrNull(1) ?: continue
                    if (line.startsWith(start, i)) {
                        val endIdx = line.indexOf(end, i + start.length)
                        if (endIdx < 0) {
                            inBlockQuoteEnd = end
                            openerLine = li
                            openerCol = i
                            i = line.length
                        } else {
                            i = endIdx + end.length
                        }
                        matchedBq = true
                        break
                    }
                }
                if (matchedBq) continue

                var matchedQ = false
                for (q in quotes) {
                    if (line.startsWith(q, i)) {
                        var j = i + q.length
                        var closed = false
                        while (j < line.length) {
                            val cj = line[j]
                            if (cj == '\\' && j + 1 < line.length) { j += 2; continue }
                            if (line.startsWith(q, j)) { closed = true; break }
                            j++
                        }
                        if (!closed) {
                            unclosed += Diagnostic(
                                line = li, col = i, endLine = li, endCol = line.length,
                                severity = Severity.ERROR, message = "字符串未闭合"
                            )
                            i = line.length
                        } else {
                            i = j + q.length
                        }
                        matchedQ = true
                        break
                    }
                }
                if (matchedQ) continue

                if (c in bracketChars) {
                    symbols += Symbol(li, i, c)
                    i++
                    continue
                }
                i++
            }
        }

        if (inBlockComment) {
            unclosed += Diagnostic(
                line = openerLine, col = openerCol,
                endLine = openerLine, endCol = openerCol + 2,
                severity = Severity.ERROR, message = "块注释未闭合"
            )
        }
        if (inBlockQuoteEnd != null) {
            unclosed += Diagnostic(
                line = openerLine, col = openerCol,
                endLine = openerLine, endCol = openerCol + 3,
                severity = Severity.ERROR, message = "块字符串未闭合"
            )
        }
        return Result(symbols, unclosed)
    }
}
