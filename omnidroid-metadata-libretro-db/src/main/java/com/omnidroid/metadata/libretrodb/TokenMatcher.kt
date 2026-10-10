package com.omnidroid.metadata.libretrodb

internal object TokenMatcher {
    private val STOP_WORDS =
        setOf("the", "a", "an", "and", "of", "in", "to", "for", "with", "super", "ultra", "mega", "new")

    fun selectSignificantToken(query: String): String? {
        val tokens = tokenize(query).filter { it.length >= 3 }
        return tokens.filterNot { it in STOP_WORDS }.maxByOrNull { it.length }
            ?: tokens.maxByOrNull { it.length }
    }

    fun bestScore(
        query: String,
        candidate: String,
    ): Int {
        val tokensQ = tokenize(query)
        val tokensC = tokenize(candidate)
        if (tokensQ.isEmpty() || tokensC.isEmpty()) return 0

        val setScore = tokenSetScore(tokensQ, tokensC)
        val sortScore = tokenSortScore(tokensQ, tokensC)
        val directRatio = levenshteinRatio(query, candidate)

        return maxOf(setScore, sortScore, directRatio)
    }

    private fun tokenSetScore(
        tokensA: Set<String>,
        tokensB: Set<String>,
    ): Int {
        val intersection = tokensA.intersect(tokensB)
        if (intersection.isEmpty()) return 0
        val minSize = minOf(tokensA.size, tokensB.size)
        val coverage = (intersection.size.toDouble() / minSize) * 100
        return coverage.toInt()
    }

    private fun tokenSortScore(
        tokensA: Set<String>,
        tokensB: Set<String>,
    ): Int {
        val sortedA = tokensA.sorted().joinToString(" ")
        val sortedB = tokensB.sorted().joinToString(" ")
        return levenshteinRatio(sortedA, sortedB)
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()

    fun levenshteinRatio(
        a: String,
        b: String,
    ): Int {
        if (a == b) return 100
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 100
        val dist = levenshteinDistance(a, b)
        return ((1.0 - dist.toDouble() / maxLen) * 100).toInt()
    }

    private fun levenshteinDistance(
        a: String,
        b: String,
    ): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] =
                    minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                    )
            }
        }
        return dp[a.length][b.length]
    }
}
