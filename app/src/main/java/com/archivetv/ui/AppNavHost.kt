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
import com.archivetv.ui.browse.BrowseScreen
import com.archivetv.ui.search.SearchScreen
import com.archivetv.ui.section.SectionScreen
import com.archivetv.ui.section.TilesScreen
import com.archivetv.ui.theme.SectionIcons
import com.archivetv.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archivetv.ui.nowplaying.MiniBar
import com.archivetv.ui.nowplaying.NowPlayingScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.archivetv.ArchiveTvApp
import kotlinx.coroutines.flow.emptyFlow

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MY_LIST = "mylist"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player/{id}?file={file}"
    const val SECTION = "section/{id}"
    const val TILES = "tiles/{id}"
    const val BROWSE = "browse/{id}"
    const val NOW_PLAYING = "nowplaying"
    fun detail(id: String) = "detail/$id"
    fun player(id: String, fileIndex: Int? = null) = "player/$id" + (fileIndex?.let { "?file=$it" } ?: "")
    fun section(id: String) = "section/$id"
    fun tiles(id: String) = "tiles/$id"
    fun browse(id: String) = "browse/$id"
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

// Section ids match the top-level groups in browse_config.json.
private val destinations = listOf(
    Destination(Routes.SEARCH, "Search", Icons.Filled.Search),
    Destination(Routes.HOME, "Home", Icons.Filled.Home),
    Destination(Routes.section("movies"), "Movies", SectionIcons.Movie),
    Destination(Routes.section("tv"), "TV", SectionIcons.Tv),
    Destination(Routes.section("animation"), "Animation and Shorts", SectionIcons.Animation),
    Destination(Routes.section("educational"), "Educational", SectionIcons.School),
    Destination(Routes.section("listen"), "Listen", SectionIcons.Headphones),
    Destination(Routes.MY_LIST, "My List", Icons.Filled.Favorite),
)

/**
 * @param voiceSearchRequests emits each time the remote's search/mic key is pressed.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppNavHost(
    voiceSearchRequests: Flow<Unit> = emptyFlow(),
    openDetail: String? = null,
    nowPlayingRequests: Flow<Unit> = emptyFlow(),
) {
    val nav = rememberNavController()
    val audio = (LocalContext.current.applicationContext as ArchiveTvApp).container.audio
    val scope = rememberCoroutineScope()
    /** Starts an audio item through the background player. */
    fun playAudio(id: String, index: Int?) {
        scope.launch { if (audio.play(id, index)) nav.navigate(Routes.NOW_PLAYING) }
    }
    val nowPlaying by audio.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        nowPlayingRequests.collect { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }
    }
    // The mini bar may take focus only after the screen below it has had focus,
    // so it never steals a screen's initial focus.
    var barFocusable by remember { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    // Sections share one route pattern; key them by id so the drawer can tell them apart.
    val route = backStack?.destination?.route.let { pattern ->
        if (pattern == Routes.SECTION) Routes.section(backStack?.arguments?.getString("id").orEmpty()) else pattern
    }
    val topLevel = destinations.any { it.route == route }

    // One-shot: set by the mic key, cleared by Search once it starts listening.
    var voicePending by remember { mutableStateOf(false) }
    val currentRoute by rememberUpdatedState(route)
    LaunchedEffect(Unit) {
        voiceSearchRequests.collect {
            voicePending = true
            if (currentRoute != Routes.SEARCH) nav.navigateTopLevel(Routes.SEARCH)
        }
    }

    LaunchedEffect(openDetail) {
        if (!openDetail.isNullOrBlank()) nav.navigate(Routes.detail(openDetail))
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
        Column(Modifier.fillMaxSize()) {
        val np = nowPlaying
        if (np != null && route != Routes.NOW_PLAYING && route != Routes.PLAYER) {
            MiniBar(
                np = np,
                onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } },
                modifier = Modifier
                    .padding(start = Dimens.ContentStart, end = Dimens.OverscanHorizontal, top = 8.dp)
                    .focusProperties { canFocus = barFocusable },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onFocusChanged { if (it.hasFocus) barFocusable = true }
        ) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onPlayFile = { id, index -> nav.navigate(Routes.player(id, index)) },
                    onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                    onOpenLeaf = { nav.navigate(Routes.browse(it)) },
                    onListen = ::playAudio,
                )
            }
            composable(Routes.SECTION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                SectionScreen(
                    sectionId = entry.arguments!!.getString("id")!!,
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                    onOpenLeaf = { nav.navigate(Routes.browse(it)) },
                )
            }
            composable(Routes.TILES, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                TilesScreen(
                    groupId = entry.arguments!!.getString("id")!!,
                    onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                    onOpenLeaf = { nav.navigate(Routes.browse(it)) },
                )
            }
            composable(Routes.BROWSE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                BrowseScreen(
                    leafId = entry.arguments!!.getString("id")!!,
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onPlayAll = { items, shuffle ->
                        audio.playAll(items, shuffle)
                        nav.navigate(Routes.NOW_PLAYING)
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    voicePending = voicePending,
                    onVoiceStarted = { voicePending = false },
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
                    onPlay = { id, index -> nav.navigate(Routes.player(id, index)) },
                    onPlayAudio = ::playAudio,
                )
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("file") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                PlayerScreen(
                    identifier = entry.arguments!!.getString("id")!!,
                    startIndex = entry.arguments!!.getInt("file").takeIf { it >= 0 },
                    onExit = { nav.popBackStack() },
                )
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen()
            }
        }
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
