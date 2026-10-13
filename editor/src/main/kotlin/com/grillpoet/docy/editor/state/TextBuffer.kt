package com.grillpoet.docy.editor.state

/**
 * 按行存储的文本缓冲。
 */
class TextBuffer {
    private val lines = mutableListOf<String>()

    val lineCount: Int get() = lines.size
    fun line(n: Int): String = lines[n]
    fun allLines(): List<String> = lines

    fun setText(text: String, eol: String = "\n") {
        lines.clear()
        lines.addAll(text.split(eol))
    }

    fun serialize(eol: String = "\n"): String = lines.joinToString(eol)

    fun replaceLine(n: Int, content: String) { lines[n] = content }
    fun insertLine(n: Int, content: String) { lines.add(n, content) }
    fun removeLine(n: Int) { lines.removeAt(n) }
}

enum class EolStyle(val value: String) {
    LF("\n"), CRLF("\r\n"), CR("\r");

    companion object {
        fun detect(text: String): EolStyle =
            when {
                text.contains("\r\n") -> CRLF
                text.contains("\r") -> CR
                else -> LF
            }
    }
}