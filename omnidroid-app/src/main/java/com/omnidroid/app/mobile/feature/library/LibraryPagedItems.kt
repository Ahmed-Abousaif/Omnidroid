package com.omnidroid.app.mobile.feature.library

import androidx.compose.runtime.Stable
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.omnidroid.lib.library.db.entity.Game

/** One index/key contract for every paged layout, including the pinned Continue tile. */
@Stable
internal class LibraryPagedItems(
    private val games: LazyPagingItems<Game>,
    private val featured: Game?,
) {
    private val offset = if (featured == null) 0 else 1
    private val gameKey = games.itemKey { it.id }
    private val gameContentType = games.itemContentType { "game" }

    val count: Int get() = games.itemCount + offset

    // Paging's placeholder keys have their own type, so they cannot collide with game IDs.
    val key: (Int) -> Any = { index ->
        if (isFeatured(index)) "continue-${featured!!.id}" else gameKey(index - offset)
    }

    val contentType: (Int) -> Any? = { index ->
        if (isFeatured(index)) "continue" else gameContentType(index - offset)
    }

    fun isFeatured(index: Int) = featured != null && index == 0

    operator fun get(index: Int): Game? =
        if (isFeatured(index)) featured else games[index - offset]

    /** Prefetch covers without sending additional data-loading hints into Paging. */
    fun peek(index: Int): Game? = when {
        index !in 0 until count -> null
        isFeatured(index) -> featured
        else -> games.peek(index - offset)
    }
}
