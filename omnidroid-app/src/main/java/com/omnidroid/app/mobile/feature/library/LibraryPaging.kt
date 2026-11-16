package com.omnidroid.app.mobile.feature.library

import androidx.paging.PagingConfig

/** A few viewports in memory, with Room offset jumps for distant positions. */
internal object LibraryPaging {
    const val PAGE_SIZE = 60
    const val MAX_LOADED_GAMES = 600

    fun config() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE * 2,
        // A full page ahead so fast flings reach loaded rows instead of empty placeholders.
        prefetchDistance = PAGE_SIZE,
        enablePlaceholders = true,
        maxSize = MAX_LOADED_GAMES,
        jumpThreshold = PAGE_SIZE * 3,
    )
}
