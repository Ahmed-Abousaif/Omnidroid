package com.omnidroid.app.mobile.feature.library

import androidx.paging.PagingConfig

/** A few viewports in memory, with Room offset jumps for distant positions. */
internal object LibraryPaging {
    const val PAGE_SIZE = 60
    const val MAX_LOADED_GAMES = 600

    fun config() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE * 2,
        prefetchDistance = PAGE_SIZE / 2,
        enablePlaceholders = true,
        maxSize = MAX_LOADED_GAMES,
        jumpThreshold = PAGE_SIZE * 3,
    )
}
