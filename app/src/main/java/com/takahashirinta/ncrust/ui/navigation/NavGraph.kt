package com.takahashirinta.ncrust.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.takahashirinta.ncrust.network.SongItem
import com.takahashirinta.ncrust.ui.components.SongMenuAction
import com.takahashirinta.ncrust.ui.screen.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val FluentMotionEasing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.0f)
private const val FLUENT_ENTER_MS = 220
private const val FLUENT_EXIT_MS = 180

private fun <T> AnimatedContentTransitionScope<T>.fluentEnterTransition(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(FLUENT_ENTER_MS, easing = FluentMotionEasing),
        initialOffset = { (it * 0.08f).toInt().coerceIn(100, 240) }
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = tween(FLUENT_ENTER_MS, easing = FluentMotionEasing)
    ) + fadeIn(
        animationSpec = tween(FLUENT_ENTER_MS, easing = FluentMotionEasing)
    )

private fun <T> AnimatedContentTransitionScope<T>.fluentExitTransition(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(FLUENT_EXIT_MS, easing = FluentMotionEasing),
        targetOffset = { -(it * 0.04f).toInt().coerceIn(40, 120) }
    ) + fadeOut(
        animationSpec = tween(FLUENT_EXIT_MS, easing = FluentMotionEasing)
    )

private fun <T> AnimatedContentTransitionScope<T>.fluentPopEnterTransition(): EnterTransition =
    scaleIn(
        initialScale = 0.97f,
        animationSpec = tween(FLUENT_ENTER_MS, easing = FluentMotionEasing)
    ) + fadeIn(
        animationSpec = tween(FLUENT_ENTER_MS, easing = FluentMotionEasing)
    )

private fun <T> AnimatedContentTransitionScope<T>.fluentPopExitTransition(): ExitTransition =
    slideOut(
        animationSpec = tween(FLUENT_EXIT_MS, easing = FastOutSlowInEasing),
        targetOffset = { IntOffset((it.width * 0.08f).toInt().coerceIn(60, 180), (it.height * 0.06f).toInt().coerceIn(60, 180)) }
    ) + fadeOut(
        animationSpec = tween(FLUENT_EXIT_MS, easing = FastOutSlowInEasing)
    )

object NavRoutes {
    const val HOME = "home"
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"
    const val PLAYLIST = "playlist/{playlistId}/{playlistName}/{playlistCoverUrl}"
    const val SONG_DETAIL = "song/{songId}"

    fun album(albumId: Long) = "album/$albumId"
    fun artist(artistId: Long) = "artist/$artistId"
    fun playlist(id: Long, name: String = "", coverUrl: String = "") =
        "playlist/$id/${URLEncoder.encode(name, StandardCharsets.UTF_8.toString())}/${URLEncoder.encode(coverUrl, StandardCharsets.UTF_8.toString())}"
    fun song(songId: Long) = "song/$songId"
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    onSongClick: (SongItem) -> Unit,
    onReplaceAndPlay: (List<SongItem>) -> Unit = {},
    onInsertNext: (List<SongItem>) -> Unit = {},
    onSongInsertNext: (SongItem) -> Unit = {},
    onSongAppendToQueue: (SongItem) -> Unit = {},
    onShowSongMenu: (SongItem, List<SongMenuAction>) -> Unit = { _, _ -> },
    startDestination: String = NavRoutes.HOME
) {
    // 路由层级转场动画：采用 Windows 11 Fluent Design Motion 规范
    // 微位移(80dp 范围) + 微缩放(0.96f->1f) + 柔和淡入淡出，消除掉帧与重排
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fluentEnterTransition() },
        exitTransition = { fluentExitTransition() },
        popEnterTransition = { fluentPopEnterTransition() },
        popExitTransition = { fluentPopExitTransition() }
    ) {
        composable(NavRoutes.HOME) {
            // 不渲染任何内容，由 MainScreen 的 Scaffold 内容填充
        }

        composable(
            route = NavRoutes.ALBUM,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            enterTransition = { fluentEnterTransition() },
            exitTransition = { fluentExitTransition() },
            popEnterTransition = { fluentPopEnterTransition() },
            popExitTransition = { fluentPopExitTransition() }
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                onBack = { navController.popBackStack() },
                onSongClick = onSongClick,
                onArtistClick = { artistId -> navController.navigate(NavRoutes.artist(artistId)) },
                onReplaceAndPlay = onReplaceAndPlay,
                onInsertNext = onInsertNext,
                onSongInsertNext = onSongInsertNext,
                onSongAppendToQueue = onSongAppendToQueue,
                onShowSongMenu = onShowSongMenu
            )
        }

        composable(
            route = NavRoutes.ARTIST,
            arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
            enterTransition = { fluentEnterTransition() },
            exitTransition = { fluentExitTransition() },
            popEnterTransition = { fluentPopEnterTransition() },
            popExitTransition = { fluentPopExitTransition() }
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("artistId") ?: return@composable
            ArtistDetailScreen(
                artistId = artistId,
                onBack = { navController.popBackStack() },
                onSongClick = onSongClick,
                onAlbumClick = { id -> navController.navigate(NavRoutes.album(id)) },
                onSongInsertNext = onSongInsertNext,
                onSongAppendToQueue = onSongAppendToQueue,
                onShowSongMenu = onShowSongMenu
            )
        }

        composable(
            route = NavRoutes.PLAYLIST,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("playlistName") { type = NavType.StringType; defaultValue = "" },
                navArgument("playlistCoverUrl") { type = NavType.StringType; defaultValue = "" }
            ),
            enterTransition = { fluentEnterTransition() },
            exitTransition = { fluentExitTransition() },
            popEnterTransition = { fluentPopEnterTransition() },
            popExitTransition = { fluentPopExitTransition() }
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            // ← 解码
            val name = URLDecoder.decode(
                backStackEntry.arguments?.getString("playlistName") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            val cover = URLDecoder.decode(
                backStackEntry.arguments?.getString("playlistCoverUrl") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            PlaylistDetailScreen(
                playlistId = playlistId,
                playlistName = name,
                playlistCoverUrl = cover,
                onBack = { navController.popBackStack() },
                onSongClick = onSongClick,
                onReplaceAndPlay = onReplaceAndPlay,
                onInsertNext = onInsertNext,
                onSongInsertNext = onSongInsertNext,
                onSongAppendToQueue = onSongAppendToQueue,
                onShowSongMenu = onShowSongMenu
            )
        }

        composable(
            route = NavRoutes.SONG_DETAIL,
            arguments = listOf(navArgument("songId") { type = NavType.LongType }),
            enterTransition = { fluentEnterTransition() },
            exitTransition = { fluentExitTransition() },
            popEnterTransition = { fluentPopEnterTransition() },
            popExitTransition = { fluentPopExitTransition() }
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
            SongDetailScreen(
                songId = songId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}