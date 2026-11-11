package com.omnidroid.app.mobile.shared.compose.sidemenu

import org.junit.Test

class SideMenuNavigationTest {
    @Test fun acceptsArbitraryDepth() = validateSideMenuDestinations(listOf(
        SideMenuDestination("root", 1), SideMenuDestination("child", 2, "root"),
        SideMenuDestination("deep", 3, "child"), SideMenuDestination("deeper", 4, "deep"),
    ))
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateRoutes() =
        validateSideMenuDestinations(listOf(SideMenuDestination("root", 1), SideMenuDestination("root", 2)))
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingParents() =
        validateSideMenuDestinations(listOf(SideMenuDestination("child", 1, "missing")))
    @Test(expected = IllegalArgumentException::class) fun rejectsCycles() = validateSideMenuDestinations(listOf(
        SideMenuDestination("a", 1, "b"), SideMenuDestination("b", 2, "a"),
    ))
}
