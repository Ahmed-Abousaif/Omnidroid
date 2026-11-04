package com.omnidroid.metadata.libretrodb

import com.omnidroid.lib.library.metadata.LibretroTitles
import java.util.Locale

/**
 * Filename → normalizedName matching helpers.
 *
 * Must stay aligned with Keys.humanize + Keys.normalizeTitle in the slice builder:
 * DB rows are stored humanized then normalized, so article-sorted dump names like
 * "Legend of Zelda, The - ..." have to be humanized before comparison.
 */
internal object LibretroTitleMatching {
    private val commonAliases =
        mapOf(
            "megaman" to "mega man",
            "thps" to "tony hawk's pro skater",
            "sf2" to "street fighter ii",
            "sfii" to "street fighter ii",
            "mk" to "mortal kombat",
            "smb" to "super mario bros",
            "tmnt" to "teenage mutant ninja turtles",
            "zelda" to "the legend of zelda",
            "castlevania sotn" to "castlevania symphony of the night",
            "re1" to "resident evil",
            "re2" to "resident evil 2",
            "re3" to "resident evil 3",
            "bros" to "brothers",
            "pacman" to "pac man",
            "ddragon" to "double dragon",
        )

    /** Prefer these when a dump lists multiple regions (e.g. USA, Asia). */
    private val REGION_TAGS =
        listOf(
            "usa",
            "europe",
            "japan",
            "world",
            "asia",
            "australia",
            "brazil",
            "korea",
            "china",
            "germany",
            "france",
            "spain",
            "italy",
            "sweden",
            "netherlands",
        )

    private val regionOrTagSuffix =
        Regex(
            """\s*\((?:usa|europe|japan|world|asia|australia|brazil|korea|china|germany|france|spain|italy|sweden|netherlands|rev\s*\d+|v\d+\.\d+|beta|proto|track\s*\d+|en|fr|es|de|it|nl|pt|sv|no|da|fi|zh|ko|ja)[^)]*\)$""",
            RegexOption.IGNORE_CASE,
        )

    private val parenGroup = Regex("""\s*\(([^)]*)\)""")

    fun normalizeTitle(value: String): String? {
        val humanized = LibretroTitles.humanize(value)
        val cleaned =
            humanized
                .lowercase(Locale.US)
                .replace(Regex("""\.\w{1,4}$"""), "")
                .replace(Regex("""\s*\[[^\]]*\]"""), "")
                .replace('_', ' ')
                // Keep tag separators so "(En,Fr,Es)" stays tokenizable.
                .replace(',', ' ')
                .replace(Regex("""[-:]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    /** Normalized title with region/language parentheticals removed (for fuzzy scoring). */
    fun stripTags(normalized: String): String =
        normalized
            .replace(Regex("""\s*\([^)]*\)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun extractTags(normalized: String): Set<String> {
        val tags = linkedSetOf<String>()
        parenGroup.findAll(normalized).forEach { match ->
            match.groupValues[1]
                .split(Regex("""[\s,+/]+"""))
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.isNotEmpty() }
                .forEach { tags += it }
        }
        return tags
    }

    /**
     * Higher is better. Rewards overlapping region/language tags from the dump name
     * so "(USA, Asia)" prefers the USA DAT row over Europe.
     */
    fun regionScore(
        queryNormalized: String,
        candidateNormalized: String,
    ): Int {
        val wanted = extractTags(queryNormalized)
        if (wanted.isEmpty()) return 0
        val have = extractTags(candidateNormalized)
        if (have.isEmpty()) return 0
        val overlap = wanted.intersect(have).size
        val extra = have.minus(wanted).size
        return overlap * 10 - extra
    }

    fun titleVariants(value: String): List<String> {
        val base = normalizeTitle(value) ?: return emptyList()
        val variants = linkedSetOf(base)

        // 1. Article variations ("the", "a", "an")
        if (base.startsWith("the ")) {
            variants += base.removePrefix("the ").trim()
        } else {
            variants += "the $base"
        }
        if (base.endsWith(" the")) {
            variants += "the " + base.removeSuffix(" the").trim()
            variants += base.removeSuffix(" the").trim()
        }

        // 2. Region-specific cores first (before bare/prefix titles) so a
        // "(USA, Asia)" dump exact-matches USA instead of prefix-hitting Europe.
        val coreTitle = stripTags(base)
        if (coreTitle.isNotEmpty()) {
            val tags = extractTags(base)
            for (region in REGION_TAGS) {
                if (region in tags) {
                    variants += "$coreTitle ($region)"
                    if (coreTitle.startsWith("the ")) {
                        variants += "${coreTitle.removePrefix("the ").trim()} ($region)"
                    }
                }
            }
        }

        // 3. Peel trailing region / language tags, then bare title
        var withoutTags = base
        while (true) {
            val next = withoutTags.replace(regionOrTagSuffix, "").trim()
            if (next == withoutTags) break
            withoutTags = next
            if (withoutTags.isNotEmpty()) {
                variants += withoutTags
                if (withoutTags.startsWith("the ")) {
                    variants += withoutTags.removePrefix("the ").trim()
                }
            }
        }
        if (coreTitle.isNotEmpty() && coreTitle != base) {
            variants += coreTitle
            if (!coreTitle.startsWith("the ")) {
                variants += "the $coreTitle"
            } else {
                variants += coreTitle.removePrefix("the ").trim()
            }
        }

        // 4. Common Alias Mapping
        for (v in variants.toList()) {
            for ((alias, canonical) in commonAliases) {
                val aliased = applyAlias(v, alias, canonical) ?: continue
                variants += aliased
            }
        }

        return variants.filter { it.isNotBlank() }
    }

    private fun applyAlias(
        value: String,
        alias: String,
        canonical: String,
    ): String? {
        // When the expansion contains the alias (e.g. zelda → the legend of zelda),
        // only rewrite titles that start with the alias so we don't turn
        // "the legend of zelda ..." into "the legend of the legend of zelda ...".
        if (canonical.contains(alias)) {
            if (value != alias && !value.startsWith("$alias ") && !value.startsWith("$alias (")) {
                return null
            }
            return (canonical + value.removePrefix(alias)).trim()
        }
        if (!value.contains(alias)) return null
        return value.replace(alias, canonical).trim()
    }
}
