package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.swordfish.lemuroid.R

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
    FAVORITES(
        route = "favorites",
        titleId = R.string.favorites,
    ),
    SEARCH(
        route = "search",
        titleId = R.string.title_search,
    ),
    SYSTEMS(
        route = "systems/home",
        titleId = R.string.title_systems,
    ),
    SYSTEM_GAMES(
        route = "systems/{metaSystemId}",
        titleId = R.string.title_games,
        parent = SYSTEMS,
        listOf(navArgument("metaSystemId") { type = NavType.StringType }),
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

enum class MainNavigationRoutes(
    val route: MainRoute,
    @StringRes val titleId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(MainRoute.HOME, R.string.title_home, Icons.Filled.Home, Icons.Outlined.Home),
    FAVORITES(MainRoute.FAVORITES, R.string.favorites, Icons.Filled.Favorite, Icons.Filled.FavoriteBorder),
    SYSTEMS(MainRoute.SYSTEMS, R.string.title_systems, Icons.Filled.VideogameAsset, Icons.Outlined.VideogameAsset),
    SEARCH(MainRoute.SEARCH, R.string.title_search, Icons.Filled.Search, Icons.Outlined.Search),
}
