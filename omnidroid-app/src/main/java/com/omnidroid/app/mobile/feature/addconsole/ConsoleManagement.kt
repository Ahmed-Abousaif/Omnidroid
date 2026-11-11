package com.omnidroid.app.mobile.feature.addconsole

import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.lib.library.MetaSystemID

/** Breakpoints use the available content area, including the user's UI scale. */
internal fun useConsoleSplitLayout(
    width: Float,
    height: Float,
): Boolean = width >= 720f || (width >= 560f && width >= height * 1.25f)

internal fun consoleCatalogue(
    consoles: List<ConsoleItem>,
    query: String,
    title: (MetaSystemID) -> String,
): List<ConsoleItem> {
    val matching =
        consoles.filter { console ->
            query.isBlank() || title(console.metaSystemID).contains(query.trim(), ignoreCase = true) ||
                console.coreDisplayName?.contains(query.trim(), ignoreCase = true) == true
        }
    val byName = compareBy<ConsoleItem, String>(String.CASE_INSENSITIVE_ORDER) { title(it.metaSystemID) }
    return matching.sortedWith(
        compareBy<ConsoleItem> {
            when {
                it.needsAttention -> 0
                it.registeredOrHasGames -> 1
                else -> 2
            }
        }.then(byName),
    )
}

internal fun resolveConsoleSelection(
    selected: MetaSystemID?,
    consoles: List<ConsoleItem>,
): MetaSystemID? =
    selected?.takeIf { id -> consoles.any { it.metaSystemID == id } } ?: consoles.firstOrNull()?.metaSystemID
