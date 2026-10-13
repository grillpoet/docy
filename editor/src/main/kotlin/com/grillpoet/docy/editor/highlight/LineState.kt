package com.grillpoet.docy.editor.highlight

data class LineState(
    val inBlockComment: String? = null,
    val inBlockString: String? = null
)

enum class SpanKind {
    KEYWORD,
    TYPE,
    FUNCTION,
    LITERAL,
    NUMBER,
    STRING,
    COMMENT,
    OPERATOR,
    LIBRARY,
    ANNOTATION,
    PUNCTUATION
}

data class Span(val start: Int, val end: Int, val kind: SpanKind)
