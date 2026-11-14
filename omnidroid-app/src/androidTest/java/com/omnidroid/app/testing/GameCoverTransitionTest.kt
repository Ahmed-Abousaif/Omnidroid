package com.omnidroid.app.testing

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omnidroid.app.mobile.feature.main.MainRoute
import com.omnidroid.app.mobile.feature.main.gameCoverOrigin
import com.omnidroid.app.mobile.feature.main.navigateToGameDetails
import com.omnidroid.app.mobile.shared.compose.ui.GameCoverOrigin
import com.omnidroid.app.mobile.shared.compose.ui.gameCoverSharedBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class GameCoverTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<LibraryStressActivity>()

    @Test
    fun continueAndOrdinaryCopiesKeepTheirOwnOriginOnOpenAndReturn() {
        lateinit var nav: NavHostController
        lateinit var shared: SharedTransitionScope
        var destinationOrigin: GameCoverOrigin? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                shared = this
                val sharedScope = this
                nav = rememberNavController()
                NavHost(nav, startDestination = "home",
                    enterTransition = { fadeIn(tween(400)) }, exitTransition = { fadeOut(tween(400)) },
                    popEnterTransition = { fadeIn(tween(400)) }, popExitTransition = { fadeOut(tween(400)) }) {
                    composable("home") {
                        val visibility = this
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.BottomStart).size(80.dp, 120.dp)
                                .gameCoverSharedBounds(sharedScope, visibility, 42, 8.dp)
                                .fillMaxSize().background(Color.Red).testTag("ordinary")
                                .clickable { nav.navigateToGameDetails(42) })
                            Box(Modifier.align(Alignment.BottomEnd).size(80.dp, 120.dp)
                                .gameCoverSharedBounds(sharedScope, visibility, 42, 8.dp, GameCoverOrigin.Continue)
                                .fillMaxSize().background(Color.Magenta).testTag("continue")
                                .clickable { nav.navigateToGameDetails(42, GameCoverOrigin.Continue) })
                        }
                    }
                    composable(MainRoute.GAME_DETAILS.route, arguments = MainRoute.GAME_DETAILS.arguments) { entry ->
                        val origin = entry.gameCoverOrigin
                        destinationOrigin = origin
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.size(180.dp, 240.dp)
                                .gameCoverSharedBounds(sharedScope, this@composable, 42, 8.dp, origin)
                                .fillMaxSize().background(Color.Magenta).testTag("details"))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        for (origin in listOf(GameCoverOrigin.Continue, GameCoverOrigin.Library)) {
            compose.onNodeWithTag("continue").assertIsDisplayed()
            compose.onNodeWithTag("ordinary").assertIsDisplayed()
            compose.onNodeWithTag(if (origin == GameCoverOrigin.Continue) "continue" else "ordinary").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertTrue("The clicked copy must have a shared match", shared.isTransitionActive)
                assertEquals(origin, destinationOrigin)
                assertEquals(MainRoute.GAME_DETAILS.route, nav.currentDestination?.route)
                assertEquals(42, nav.currentBackStackEntry?.arguments?.getInt("gameId"))
                if (origin == GameCoverOrigin.Continue) {
                    assertEquals(origin.name, nav.currentBackStackEntry?.savedStateHandle?.get<String>("coverOrigin"))
                }
            }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("details").assertIsDisplayed()
            compose.runOnIdle { assertFalse(shared.isTransitionActive); nav.popBackStack() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertTrue("The return must match the same copy", shared.isTransitionActive) }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertFalse(shared.isTransitionActive) }
        }
    }
}
