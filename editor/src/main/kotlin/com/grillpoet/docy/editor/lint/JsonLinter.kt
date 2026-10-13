package com.grillpoet.docy.editor.lint

import kotlinx.serialization.json.Json

/**
 * JSON 格式检查。用 kotlinx.serialization 试 parse。
 */
class JsonLinter : Diagnostician {

    override suspend fun diagnose(source: SourceView): List<Diagnostic> {
        val text = source.text()
        if (text.isBlank()) return emptyList()

        return try {
            Json.parseToJsonElement(text)
            emptyList()
        } catch (e: Exception) {
            val msg = e.message ?: "JSON 格式错误"
            val offset = Regex("offset (\\d+)")
                .find(msg)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            val (line, col) = if (offset != null && offset <= text.length) {
                offsetToLineCol(text, offset)
            } else {
                0 to 0
            }

            listOf(
                Diagnostic(
                    line = line,
                    col = col,
                    endLine = line,
                    endCol = col + 1,
                    severity = Severity.ERROR,
                    message = "JSON 格式错误"
                )
            )
        }
    }

    private fun offsetToLineCol(text: String, offset: Int): Pair<Int, Int> {
        var line = 0
        var lastNl = -1
        val end = offset.coerceAtMost(text.length)
        for (i in 0 until end) {
            if (text[i] == '\n') {
                line++
                lastNl = i
            }
        }
        return line to (offset - lastNl - 1)
    }
}
