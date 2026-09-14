package com.swordfish.lemuroid.app.mobile.feature.library

import com.swordfish.lemuroid.lib.library.MetaSystemID

sealed class LibraryFilter {
    data object All : LibraryFilter()

    data object Favorites : LibraryFilter()

    data class System(val metaSystemID: MetaSystemID) : LibraryFilter()
}
