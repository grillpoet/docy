package com.grillpoet.docy.editor.highlight

import androidx.compose.ui.graphics.Color
import com.grillpoet.docy.core.plugin.LanguagePlugin

object Highlighter {

    data class Palette(
        val keyword: Color,
        val type: Color,
        val function: Color,
        val literal: Color,
        val number: Color,
        val string: Color,
        val comment: Color,
        val operator: Color,
        val library: Color,
        val annotation: Color,
        val punctuation: Color
    )

    fun highlightLine(
        line: String,
        enter: LineState,
        plugin: LanguagePlugin?
    ): Pair<List<Span>, LineState> {
        if (plugin == null || plugin.rules.isEmpty()) {
            return emptyList<Span>() to LineState()
        }

        val spans = mutableListOf<Span>()
        var state = enter
        var i = 0
        val n = line.length

        val sortedOperators = if (plugin.operators.isEmpty()) emptyList()
                              else plugin.operators.sortedByDescending { it.length }
        val keywordSet = plugin.rules.flatMap { it.words }.toSet()

        while (i < n) {
            val carry = state.inBlockComment ?: state.inBlockString
            if (carry != null) {
                val isComment = state.inBlockComment != null
                val kind = if (isComment) SpanKind.COMMENT else SpanKind.STRING
                val endIdx = line.indexOf(carry, i)
                if (endIdx < 0) {
                    spans += Span(i, n, kind)
                    return spans to state
                }
                spans += Span(i, endIdx + carry.length, kind)
                i = endIdx + carry.length
                state = if (isComment) state.copy(inBlockComment = null)
                        else state.copy(inBlockString = null)
                continue
            }

            val hit = matchFirst(line, i, plugin)
            if (hit != null) {
                when (hit) {
                    is Hit.LineToEnd -> {
                        spans += Span(i, n, hit.kind)
                        return spans to state
                    }
                    is Hit.Block -> {
                        val endIdx = line.indexOf(hit.end, i + hit.start.length)
                        if (endIdx < 0) {
                            spans += Span(i, n, hit.kind)
                            val next = if (hit.kind == SpanKind.COMMENT)
                                state.copy(inBlockComment = hit.end)
                            else
                                state.copy(inBlockString = hit.end)
                            return spans to next
                        }
                        val endExcl = endIdx + hit.end.length
                        spans += Span(i, endExcl, hit.kind)
                        i = endExcl
                    }
                    is Hit.Quoted -> {
                        val start = i
                        var j = i + hit.quote.length
                        var closed = false
                        while (j < n) {
                            val c = line[j]
                            if (c == '\\' && j + 1 < n) { j += 2; continue }
                            if (line.startsWith(hit.quote, j)) {
                                val endExcl = j + hit.quote.length
                                spans += Span(start, endExcl, SpanKind.STRING)
                                i = endExcl
                                closed = true
                                break
                            }
                            j++
                        }
                        if (!closed) {
                            spans += Span(start, n, SpanKind.STRING)
                            return spans to state
                        }
                    }
                    is Hit.Word -> {
                        spans += Span(i, i + hit.word.length, hit.kind)
                        i += hit.word.length
                    }
                    is Hit.Regex -> {
                        spans += Span(hit.start, hit.end, hit.kind)
                        i = hit.end
                    }
                }
                continue
            }

            val c = line[i]

            if (c == '@' && i + 1 < n && (line[i + 1].isLetter() || line[i + 1] == '_')) {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                spans += Span(i, j, SpanKind.ANNOTATION)
                i = j
                continue
            }

            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                val word = line.substring(i, j)
                var k = j
                while (k < n && (line[k] == ' ' || line[k] == '\t')) k++
                if (k < n && line[k] == '(' && word !in keywordSet) {
                    spans += Span(i, j, SpanKind.FUNCTION)
                }
                i = j
                continue
            }

            if (sortedOperators.isNotEmpty()) {
                val op = sortedOperators.firstOrNull { line.startsWith(it, i) }
                if (op != null) {
                    spans += Span(i, i + op.length, SpanKind.OPERATOR)
                    i += op.length
                    continue
                }
            }

            if (plugin.punctuation.isNotEmpty() && plugin.punctuation.indexOf(c) >= 0) {
                spans += Span(i, i + 1, SpanKind.PUNCTUATION)
                i++
                continue
            }

            i++
        }
        return spans to state
    }

    private sealed class Hit {
        data class LineToEnd(val kind: SpanKind) : Hit()
        data class Block(val start: String, val end: String, val kind: SpanKind) : Hit()
        data class Quoted(val quote: String) : Hit()
        data class Word(val word: String, val kind: SpanKind) : Hit()
        data class Regex(val start: Int, val end: Int, val kind: SpanKind) : Hit()
    }

    private fun matchFirst(line: String, i: Int, plugin: LanguagePlugin): Hit? {
        val c = line[i]
        for (rule in plugin.rules) {
            when (rule.kind) {
                "comment" -> {
                    when (rule.type) {
                        "line" -> {
                            val s = rule.start ?: continue
                            if (line.startsWith(s, i)) return Hit.LineToEnd(SpanKind.COMMENT)
                        }
                        "block" -> {
                            val s = rule.start ?: continue
                            val e = rule.end ?: continue
                            if (line.startsWith(s, i)) return Hit.Block(s, e, SpanKind.COMMENT)
                        }
                    }
                }
                "string" -> {
                    when (rule.type) {
                        "block" -> {
                            val s = rule.start ?: continue
                            val e = rule.end ?: continue
                            if (line.startsWith(s, i)) return Hit.Block(s, e, SpanKind.STRING)
                        }
                        "quote" -> {
                            for (q in rule.quotes) {
                                if (line.startsWith(q, i)) return Hit.Quoted(q)
                            }
                        }
                    }
                }
                "number" -> {
                    if (!c.isDigit()) continue
                    val rx = rule.regex ?: "\\d+"
                    val m = Regex(rx).find(line, i) ?: continue
                    if (m.range.first == i) {
                        return Hit.Regex(i, m.range.last + 1, SpanKind.NUMBER)
                    }
                }
                "keyword", "type", "literal", "library" -> {
                    if (!c.isLetter() && c != '_') continue
                    val w = readWord(line, i) ?: continue
                    if (w in rule.words) {
                        val k = when (rule.kind) {
                            "keyword" -> SpanKind.KEYWORD
                            "type" -> SpanKind.TYPE
                            "literal" -> SpanKind.LITERAL
                            "library" -> SpanKind.LIBRARY
                            else -> SpanKind.KEYWORD
                        }
                        return Hit.Word(w, k)
                    }
                }
            }
        }
        return null
    }

    private fun readWord(line: String, start: Int): String? {
        var j = start
        while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
        if (j == start) return null
        return line.substring(start, j)
    }
}
