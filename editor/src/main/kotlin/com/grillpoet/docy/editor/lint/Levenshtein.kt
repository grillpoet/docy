package com.grillpoet.docy.editor.lint

object Levenshtein {
    fun distance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = curr; curr = t
        }
        return prev[n]
    }

    fun suggest(input: String, candidates: Collection<String>, maxDist: Int = 2): List<String> =
        candidates.map { it to distance(input, it) }
            .filter { it.second in 1..maxDist }
            .sortedBy { it.second }
            .take(3)
            .map { it.first }
}