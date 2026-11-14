package com.omnidroid.app.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.omnidroid.app.mobile.feature.library.ConsoleBackdrop
import com.omnidroid.app.mobile.feature.library.ConsoleLibrary
import com.omnidroid.app.mobile.feature.library.LibraryLayout
import com.omnidroid.app.mobile.feature.library.LibraryPagedContent
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.calculateAdaptiveLayoutSpec
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Profiling variants only. Fixtures never enter Room or touch user games/saves. */
class LibraryBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val count = intent.getIntExtra("count", 1_000).coerceIn(1, 5_000)
        val layout = LibraryLayout.valueOf(intent.getStringExtra("layout") ?: "GRID")
        lifecycleScope.launch {
            val fixtures = withContext(Dispatchers.IO) { fixtures(count) }
            setContent { BenchmarkLibrary(fixtures, layout) }
        }
    }

    private fun fixtures(count: Int): List<Game> {
        val directory = File(cacheDir, "library-benchmark-covers").apply { mkdirs() }
        val covers = List(16) { index ->
            File(directory, "$index.jpg").also { file ->
                if (!file.isFile) {
                    val bitmap = Bitmap.createBitmap(240, 360, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.HSVToColor(floatArrayOf(index * 22.5f, 0.65f, 0.5f)))
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            textSize = 28f
                        }
                        canvas.drawText("Cover ${index + 1}", 30f, 180f, paint)
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        return List(count) { index ->
            Game(
                id = index + 1,
                title = "Benchmark game ${index.toString().padStart(5, '0')}",
                fileName = "$index.nes",
                fileUri = "benchmark://library/$index",
                systemId = "nes",
                developer = null,
                coverFrontUrl = null,
                customCoverPath = covers[index % covers.size].absolutePath,
                lastIndexedAt = 0,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun BenchmarkLibrary(fixtures: List<Game>, layout: LibraryLayout) {
    val flow = remember(fixtures) { flowOf(PagingData.from(fixtures)) }
    val games = flow.collectAsLazyPagingItems()
    var backdrop by remember { mutableStateOf<Game?>(fixtures.first()) }
    val selection = remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    AppTheme {
        BoxWithConstraints(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            CompositionLocalProvider(
                LocalAdaptiveLayout provides calculateAdaptiveLayoutSpec(maxWidth, maxHeight),
            ) {
                Box(Modifier.fillMaxSize().testTag(if (games.itemCount > 0) "benchmark-library-ready" else "benchmark-library-loading")) {
                    if (layout == LibraryLayout.CONSOLE) ConsoleBackdrop(backdrop, Modifier.matchParentSize())
                    SharedTransitionLayout {
                        val shared = this
                        AnimatedVisibility(true) {
                            if (layout == LibraryLayout.CONSOLE) {
                                ConsoleLibrary(
                                    sharedTransitionScope = shared,
                                    animatedVisibilityScope = this,
                                    games = games,
                                    featured = fixtures.first(),
                                    selection = selection,
                                    portrait = false,
                                    showControllerGlyph = false,
                                    focusRequester = focusRequester,
                                    onOpenGame = {}, onPlayGame = {}, onGameLongClick = {},
                                    onFavoriteToggle = { _, _ -> },
                                    onBackdropGameChange = { backdrop = it },
                                )
                            } else {
                                LibraryPagedContent(
                                    games = games,
                                    featured = fixtures.first(),
                                    layout = layout,
                                    zoomDensity = 1,
                                    portrait = false,
                                    sharedTransitionScope = shared,
                                    animatedVisibilityScope = this,
                                    firstItemRequester = focusRequester,
                                    onGameClick = {}, onContinueClick = {}, onGameLongClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
