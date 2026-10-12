package com.takahashirinta.ncrust.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.takahashirinta.ncrust.auth.CookieManager
import com.takahashirinta.ncrust.cache.ContentCache
import com.takahashirinta.ncrust.library.LibraryManager
import com.takahashirinta.ncrust.network.PlaylistApi
import com.takahashirinta.ncrust.network.SongItem
import com.takahashirinta.ncrust.ui.components.DetailHeader
import com.takahashirinta.ncrust.ui.components.DetailScaffold
import com.takahashirinta.ncrust.ui.components.EditPlaylistDialog
import com.takahashirinta.ncrust.ui.components.PlayAllDialog
import com.takahashirinta.ncrust.ui.components.SongCard
import com.takahashirinta.ncrust.ui.components.SongCardStyle
import com.takahashirinta.ncrust.ui.components.SongMenuAction
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import com.takahashirinta.ncrust.ui.icons.AppIcons
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroTypography
import io.github.takahashirinta.kanesumi.core.theme.MetroIcon
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String = "",
    playlistCoverUrl: String = "",
    onBack: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onReplaceAndPlay: (List<SongItem>) -> Unit = {},
    onInsertNext: (List<SongItem>) -> Unit = {},
    onSongInsertNext: (SongItem) -> Unit = {},
    onSongAppendToQueue: (SongItem) -> Unit = {},
    onShowSongMenu: (SongItem, List<SongMenuAction>) -> Unit = { _, _ -> }
) {
    val cached = remember(playlistId) { ContentCache.getPlaylistSongs(playlistId) }
    var songs by remember(playlistId) { mutableStateOf(cached ?: emptyList()) }
    var currentName by remember(playlistId, playlistName) { mutableStateOf(playlistName) }
    var isLoading by remember(playlistId) { mutableStateOf(cached == null) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }
    var showPlayAllDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isOwnPlaylist by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val strings = LocalStrings.current

    // 判断是否为用户自建/拥有管理权限的歌单
    LaunchedEffect(playlistId) {
        if (CookieManager.hasCookie(context)) {
            try {
                val uid = PlaylistApi.getCurrentUserId()
                val userPlaylists = ContentCache.userPlaylists ?: PlaylistApi.getUserPlaylists(uid).playlists
                isOwnPlaylist = userPlaylists.any { it.id == playlistId && (it.creatorUserId == 0L || it.creatorUserId == uid) }
            } catch (_: Exception) {}
        }
    }

    fun loadSongs() {
        coroutineScope.launch {
            if (songs.isEmpty()) isLoading = true
            error = null
            try {
                val fresh = PlaylistApi.getPlaylistDetail(playlistId)
                songs = fresh
                ContentCache.putPlaylistSongs(playlistId, fresh)
            } catch (e: Exception) {
                if (songs.isEmpty()) error = strings.loadFailed(e.message)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(playlistId) { loadSongs() }

    val coverUrl = playlistCoverUrl.ifEmpty { songs.firstOrNull()?.album?.picUrl }

    if (showPlayAllDialog) {
        PlayAllDialog(
            songCount = songs.size,
            onDismiss = { showPlayAllDialog = false },
            onReplaceAndPlay = { onReplaceAndPlay(songs) },
            onInsertNext = { onInsertNext(songs) }
        )
    }

    if (showEditDialog) {
        EditPlaylistDialog(
            playlistId = playlistId,
            initialName = currentName.ifEmpty { strings.categoryPlaylists },
            onDismiss = { showEditDialog = false },
            onSuccess = { newName, _ ->
                currentName = newName
            }
        )
    }

    DetailScaffold(
        title = currentName.ifEmpty { strings.playlistDetailTitle },
        subtitle = if (songs.isNotEmpty()) strings.trackCountSongs(songs.size) else null,
        coverUrl = coverUrl,
        onBack = onBack,
        isLoading = isLoading,
        hasCachedContent = songs.isNotEmpty(),
        error = error,
        onRetry = { loadSongs() },
        onPlayAll = if (songs.isNotEmpty()) ({ showPlayAllDialog = true }) else null,
        header = {
            DetailHeader(
                coverUrl = coverUrl,
                title = currentName.ifEmpty { strings.categoryPlaylists },
                subtitle = null,
                infoLines = listOf(strings.trackCountSongs(songs.size)),
                onPlayAll = if (songs.isNotEmpty()) ({ showPlayAllDialog = true }) else null,
                headerActions = {
                    if (isOwnPlaylist) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(LocalMetroColors.current.surfaceVariant.copy(alpha = 0.35f))
                                .border(
                                    width = 0.75.dp,
                                    color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { showEditDialog = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetroIcon(
                                imageVector = AppIcons.Edit,
                                contentDescription = null,
                                tint = LocalMetroColors.current.primary,
                                sizeDp = 16.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            MetroText(
                                text = strings.editPlaylistTitle,
                                color = LocalMetroColors.current.primary,
                                style = LocalMetroTypography.current.bodySmall
                            )
                        }
                    }
                }
            )
        },
        content = {
            items(songs, key = { it.id }) { song ->
                SongCard(
                    song = song,
                    style = SongCardStyle.COMPACT,
                    onClick = { onSongClick(song) },
                    onShowMenu = {
                        val actions = mutableListOf(
                            SongMenuAction(AppIcons.Add, strings.actionAddToLibrary) {
                                LibraryManager.saveSong(context, song)
                                Toast.makeText(context, strings.addedToLibrary, Toast.LENGTH_SHORT).show()
                            },
                            SongMenuAction(AppIcons.Queue, strings.actionInsertNext) {
                                onSongInsertNext(song)
                            },
                            SongMenuAction(AppIcons.Queue, strings.actionAppendToQueue) {
                                onSongAppendToQueue(song)
                            }
                        )
                        if (isOwnPlaylist) {
                            actions.add(
                                SongMenuAction(AppIcons.Delete, strings.actionRemoveFromPlaylist) {
                                    coroutineScope.launch {
                                        val ok = PlaylistApi.removeSongFromPlaylist(playlistId, song.id)
                                        if (ok) {
                                            songs = songs.filter { it.id != song.id }
                                            ContentCache.putPlaylistSongs(playlistId, songs)
                                            Toast.makeText(context, strings.removeFromPlaylistSuccess, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "移除失败，请重试", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        onShowSongMenu(song, actions)
                    }
                )
            }
        }
    )
}

