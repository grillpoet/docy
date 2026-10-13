package com.grillpoet.docy.editor.search

/**
 * 字面量查找 / 替换。不做正则。
 */
object Finder {

    data class Match(val line: Int, val col: Int, val len: Int)

    fun findAll(lines: List<String>, query: String, caseSensitive: Boolean): List<Match> {
        if (query.isEmpty()) return emptyList()
        val result = mutableListOf<Match>()
        val cmp = if (caseSensitive) query else query.lowercase()
        lines.forEachIndexed { li, line ->
            val src = if (caseSensitive) line else line.lowercase()
            var i = 0
            while (true) {
                val idx = src.indexOf(cmp, i)
                if (idx < 0) break
                result += Match(li, idx, query.length)
                i = idx + 1
            }
        }
        return result
    }

    fun replaceAll(
        lines: MutableList<String>,
        query: String,
        replacement: String,
        caseSensitive: Boolean
    ): Int {
        if (query.isEmpty()) return 0
        val cmp = if (caseSensitive) query else query.lowercase()
        var count = 0
        for (i in lines.indices) {
            val src = if (caseSensitive) lines[i] else lines[i].lowercase()
            val sb = StringBuilder()
            var j = 0
            while (true) {
                val idx = src.indexOf(cmp, j)
                if (idx < 0) {
                    sb.append(lines[i], j, lines[i].length)
                    break
                }
                sb.append(lines[i], j, idx).append(replacement)
                j = idx + query.length
                count++
            }
            lines[i] = sb.toString()
        }
        return count
    }
}