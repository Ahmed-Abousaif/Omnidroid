package com.omnidroid.app.mobile.feature.library.gamemenu

import android.net.Uri
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.GameCloudSyncOverride

/** Library effects stay at the host boundary; rows only describe intent. */
data class LibraryGameMenuActions(
    val play: (Game) -> Unit,
    val restart: (Game) -> Unit,
    val favorite: (Game, Boolean) -> Unit,
    val cloudOverride: (Game, GameCloudSyncOverride) -> Unit,
    val frameSpeed: (Game, Int) -> Unit,
    val syncNow: (Game) -> Unit,
    val setThumbnail: (Game, Uri) -> Unit,
    val removeThumbnail: (Game) -> Unit,
    val setCoverUrl: (Game, String) -> Unit,
    val fixMatch: (Game, String, String) -> Unit,
    val setName: (Game, String) -> Unit,
    val removeName: (Game) -> Unit,
)
