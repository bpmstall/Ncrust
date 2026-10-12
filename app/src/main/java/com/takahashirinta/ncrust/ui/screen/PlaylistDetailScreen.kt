package com.takahashirinta.ncrust.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.takahashirinta.ncrust.auth.CookieManager
import com.takahashirinta.ncrust.cache.ContentCache
import com.takahashirinta.ncrust.library.LibraryManager
import com.takahashirinta.ncrust.network.PlaylistApi
import com.takahashirinta.ncrust.network.SongItem
import com.takahashirinta.ncrust.ui.BottomOverlayInsetDp
import com.takahashirinta.ncrust.ui.components.AddToPlaylistSheet
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
    var isBatchMode by remember { mutableStateOf(false) }
    val selectedSongIds = remember { mutableStateListOf<Long>() }
    var batchAddToPlaylistSongs by remember { mutableStateOf<List<SongItem>?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val strings = LocalStrings.current

    BackHandler(enabled = isBatchMode) {
        isBatchMode = false
        selectedSongIds.clear()
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            if (isOwnPlaylist) {
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
                                Spacer(Modifier.width(8.dp))
                            }

                            if (songs.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isBatchMode) LocalMetroColors.current.primary.copy(alpha = 0.15f)
                                            else LocalMetroColors.current.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                        .border(
                                            width = 0.75.dp,
                                            color = if (isBatchMode) LocalMetroColors.current.primary
                                            else LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            isBatchMode = !isBatchMode
                                            selectedSongIds.clear()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MetroIcon(
                                        imageVector = AppIcons.Checklist,
                                        contentDescription = null,
                                        tint = LocalMetroColors.current.primary,
                                        sizeDp = 16.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    MetroText(
                                        text = if (isBatchMode) strings.cancel else "批量管理",
                                        color = LocalMetroColors.current.primary,
                                        style = LocalMetroTypography.current.bodySmall
                                    )
                                }
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
                        isSelectionMode = isBatchMode,
                        isSelected = selectedSongIds.contains(song.id),
                        onToggleSelect = {
                            if (selectedSongIds.contains(song.id)) {
                                selectedSongIds.remove(song.id)
                            } else {
                                selectedSongIds.add(song.id)
                            }
                        },
                        onClick = {
                            if (isBatchMode) {
                                if (selectedSongIds.contains(song.id)) {
                                    selectedSongIds.remove(song.id)
                                } else {
                                    selectedSongIds.add(song.id)
                                }
                            } else {
                                onSongClick(song)
                            }
                        },
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
                                    SongMenuAction(
                                        icon = AppIcons.Delete,
                                        label = strings.actionRemoveFromPlaylist,
                                        isDestructive = true
                                    ) {
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

        // 优雅悬浮亚克力批量操作底栏
        AnimatedVisibility(
            visible = isBatchMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomOverlayInsetDp + 8.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalMetroColors.current.surface.copy(alpha = 0.95f))
                    .border(
                        width = 0.75.dp,
                        color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 全选/全不选 + 计数
                val allSelected = songs.isNotEmpty() && selectedSongIds.size == songs.size
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (allSelected) {
                                selectedSongIds.clear()
                            } else {
                                selectedSongIds.clear()
                                selectedSongIds.addAll(songs.map { it.id })
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (allSelected) LocalMetroColors.current.primary else Color.Transparent
                            )
                            .border(
                                width = if (allSelected) 0.dp else 1.5.dp,
                                color = if (allSelected) Color.Transparent else LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (allSelected) {
                            MetroIcon(
                                imageVector = AppIcons.Check,
                                contentDescription = null,
                                tint = LocalMetroColors.current.onPrimary,
                                sizeDp = 12.dp
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    MetroText(
                        text = "${selectedSongIds.size}/${songs.size}",
                        color = LocalMetroColors.current.onBackground,
                        style = LocalMetroTypography.current.bodyMedium
                    )
                }

                // 批量操作项
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasSelection = selectedSongIds.isNotEmpty()
                    val primaryTint = if (hasSelection) LocalMetroColors.current.primary else LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.35f)

                    // 1. 加到歌单
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = hasSelection) {
                                val selectedSongs = songs.filter { it.id in selectedSongIds }
                                batchAddToPlaylistSongs = selectedSongs
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetroIcon(
                                imageVector = AppIcons.Add,
                                contentDescription = null,
                                tint = primaryTint,
                                sizeDp = 18.dp
                            )
                            Spacer(Modifier.width(4.dp))
                            MetroText(
                                text = "加歌单",
                                color = primaryTint,
                                style = LocalMetroTypography.current.bodySmall
                            )
                        }
                    }

                    // 2. 下一首播放
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = hasSelection) {
                                val selectedSongs = songs.filter { it.id in selectedSongIds }
                                selectedSongs.reversed().forEach { onSongInsertNext(it) }
                                Toast.makeText(context, "已将 ${selectedSongs.size} 首歌曲添加到下一首播放", Toast.LENGTH_SHORT).show()
                                isBatchMode = false
                                selectedSongIds.clear()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetroIcon(
                                imageVector = AppIcons.Queue,
                                contentDescription = null,
                                tint = primaryTint,
                                sizeDp = 18.dp
                            )
                            Spacer(Modifier.width(4.dp))
                            MetroText(
                                text = "下一首",
                                color = primaryTint,
                                style = LocalMetroTypography.current.bodySmall
                            )
                        }
                    }

                    // 3. 从歌单移除 (仅自建歌单可用)
                    if (isOwnPlaylist) {
                        val deleteTint = if (hasSelection) Color(0xFFE53935) else LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.35f)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = hasSelection) {
                                    val toRemove = selectedSongIds.toList()
                                    coroutineScope.launch {
                                        val ok = PlaylistApi.removeSongsFromPlaylist(playlistId, toRemove)
                                        if (ok) {
                                            songs = songs.filter { it.id !in toRemove }
                                            ContentCache.putPlaylistSongs(playlistId, songs)
                                            Toast.makeText(context, "已从歌单移除 ${toRemove.size} 首歌曲", Toast.LENGTH_SHORT).show()
                                            selectedSongIds.clear()
                                            isBatchMode = false
                                        } else {
                                            Toast.makeText(context, "移除失败，请重试", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetroIcon(
                                    imageVector = AppIcons.Delete,
                                    contentDescription = null,
                                    tint = deleteTint,
                                    sizeDp = 18.dp
                                )
                                Spacer(Modifier.width(4.dp))
                                MetroText(
                                    text = "移除",
                                    color = deleteTint,
                                    style = LocalMetroTypography.current.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    batchAddToPlaylistSongs?.let { batchList ->
        AddToPlaylistSheet(
            songs = batchList,
            onDismiss = {
                batchAddToPlaylistSongs = null
                isBatchMode = false
                selectedSongIds.clear()
            }
        )
    }
}

