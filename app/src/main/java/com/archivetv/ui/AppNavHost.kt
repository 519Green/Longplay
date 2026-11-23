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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
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
import com.archivetv.ui.browse.DerivedBrowseScreen
import com.archivetv.ui.search.SearchScreen
import com.archivetv.ui.settings.SettingsScreen
import com.archivetv.ui.live.ArtistScreen
import com.archivetv.ui.live.ArtistsScreen
import com.archivetv.ui.live.LiveMusicScreen
import com.archivetv.ui.section.SectionScreen
import com.archivetv.ui.section.TilesScreen
import com.archivetv.ui.theme.SectionIcons
import com.archivetv.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archivetv.ui.nowplaying.MiniBar
import com.archivetv.ui.nowplaying.NowPlayingScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.archivetv.ArchiveTvApp
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.archivetv.ui.common.LocalReturnFocus
import com.archivetv.ui.common.ReturnFocus

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MY_LIST = "mylist"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player/{id}?file={file}&start={start}"
    const val TILES = "tiles/{id}"
    const val BROWSE = "browse/{id}"
    const val BROWSE_DERIVED = "browse/derived/{group}/{key}"
    const val NOW_PLAYING = "nowplaying"
    const val LIVE = "live"
    const val LIVE_ARTISTS = "live/artists"
    const val LIVE_ARTIST = "live/artist/{id}"
    const val SETTINGS = "settings"
    fun liveArtist(id: String) = "live/artist/$id"
    fun detail(id: String) = "detail/$id"
    /** [fromStart] plays [fileIndex] (default the first file) from 0:00, ignoring saved positions. */
    fun player(id: String, fileIndex: Int? = null, fromStart: Boolean = false) =
        "player/$id?file=${fileIndex ?: -1}&start=$fromStart"
    fun section(id: String) = "section/$id"
    fun tiles(id: String) = "tiles/$id"
    fun browse(id: String) = "browse/$id"
    /** Group ids are catalog ids and keys are `[a-z0-9-]` slugs, so neither needs encoding. */
    fun browseDerived(group: String, key: String) = "browse/derived/$group/$key"
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
    Destination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/** Section ids from the drawer; each is its own destination. */
private val sectionIds = destinations.map { it.route }.filter { it.startsWith("section/") }.map { it.removePrefix("section/") }

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
    val container = (LocalContext.current.applicationContext as ArchiveTvApp).container
    val audio = container.audio
    val scope = rememberCoroutineScope()
    /** Starts an audio item through the background player. */
    fun playAudio(id: String, index: Int?, fromStart: Boolean = false) {
        scope.launch { if (audio.play(id, index, fromStart)) nav.navigate(Routes.NOW_PLAYING) }
    }
    val nowPlaying by audio.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        nowPlayingRequests.collect { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }
    }
    val backStack by nav.currentBackStackEntryAsState()
    // The mini bar takes focus only when the user presses Up from the screen
    // below it (or while it already has focus), so it never takes a screen's
    // initial focus nor the focus a closed screen leaves behind. Focus search
    // reads canFocus while the Up key is being handled.
    var upHeld by remember { mutableStateOf(false) }
    var barHasFocus by remember { mutableStateOf(false) }
    val route = backStack?.destination?.route
    val topLevel = destinations.any { it.route == route }

    // A rights-setting change drops the cached results at once, and every screen is rebuilt
    // (fresh ViewModels, no saved stacks) when the viewer next lands on a top-level screen
    // other than Settings.
    var pendingReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        container.settings.openOnly.drop(1).collect {
            container.clearSessionCaches()
            pendingReset = true
        }
    }
    LaunchedEffect(pendingReset, route) {
        val target = route ?: return@LaunchedEffect
        if (!pendingReset || target == Routes.SETTINGS || !topLevel) return@LaunchedEffect
        pendingReset = false
        nav.rebuildAt(target)
    }

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

    // Closing Now Playing leaves nothing focused (the bar refuses focus it was
    // not sent to); give focus back to the screen below, whose restorer picks
    // the item it last had.
    // The same applies to Back from any pushed screen (Detail, Player, Browse,
    // ...): the popped screen took the focused node with it, and the drawer,
    // first in layout, would otherwise take focus.
    var previousRoute by remember { mutableStateOf<String?>(null) }
    val returnFocus = remember { mutableMapOf<String, ReturnFocus>() }
    val seenEntries = remember { mutableSetOf<String>() }
    LaunchedEffect(backStack?.id) {
        val left = previousRoute
        val entry = backStack
        previousRoute = entry?.destination?.route
        val returning = entry != null && !seenEntries.add(entry.id)
        val fromPushed = left != null && destinations.none { it.route == left }
        if (entry != null && (left == Routes.NOW_PLAYING || (returning && fromPushed)) && previousRoute != Routes.NOW_PLAYING) {
            // RESUMED once the screen transition has finished.
            entry.lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            // The card or tile that opened the closed screen takes focus back;
            // otherwise (a button opened it, or the card is gone) the restorer picks.
            val memory = returnFocus[entry.id]
            if (memory != null && memory.restore()) {
                withTimeoutOrNull(600) { snapshotFlow { memory.isRestoring }.first { !it } }
                memory.cancel()
            }
            if (memory?.restored != true) runCatching { contentFocus.requestFocus() }
        }
    }

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
                    // Nine items at 56dp fill a 540dp-tall TV screen; any spacing pushes the last off the bottom.
                    verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
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
        ) {
        Column(Modifier.fillMaxSize()) {
        val np = nowPlaying
        if (np != null && route != Routes.NOW_PLAYING && route != Routes.PLAYER) {
            MiniBar(
                np = np,
                onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } },
                modifier = Modifier
                    .padding(start = Dimens.ContentStart, end = Dimens.OverscanHorizontal, top = 8.dp)
                    .onFocusChanged { barHasFocus = it.hasFocus }
                    .focusProperties { canFocus = upHeld || barHasFocus },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The drawer's Right and focus restoration target the screens only,
                // so entering content never lands on the mini bar above them.
                .focusRequester(contentFocus)
                .focusRestorer()
                .onPreviewKeyEvent { e ->
                    if (e.key == Key.DirectionUp) upHeld = e.type == KeyEventType.KeyDown
                    // The viewer moved on: a restore still waiting for its card must not pull focus back.
                    if (e.type == KeyEventType.KeyDown) returnFocus[backStack?.id]?.cancel()
                    false
                }
        ) {
        CompositionLocalProvider(LocalReturnFocusMap provides returnFocus) {
        NavHost(nav, startDestination = Routes.HOME) {
            screen(Routes.HOME) {
                HomeScreen(
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onPlayFile = { id, index -> nav.navigate(Routes.player(id, index)) },
                    onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                    onOpenLeaf = { nav.navigate(Routes.browse(it)) },
                    onListen = { id, index -> playAudio(id, index) },
                    onPlay = { id, isAudio ->
                        if (isAudio) playAudio(id, null) else nav.navigate(Routes.player(id))
                    },
                )
            }
            // One destination per section, not one "section/{id}" pattern: saved top-level stacks are
            // keyed by destination, so a shared pattern made the drawer restore whichever section was
            // saved last (Movies opened Animation and Shorts).
            sectionIds.forEach { id ->
                screen(Routes.section(id)) {
                    SectionScreen(
                        sectionId = id,
                        onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                        onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                        onOpenLeaf = { nav.navigate(Routes.browse(it)) },
                        onOpenLive = { nav.navigate(Routes.LIVE) },
                    )
                }
            }
            screen(Routes.LIVE) {
                LiveMusicScreen(
                    onOpenRecording = { nav.navigate(Routes.detail(it)) },
                    onOpenArtist = { nav.navigate(Routes.liveArtist(it)) },
                    onAllArtists = { nav.navigate(Routes.LIVE_ARTISTS) },
                )
            }
            screen(Routes.LIVE_ARTISTS) {
                ArtistsScreen(onOpenArtist = { nav.navigate(Routes.liveArtist(it)) })
            }
            screen(Routes.LIVE_ARTIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                ArtistScreen(
                    artistId = entry.arguments!!.getString("id")!!,
                    onOpenRecording = { nav.navigate(Routes.detail(it)) },
                )
            }
            screen(Routes.TILES, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                TilesScreen(
                    groupId = entry.arguments!!.getString("id")!!,
                    onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                    onOpenLeaf = { nav.navigate(Routes.browse(it)) },
                    onOpenEntry = { group, key -> nav.navigate(Routes.browseDerived(group, key)) },
                )
            }
            screen(
                Routes.BROWSE_DERIVED,
                arguments = listOf(
                    navArgument("group") { type = NavType.StringType },
                    navArgument("key") { type = NavType.StringType },
                ),
            ) { entry ->
                DerivedBrowseScreen(
                    groupId = entry.arguments!!.getString("group")!!,
                    key = entry.arguments!!.getString("key")!!,
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onPlayAll = { items, shuffle ->
                        audio.playAll(items, shuffle)
                        nav.navigate(Routes.NOW_PLAYING)
                    },
                )
            }
            screen(Routes.BROWSE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                BrowseScreen(
                    leafId = entry.arguments!!.getString("id")!!,
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onPlayAll = { items, shuffle ->
                        audio.playAll(items, shuffle)
                        nav.navigate(Routes.NOW_PLAYING)
                    },
                )
            }
            screen(Routes.SEARCH) {
                SearchScreen(
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                    onOpenArtist = { nav.navigate(Routes.liveArtist(it)) },
                    onOpenGroup = { nav.navigate(Routes.tiles(it)) },
                    voicePending = voicePending,
                    onVoiceStarted = { voicePending = false },
                )
            }
            screen(Routes.MY_LIST) {
                MyListScreen(onItemClick = { nav.navigate(Routes.detail(it.identifier)) })
            }
            screen(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    identifier = entry.arguments!!.getString("id")!!,
                    onPlay = { id, index -> nav.navigate(Routes.player(id, index)) },
                    onPlayAudio = { id, index -> playAudio(id, index) },
                    onPlayFromStart = { id, isAudio ->
                        if (isAudio) playAudio(id, 0, fromStart = true)
                        else nav.navigate(Routes.player(id, 0, fromStart = true))
                    },
                    onOpenRecording = { nav.navigate(Routes.detail(it)) },
                    onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                )
            }
            screen(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("file") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("start") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                PlayerScreen(
                    identifier = entry.arguments!!.getString("id")!!,
                    startIndex = entry.arguments!!.getInt("file").takeIf { it >= 0 },
                    fromStart = entry.arguments!!.getBoolean("start"),
                    onExit = { nav.popBackStack() },
                )
            }
            screen(Routes.NOW_PLAYING) {
                NowPlayingScreen()
            }
            screen(Routes.SETTINGS) {
                SettingsScreen(container.settings)
            }
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

/**
 * A destination in its own focus group with its own restorer, so each screen
 * keeps the child it last focused while another screen (Detail, the player)
 * is on top. The content box's restorer alone only remembers the last focused
 * node anywhere, which is gone by the time Back returns to this screen.
 */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    val map = LocalReturnFocusMap.current
    val memory = remember(entry.id) { map.getOrPut(entry.id) { ReturnFocus() } }
    CompositionLocalProvider(LocalReturnFocus provides memory) {
        Box(Modifier.fillMaxSize().focusRestorer().focusGroup()) { content(entry) }
    }
}

/** One [ReturnFocus] per back stack entry id, kept by the nav host across recompositions. */
private val LocalReturnFocusMap = staticCompositionLocalOf<MutableMap<String, ReturnFocus>> { mutableMapOf() }

/**
 * Replaces the whole back stack with a fresh Home, plus [route] on top when it isn't Home, and drops every
 * saved top-level stack, so no screen loaded before a settings change can come back.
 */
private fun NavHostController.rebuildAt(route: String) {
    (destinations.map { it.route } + Routes.SECTION).forEach { runCatching { clearBackStack(it) } }
    navigate(Routes.HOME) { popUpTo(graph.id) { inclusive = true } }
    if (route != Routes.HOME) navigate(route)
}

/** Top-level screens replace each other above Home instead of stacking up. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
