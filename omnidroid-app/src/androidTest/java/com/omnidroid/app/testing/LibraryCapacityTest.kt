package com.omnidroid.app.testing

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.omnidroid.app.mobile.feature.library.LibraryFilter
import com.omnidroid.app.mobile.feature.library.LibraryLayout
import com.omnidroid.app.mobile.feature.library.LibraryScreen
import com.omnidroid.app.mobile.feature.library.LibraryStartupStore
import com.omnidroid.app.mobile.feature.library.LibraryViewModel
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.calculateAdaptiveLayoutSpec
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import java.io.File
import org.junit.Rule
import org.junit.Test

/** One fresh instrumentation process per (count, mode, orientation), driven by the host script. */
class LibraryCapacityTest {
    @get:Rule
    val compose = createAndroidComposeRule<LibraryStressActivity>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun capacity() {
        val args = InstrumentationRegistry.getArguments()
        val count = args.getString("games", "1000").toInt()
        require(count in 1..10_000_000)
        val layout = LibraryLayout.valueOf(args.getString("layout", "GRID"))
        val landscape = args.getString("orientation", "portrait") == "landscape"
        val swipes = args.getString("swipes", "30").toInt()
        val zoom = args.getString("zoom", "2").toInt().also { require(it in 0..2) }
        val featured = args.getString("featured", "false").toBoolean()
        val context = compose.activity.applicationContext
        val preferences = SharedPreferencesHelper.getSharedPreferences(context)
        val layoutKey = "pref_library_layout"
        val zoomKey = "pref_library_zoom_density"
        val sortKey = "pref_library_sort"
        val oldLayout = preferences.getString(layoutKey, null)
        val oldZoom = preferences.getInt(zoomKey, 0)
        val hadZoom = preferences.contains(zoomKey)
        val oldSort = preferences.getString(sortKey, null)
        val dbName = "library-capacity-test"
        context.deleteDatabase(dbName)
        val db = Room.databaseBuilder(context, RetrogradeDatabase::class.java, dbName).build()
        val assets = File(context.cacheDir, "library-capacity-covers").apply { mkdirs() }
        var vm: LibraryViewModel? = null
        try {
            Log.i(TAG, "PHASE seed count=$count layout=$layout")
            val covers = makeCovers(assets)
            val seedStart = SystemClock.elapsedRealtime()
            for (start in 1..count step 1000) {
                db.gameDao().insert((start..minOf(count, start + 999)).map { index ->
                    Game(
                        id = index,
                        fileName = "Capacity $index.nes",
                        fileUri = "content://library-capacity-test/game/$index",
                        title = title(index),
                        systemId = if (index % 2 == 0) "nes" else "snes",
                        developer = "Capacity fixture",
                        coverFrontUrl = null,
                        customCoverPath = covers[(index - 1) % covers.size].absolutePath,
                        lastIndexedAt = 1_700_000_000_000L + index,
                        lastPlayedAt = if (featured && index == 1) 1_700_000_000_001L else null,
                        fileSize = 32768L + index,
                        isAvailable = true,
                    )
                })
            }
            val seedMs = SystemClock.elapsedRealtime() - seedStart
            // The VM reads initial presentation once; restore before rendering the trial.
            preferences.edit().putString(layoutKey, layout.name).putInt(zoomKey, zoom)
                .putString(sortKey, "TITLE").commit()
            compose.runOnUiThread {
                compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                compose.activity.requestedOrientation = if (landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                vm = ViewModelProvider(
                    compose.activity,
                    LibraryViewModel.Factory(context, db, RegisteredSystemsStore(context), LibraryStartupStore(context)),
                )[LibraryViewModel::class.java]
                vm!!.selectFilter(LibraryFilter.All)
            }
            preferences.edit().apply {
                if (oldLayout == null) remove(layoutKey) else putString(layoutKey, oldLayout)
                if (hadZoom) putInt(zoomKey, oldZoom) else remove(zoomKey)
                if (oldSort == null) remove(sortKey) else putString(sortKey, oldSort)
            }.commit()
            val viewModel = vm!!
            val renderStart = SystemClock.elapsedRealtime()
            compose.setContent {
                AppTheme {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalAdaptiveLayout provides calculateAdaptiveLayoutSpec(maxWidth, maxHeight)) {
                            SharedTransitionLayout {
                                val shared = this
                                AnimatedVisibility(visible = true) {
                                    LibraryScreen(
                                        sharedTransitionScope = shared,
                                        animatedVisibilityScope = this,
                                        viewModel = viewModel,
                                        onGameClick = {}, onContinueClick = {}, onGameLongClick = {},
                                        onFavoriteToggle = { _, _ -> }, onAddConsole = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
            awaitTitle(1)
            if (featured) {
                compose.waitUntil(10_000) { viewModel.state.value.continueGame?.id == 1 }
                compose.waitForIdle()
                if (layout != LibraryLayout.CONSOLE) scrollTo(0)
                compose.onNodeWithText(context.getString(com.omnidroid.R.string.continue_playing))
                    .assertIsDisplayed()
            }
            val renderMs = SystemClock.elapsedRealtime() - renderStart
            sample("rendered")
            if (args.getString("capture", "false").toBoolean()) {
                SystemClock.sleep(300)
                val screenshot = File(context.getExternalFilesDir(null), "capacity-$layout-${if (landscape) "landscape" else "portrait"}-$count-z$zoom.png")
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                screenshot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                    putString("capacity_screenshot", screenshot.absolutePath)
                })
            }
            // Grid/list exercise sparse paging jumps including the final database row.
            // Console uses its real gesture navigation, then searches for the final row below.
            if (layout != LibraryLayout.CONSOLE) {
                listOf(count / 4, count / 2, count * 3 / 4, count - 1, count / 2, 0).distinct().forEach { index ->
                    val position = index + if (featured) 1 else 0
                    scrollTo(position)
                    awaitTitle(index + 1, position)
                    sample("index-$index")
                }
            }
            repeat(swipes) {
                compose.onRoot().performTouchInput {
                    if (layout == LibraryLayout.CONSOLE || (layout == LibraryLayout.GRID && landscape)) swipeLeft()
                    else swipeUp()
                }
                compose.waitForIdle()
            }
            sample("scrolled")
            compose.runOnUiThread { viewModel.changeQueryString(title(count)) }
            awaitTitle(count)
            sample("last-search")
            compose.runOnUiThread { viewModel.clearSearchQuery() }
            // Lazy lists may retain their previous position when search is cleared.
            // Explicitly navigate back instead of treating that valid behavior as a failure.
            if (layout != LibraryLayout.CONSOLE) scrollTo(0)
            awaitTitle(1, if (layout == LibraryLayout.CONSOLE) null else 0)
            // Leave enough real time for image decoding, prefetch work and delayed exceptions.
            SystemClock.sleep(2000)
            compose.waitForIdle()
            sample("settled")
            Log.i(TAG, "PASS count=$count layout=$layout seedMs=$seedMs renderMs=$renderMs")
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("capacity_result", "PASS count=$count layout=$layout seedMs=$seedMs renderMs=$renderMs")
            })
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent {}
                compose.activity.viewModelStore.clear()
            }
            db.close()
            context.deleteDatabase(dbName)
            preferences.edit().apply {
                if (oldLayout == null) remove(layoutKey) else putString(layoutKey, oldLayout)
                if (hadZoom) putInt(zoomKey, oldZoom) else remove(zoomKey)
                if (oldSort == null) remove(sortKey) else putString(sortKey, oldSort)
            }.commit()
        }
    }

    private fun scrollTo(index: Int) {
        val scrollable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .fetchSemanticsNodes().maxBy { it.boundsInRoot.width * it.boundsInRoot.height }
        compose.onNode(SemanticsMatcher("library viewport") { it.id == scrollable.id })
            .performScrollToIndex(index)
    }

    private fun awaitTitle(index: Int, scrollIndex: Int? = null) {
        val expected = title(index)
        Log.i(TAG, "AWAIT title=$expected scrollIndex=$scrollIndex")
        var nextScroll = SystemClock.elapsedRealtime() + 500
        compose.waitUntil(30_000) {
            val found = compose.onAllNodes(hasText(expected, substring = true) or hasContentDescription(expected))
                .fetchSemanticsNodes().isNotEmpty()
            // Paging replaces zero-size placeholder slots asynchronously. Reapply the target
            // after loading, since lazy layout anchoring can move it outside the viewport.
            if (!found && scrollIndex != null && SystemClock.elapsedRealtime() >= nextScroll) {
                scrollTo(scrollIndex)
                nextScroll = SystemClock.elapsedRealtime() + 500
            }
            found
        }
    }

    private fun sample(phase: String) {
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val runtime = Runtime.getRuntime()
        Log.i(TAG, "MEM phase=$phase pssKb=${memory.totalPss} javaUsedKb=${(runtime.totalMemory() - runtime.freeMemory()) / 1024} nativeKb=${Debug.getNativeHeapAllocatedSize() / 1024}")
    }

    private fun makeCovers(directory: File): List<File> = (0 until 256).map { index ->
        File(directory, "cover-$index.png").also { file ->
            if (!file.exists()) {
                val bitmap = Bitmap.createBitmap(384, 512, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.HSVToColor(floatArrayOf(index * 360f / 256, .7f, .7f)))
                val paint = Paint().apply { color = Color.WHITE; textSize = 42f }
                canvas.drawText("Game $index", 24f, 256f, paint)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private fun title(index: Int) = "Capacity Game ${index.toString().padStart(8, '0')}"

    companion object { const val TAG = "LibraryCapacity" }
}
