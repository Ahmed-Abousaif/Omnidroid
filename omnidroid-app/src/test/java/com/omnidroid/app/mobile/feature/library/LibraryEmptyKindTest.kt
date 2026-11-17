package com.omnidroid.app.mobile.feature.library

import com.omnidroid.lib.library.MetaSystemID
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryEmptyKindTest {
    @Test
    fun noFolderComesFirstForTheMainList() {
        assertEquals(LibraryEmptyKind.NO_FOLDER, libraryEmptyKind(false, LibraryFilter.All, ""))
        assertEquals(LibraryEmptyKind.NO_FOLDER, libraryEmptyKind(false, LibraryFilter.System(MetaSystemID.NES), ""))
    }

    @Test
    fun folderWithoutGames() {
        assertEquals(LibraryEmptyKind.NO_GAMES, libraryEmptyKind(true, LibraryFilter.All, ""))
        assertEquals(LibraryEmptyKind.NO_CONSOLE_GAMES, libraryEmptyKind(true, LibraryFilter.System(MetaSystemID.NES), ""))
    }

    @Test
    fun searchAndFavoritesExplainThemselves() {
        assertEquals(LibraryEmptyKind.NO_SEARCH_RESULTS, libraryEmptyKind(true, LibraryFilter.All, "zelda"))
        assertEquals(LibraryEmptyKind.NO_SEARCH_RESULTS, libraryEmptyKind(false, LibraryFilter.Favorites, "zelda"))
        assertEquals(LibraryEmptyKind.NO_FAVORITES, libraryEmptyKind(true, LibraryFilter.Favorites, ""))
        assertEquals(LibraryEmptyKind.NO_FAVORITES, libraryEmptyKind(false, LibraryFilter.Favorites, " "))
    }
}
