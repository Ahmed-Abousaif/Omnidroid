package com.omnidroid.app.mobile.shared.compose.sidemenu

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/** Page metadata has no callbacks or Android objects and supports arbitrary nesting. */
data class SideMenuDestination(val route: String, @param:StringRes val titleRes: Int, val parent: String? = null)

fun validateSideMenuDestinations(pages: List<SideMenuDestination>) {
    require(pages.map { it.route }.distinct().size == pages.size) { "Duplicate side-menu route" }
    val byId = pages.associateBy { it.route }
    pages.forEach { page ->
        val visited = mutableSetOf(page.route)
        var parent = page.parent
        while (parent != null) {
            require(visited.add(parent)) { "Cyclic side-menu parent: $parent" }
            parent = requireNotNull(byId[parent]) { "Missing side-menu parent: $parent" }.parent
        }
    }
}

@Composable
fun SideMenuNavHost(navController: NavHostController, startDestination: String, content: NavGraphBuilder.() -> Unit) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(160)) },
        builder = content,
    )
}

fun NavController.openMenuPage(destination: SideMenuDestination) = navigate(destination.route) { launchSingleTop = true }
