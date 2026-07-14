package com.audiora.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Library : Screen(
        route = "library",
        title = "Library",
        icon = Icons.Rounded.LibraryMusic
    )

    object Create : Screen(
        route = "create",
        title = "Create",
        icon = Icons.Rounded.AddCircle
    )

    object Player : Screen(
        route = "player/{bookId}",
        title = "Player",
        icon = Icons.Rounded.PlayCircle
    )

    object Edit : Screen(
        route = "edit", // Beautiful clean standard route format matching Edit screen
        title = "Edit",
        icon = Icons.Rounded.EditNote
    )

    object Search : Screen(
        route = "search",
        title = "Search",
        icon = Icons.Rounded.Search
    )

    object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Rounded.Settings
    )

    object Details : Screen(
        route = "details/{bookId}",
        title = "Details",
        icon = Icons.Rounded.Info
    )

    object Processing : Screen(
        route = "processing",
        title = "Processing...",
        icon = Icons.Rounded.HourglassEmpty
    )

    companion object {
        val items = listOf(Library, Create, Edit, Search, Settings)
        // The route strings for bottom-nav tab destinations.
        // Used by showBottomNav, transition animations, and navigation guards.
        val tabRouteStrings: Set<String> = items.map { it.route }.toSet()
    }
}
