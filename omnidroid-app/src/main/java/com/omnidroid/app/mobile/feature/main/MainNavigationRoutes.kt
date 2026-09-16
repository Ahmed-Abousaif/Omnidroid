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

fun NavGraphBuilder.composable(
    route: MainRoute,
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
        this.composable(route = route.route, arguments = route.arguments, content = content)
    }
}

fun NavController.navigateToRoute(route: MainRoute) {
    this.navigate(route.route)
}

fun NavController.navigateToGameDetails(gameId: Int) {
    this.navigate("games/$gameId")
}

enum class MainRoute(
    val route: String,
    @StringRes val titleId: Int,
    val parent: MainRoute? = null,
    val arguments: List<NamedNavArgument> = emptyList(),
    val showBottomNavigation: Boolean = true,
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
        showBottomNavigation = false,
    ),
    ADD_CONSOLES(
        route = "systems/add",
        titleId = R.string.title_add_console,
        parent = HOME,
        showBottomNavigation = false,
    ),
    SETTINGS(
        route = "settings/home",
        titleId = R.string.title_settings,
        showBottomNavigation = false,
    ),
    SETTINGS_ADVANCED(
        route = "settings/advanced",
        titleId = R.string.settings_title_advanced_settings,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_BIOS(
        route = "settings/bios",
        titleId = R.string.settings_title_display_bios_info,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_CORES_SELECTION(
        route = "settings/cores",
        titleId = R.string.settings_title_open_cores_selection,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_INPUT_DEVICES(
        route = "settings/inputdevices",
        titleId = R.string.settings_title_gamepad_settings,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_SAVE_SYNC(
        route = "settings/savesync",
        titleId = R.string.settings_title_save_sync,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_DEVICE_PROFILE(
        route = "settings/deviceprofile",
        titleId = R.string.settings_title_device_profile,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    PROFILE(
        route = "profile",
        titleId = R.string.title_profile,
        parent = HOME,
        showBottomNavigation = false,
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
