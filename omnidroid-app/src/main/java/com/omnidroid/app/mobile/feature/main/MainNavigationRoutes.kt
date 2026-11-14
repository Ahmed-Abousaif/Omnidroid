package com.omnidroid.app.mobile.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.GameCoverOrigin

import androidx.compose.animation.AnimatedContentTransitionScope

fun NavGraphBuilder.composable(
    route: MainRoute,
    enterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
    popExitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
    instant: Boolean = false,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    if (instant) {
        this.composable(
            route = route.route,
            arguments = route.arguments,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            content = content,
        )
    } else {
        this.composable(
            route = route.route,
            arguments = route.arguments,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
            content = content,
        )
    }
}

fun NavController.navigateToRoute(route: MainRoute) {
    this.navigate(route.route)
}

fun NavController.navigateToGameDetails(gameId: Int) {
    this.navigate("games/$gameId")
}

internal fun NavController.navigateToGameDetails(gameId: Int, origin: GameCoverOrigin) {
    navigateToGameDetails(gameId)
    currentBackStackEntry?.savedStateHandle?.set("coverOrigin", origin.name)
}

internal val NavBackStackEntry.gameCoverOrigin: GameCoverOrigin
    get() = if (savedStateHandle.get<String>("coverOrigin") == GameCoverOrigin.Continue.name) {
        GameCoverOrigin.Continue
    } else GameCoverOrigin.Library

enum class MainRoute(
    val route: String,
    @StringRes val titleId: Int,
    val parent: MainRoute? = null,
    val arguments: List<NamedNavArgument> = emptyList(),
) {
    HOME(
        route = "home",
        titleId = R.string.title_home,
    ),
    GAME_DETAILS(
        route = "games/{gameId}",
        titleId = R.string.title_game,
        parent = HOME,
        arguments = listOf(navArgument("gameId") { type = NavType.IntType }),
    ),
    ADD_CONSOLES(
        route = "systems/add",
        titleId = R.string.title_add_console,
        parent = HOME,
    ),
    SETTINGS(
        route = "settings/home",
        titleId = R.string.title_settings,
    ),
    SETTINGS_UNRECOGNIZED(
        route = "settings/unrecognized",
        titleId = R.string.settings_title_unrecognized_files,
        parent = SETTINGS,
    ),
    SETTINGS_BIOS(
        route = "settings/bios",
        titleId = R.string.settings_title_display_bios_info,
        parent = SETTINGS,
    ),
    SETTINGS_CORES_SELECTION(
        route = "settings/cores",
        titleId = R.string.settings_title_open_cores_selection,
        parent = SETTINGS,
    ),
    SETTINGS_GRAPHICS_API(
        route = "settings/graphicsapi",
        titleId = R.string.settings_title_graphics_api_selection,
        parent = SETTINGS,
    ),
    SETTINGS_INPUT_DEVICES(
        route = "settings/inputdevices",
        titleId = R.string.settings_title_gamepad_settings,
        parent = SETTINGS,
    ),
    SETTINGS_SAVE_SYNC(
        route = "settings/savesync",
        titleId = R.string.settings_title_saves_backup,
        parent = SETTINGS,
    ),
    SETTINGS_DEVICE_PROFILE(
        route = "settings/deviceprofile",
        titleId = R.string.settings_title_device_profile,
        parent = SETTINGS,
    ),
    SETTINGS_UI_SCALE(
        route = "settings/uiscale",
        titleId = R.string.settings_title_button_ui_scale,
        parent = SETTINGS,
    ),
    PROFILE(
        route = "profile",
        titleId = R.string.title_profile,
        parent = HOME,
    ),
    ;

    val root = root()

    private fun root(): MainRoute {
        return parent?.root() ?: this
    }

    companion object {
        fun findByRoute(route: String): MainRoute {
            return values().first { it.route == route }
        }
    }
}
