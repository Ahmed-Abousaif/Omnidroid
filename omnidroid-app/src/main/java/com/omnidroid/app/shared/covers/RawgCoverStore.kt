package com.omnidroid.app.shared.covers

import java.util.concurrent.ConcurrentHashMap

/** In-memory RAWG cover URLs for library grids; populated when enrichment runs / bootstrap loads. */
object RawgCoverStore {
    private val covers = ConcurrentHashMap<Int, String>()

    fun put(
        gameId: Int,
        url: String,
    ) {
        covers[gameId] = url
    }

    fun get(gameId: Int): String? = covers[gameId]

    fun replaceAll(entries: Map<Int, String>) {
        covers.clear()
        covers.putAll(entries)
    }

    fun clear() {
        covers.clear()
    }
}
