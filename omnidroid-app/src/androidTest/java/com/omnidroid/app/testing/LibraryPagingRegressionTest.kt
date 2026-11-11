package com.omnidroid.app.testing

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.omnidroid.app.mobile.feature.library.LibraryLayout
import com.omnidroid.app.mobile.feature.library.LibraryPagedContent
import com.omnidroid.app.mobile.feature.library.LibraryPagedItems
import com.omnidroid.app.mobile.feature.library.LibraryPaging
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.calculateAdaptiveLayoutSpec
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryPagingRegressionTest {
    @get:Rule
    val compose = createAndroidComposeRule<LibraryStressActivity>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun pinnedGameAndPlaceholdersStayUniqueAcrossJumpsAndPageEviction() {
        val total = 20_000
        val loads = java.util.concurrent.atomic.AtomicInteger()
        val sources = java.util.concurrent.atomic.AtomicInteger()
        val pager = Pager(LibraryPaging.config()) {
            sources.incrementAndGet()
            FixtureSource(total, loads)
        }
        val layout = mutableStateOf(LibraryLayout.LIST)
        var presented: LazyPagingItems<Game>? = null
        compose.setContent {
            val games = pager.flow.collectAsLazyPagingItems()
            SideEffect { presented = games }
            FixtureLibrary(games, layout.value, 2)
        }
        compose.waitUntil(10_000) { presented?.itemSnapshotList?.items?.isNotEmpty() == true }
        compose.runOnIdle {
            val items = LibraryPagedItems(presented!!, fixture(1))
            val keys = (0 until items.count).map(items.key)
            assertEquals(total + 1, keys.size)
            assertEquals(
                "Pinned row, loaded IDs and placeholder positions must never share keys",
                keys.size, keys.toSet().size,
            )
        }

        // A distant jump must refresh around the destination, not load all preceding pages.
        jumpTo(10_000)
        assertTrue("Distant navigation should use Room-style offset jumping", loads.get() < 20)
        val generation = sources.get()

        // Stay below the jump threshold: eviction must work within one source generation,
        // rather than merely emptying the cache as a side effect of refresh/jumping.
        for (index in 10_150..14_000 step 150) jumpTo(index)
        compose.waitUntil(10_000) {
            presented!!.itemSnapshotList.items.size <= LibraryPaging.MAX_LOADED_GAMES + LibraryPaging.PAGE_SIZE * 2
        }
        compose.runOnIdle {
            assertEquals("Nearby browsing must not recreate the source", generation, sources.get())
            assertNull("Previously visited pages must be evicted", presented!!.peek(9_999))
            val items = LibraryPagedItems(presented!!, fixture(1))
            val keys = (0 until items.count).map(items.key)
            assertEquals(
                "Keys must stay unique when loaded games become placeholders again",
                keys.size, keys.toSet().size,
            )
        }

        // Both renderers use the same paging/key contract, including after a mode change.
        compose.runOnUiThread { layout.value = LibraryLayout.GRID }
        jumpTo(19_999)
        compose.runOnUiThread { layout.value = LibraryLayout.LIST }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText(title(13_900)) or hasContentDescription(title(13_900)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        jumpTo(1)
    }

    @Test
    fun landscapeContinueAndOrdinaryCoverSettleAtEveryZoom() {
        compose.runOnUiThread {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        val zoom = mutableIntStateOf(0)
        val layout = mutableStateOf(LibraryLayout.GRID)
        val data = flowOf(PagingData.from((1..20).map(::fixture)))
        compose.setContent { FixtureLibrary(data.collectAsLazyPagingItems(), layout.value, zoom.intValue) }
        for (density in 0..2) {
            compose.runOnUiThread { zoom.intValue = density }
            // Duplicate shared-bounds matches previously kept lookahead measuring forever.
            compose.waitForIdle()
            assertTrue(compose.onAllNodes(hasContentDescription(title(1))).fetchSemanticsNodes().isNotEmpty())
        }
        compose.runOnUiThread { layout.value = LibraryLayout.LIST }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText(title(1))).fetchSemanticsNodes().isNotEmpty())
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun FixtureLibrary(games: LazyPagingItems<Game>, layout: LibraryLayout, zoom: Int) {
        AppTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalAdaptiveLayout provides calculateAdaptiveLayoutSpec(maxWidth, maxHeight),
                ) {
                    SharedTransitionLayout {
                        val shared = this
                        AnimatedVisibility(true) {
                            LibraryPagedContent(
                                games = games,
                                featured = fixture(1), // Also appears as the first ordinary item.
                                layout = layout,
                                zoomDensity = zoom,
                                portrait = maxHeight > maxWidth,
                                sharedTransitionScope = shared,
                                animatedVisibilityScope = this,
                                firstItemRequester = remember { FocusRequester() },
                                onGameClick = {}, onContinueClick = {}, onGameLongClick = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun jumpTo(index: Int) {
        // Index zero is the pinned Continue item, so ordinary index N displays game N.
        fun scroll() {
            val viewport = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
                .fetchSemanticsNodes().maxBy { it.boundsInRoot.width * it.boundsInRoot.height }
            compose.onNode(SemanticsMatcher("library viewport") { it.id == viewport.id }).performScrollToIndex(index)
        }
        scroll()
        var nextRetry = android.os.SystemClock.elapsedRealtime() + 500
        compose.waitUntil(15_000) {
            val found = compose.onAllNodes(hasText(title(index)) or hasContentDescription(title(index)))
                .fetchSemanticsNodes().isNotEmpty()
            if (!found && android.os.SystemClock.elapsedRealtime() >= nextRetry) {
                scroll()
                nextRetry = android.os.SystemClock.elapsedRealtime() + 500
            }
            found
        }
    }

    private class FixtureSource(
        private val count: Int,
        private val loads: java.util.concurrent.atomic.AtomicInteger,
    ) : PagingSource<Int, Game>() {
        override val jumpingSupported = true

        override fun getRefreshKey(state: PagingState<Int, Game>): Int? =
            state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Game> {
            loads.incrementAndGet()
            delay(25) // Keep placeholders observable while the actual renderer is laid out.
            val key = (params.key ?: 0).coerceIn(0, count)
            val prepend = params is LoadParams.Prepend
            val start = if (prepend) (key - params.loadSize).coerceAtLeast(0) else key
            val end = if (prepend) key else (start + params.loadSize).coerceAtMost(count)
            return LoadResult.Page(
                data = (start until end).map { fixture(it + 1) },
                prevKey = if (start == 0) null else start,
                nextKey = if (end == count) null else end,
                itemsBefore = start,
                itemsAfter = count - end,
            )
        }
    }

    companion object {
        private fun title(index: Int) = "Paging fixture ${index.toString().padStart(5, '0')}"

        private fun fixture(index: Int) = Game(
            id = index, title = title(index), fileName = "$index.nes",
            fileUri = "content://library-paging-fixture/$index", systemId = "nes",
            developer = null, coverFrontUrl = null, lastIndexedAt = 0,
        )
    }
}
