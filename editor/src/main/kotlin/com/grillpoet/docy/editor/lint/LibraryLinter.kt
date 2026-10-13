package com.grillpoet.docy.editor.lint

import com.grillpoet.docy.core.plugin.LibraryPlugin
import com.grillpoet.docy.editor.highlight.Highlighter
import com.grillpoet.docy.editor.highlight.LineState
import com.grillpoet.docy.editor.highlight.SpanKind

class LibraryLinter(private val libraries: List<LibraryPlugin>) : Diagnostician {

    override suspend fun diagnose(source: SourceView): List<Diagnostic> {
        if (libraries.isEmpty()) return emptyList()

        val fullApis = libraries.flatMap { it.api }.toSet()
        if (fullApis.isEmpty()) return emptyList()

        val topNamespaces = fullApis
            .mapNotNull { it.substringBefore('.', "").takeIf { s -> s.isNotEmpty() } }
            .toSet()

        val out = mutableListOf<Diagnostic>()
        var state = LineState()

        source.lines.forEachIndexed { li, line ->
            val (spans, next) = Highlighter.highlightLine(line, state, null)
            state = next
            val skipRanges = spans.filter {
                it.kind == SpanKind.COMMENT || it.kind == SpanKind.STRING
            }.map { it.start until it.end }

            var i = 0
            while (i < line.length) {
                if (skipRanges.any { i in it }) { i++; continue }

                if (line[i].isLetter() || line[i] == '_') {
                    val start = i
                    while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '.')) {
                        i++
                    }
                    val chain = line.substring(start, i)
                    val top = chain.substringBefore('.')

                    if (chain.contains('.') && top in topNamespaces && chain !in fullApis) {
                        val suggestion = nearestApi(chain, fullApis)
                        val msg = if (suggestion != null) {
                            "未知的 $top API：$chain，可能是 $suggestion"
                        } else {
                            "未知的 $top API：$chain"
                        }
                        out += Diagnostic(
                            line = li,
                            col = start,
                            endLine = li,
                            endCol = i,
                            severity = Severity.WARNING,
                            message = msg
                        )
                    }
                } else {
                    i++
                }
            }
        }
        return out
    }

    private fun nearestApi(input: String, candidates: Set<String>): String? {
        val top = input.substringBefore('.')
        val sameNs = candidates.filter { it.substringBefore('.') == top }
        if (sameNs.isEmpty()) return null
        val best = sameNs.minByOrNull { Levenshtein.distance(input, it) } ?: return null
        val dist = Levenshtein.distance(input, best)
        return if (dist in 1..2) best else null
    }
}
