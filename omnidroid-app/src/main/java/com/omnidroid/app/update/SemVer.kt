package com.omnidroid.app.update

/**
 * Lightweight semantic version compare. Strips a leading `v`/`V` and any `-`/`+` suffix
 * (e.g. `2.6.0-DEBUG` → `2.6.0`) before comparing numeric components.
 */
object SemVer {
    fun compare(
        a: String,
        b: String,
    ): Int {
        val left = parse(a)
        val right = parse(b)
        val len = maxOf(left.size, right.size)
        for (i in 0 until len) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    fun isNewerThan(
        candidate: String,
        current: String,
    ): Boolean = compare(candidate, current) > 0

    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("v", ignoreCase = true)) {
            s = s.substring(1)
        }
        val cut = s.indexOfFirst { it == '-' || it == '+' }
        if (cut >= 0) {
            s = s.substring(0, cut)
        }
        return s.trim()
    }

    private fun parse(raw: String): List<Int> {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return listOf(0)
        return normalized.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
