package com.archivetv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.archivetv.ui.detail.DetailScreen
import com.archivetv.ui.home.HomeScreen
import com.archivetv.ui.player.PlayerScreen
import com.archivetv.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player/{id}"
    fun detail(id: String) = "detail/$id"
    fun player(id: String) = "player/$id"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onItemClick = { nav.navigate(Routes.detail(it.identifier)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(onItemClick = { nav.navigate(Routes.detail(it.identifier)) })
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
