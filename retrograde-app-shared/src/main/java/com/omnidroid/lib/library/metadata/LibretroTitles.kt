package com.omnidroid.lib.library.metadata

/**
 * libretro-db stores titles in sort order ("Lion King, The"). Cover art files use that form;
 * the UI should show the natural reading order ("The Lion King").
 */
object LibretroTitles {
    private val ARTICLE_SORTED =
        Regex("""^(.*),\s*(The|A|An)(\b.*)$""", RegexOption.IGNORE_CASE)

    fun isArticleSorted(name: String): Boolean = ARTICLE_SORTED.containsMatchIn(name)

    fun humanize(name: String): String {
        val match = ARTICLE_SORTED.matchEntire(name.trim()) ?: return name
        val body = match.groupValues[1].trim()
        val article = match.groupValues[2]
        val rest = match.groupValues[3]
        if (body.isEmpty()) return name
        return "$article $body$rest".replace(Regex("""\s{2,}"""), " ").trim()
    }
}
