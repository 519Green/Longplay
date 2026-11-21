package com.archivetv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.archivetv.ui.detail.DetailScreen
import com.archivetv.ui.home.HomeScreen
import com.archivetv.ui.mylist.MyListScreen
import com.archivetv.ui.player.PlayerScreen
import com.archivetv.ui.search.SearchScreen
import com.archivetv.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MY_LIST = "mylist"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player/{id}"
    fun detail(id: String) = "detail/$id"
    fun player(id: String) = "player/$id"
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

// Movies, TV, Animation and Educational join this list in Phase 2 with their screens.
private val destinations = listOf(
    Destination(Routes.SEARCH, "Search", Icons.Filled.Search),
    Destination(Routes.HOME, "Home", Icons.Filled.Home),
    Destination(Routes.MY_LIST, "My List", Icons.Filled.Favorite),
)

/**
 * @param voiceSearchRequests emits each time the remote's search/mic key is pressed.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppNavHost(voiceSearchRequests: Flow<Unit> = emptyFlow()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = destinations.any { it.route == route }

    var voiceRequest by remember { mutableIntStateOf(0) }
    val currentRoute by rememberUpdatedState(route)
    LaunchedEffect(Unit) {
        voiceSearchRequests.collect {
            voiceRequest++
            if (currentRoute != Routes.SEARCH) nav.navigateTopLevel(Routes.SEARCH)
        }
    }

    val itemFocus = remember { destinations.associate { it.route to FocusRequester() } }
    var drawerHasFocus by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }

    // Android TV "left navigation" pattern: Back from Home content moves focus
    // to the drawer; Back again (drawer focused) falls through and exits.
    BackHandler(enabled = route == Routes.HOME && !drawerHasFocus) {
        runCatching { itemFocus.getValue(Routes.HOME).requestFocus() }
    }

    ModalNavigationDrawer(
        drawerContent = {
            if (topLevel) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(12.dp)
                        .selectableGroup()
                        .onFocusChanged { drawerHasFocus = it.hasFocus }
                        .focusProperties {
                            // Entering lands on the current screen's item; Right goes
                            // back to the content, which restores its last focused card.
                            enter = { itemFocus[route] ?: FocusRequester.Default }
                            exit = { direction ->
                                if (direction == FocusDirection.Right) contentFocus else FocusRequester.Default
                            }
                        }
                        .focusGroup(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    destinations.forEach { d ->
                        DrawerItem(
                            destination = d,
                            selected = route == d.route,
                            focusRequester = itemFocus.getValue(d.route),
                            onClick = { if (route != d.route) nav.navigateTopLevel(d.route) },
                        )
                    }
                }
            }
        },
    ) {
        // Top-level content starts right of the collapsed drawer. Focus search
        // compares the drawer item with whole focus groups (lazy lists are one);
        // a group starting at x=0 is not "to the right" of the drawer, so D-pad
        // right out of the drawer would find nothing.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (topLevel) Dimens.DrawerCollapsed else 0.dp)
                .focusRequester(contentFocus)
                .focusRestorer()
        ) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(onItemClick = { nav.navigate(Routes.detail(it.identifier)) })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    voiceRequest = voiceRequest,
                )
            }
            composable(Routes.MY_LIST) {
                MyListScreen(onItemClick = { nav.navigate(Routes.detail(it.identifier)) })
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    identifier = entry.arguments!!.getString("id")!!,
                    onPlay = { nav.navigate(Routes.player(it)) },
                )
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                PlayerScreen(
                    identifier = entry.arguments!!.getString("id")!!,
                    onExit = { nav.popBackStack() },
                )
            }
        }
        }
    }
}

@Composable
private fun NavigationDrawerScope.DrawerItem(
    destination: Destination,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = { Icon(destination.icon, contentDescription = null) },
        modifier = Modifier.focusRequester(focusRequester),
    ) {
        Text(destination.label)
    }
}

/** Top-level screens replace each other above Home instead of stacking up. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
