package com.takahashirinta.ncrust.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import io.github.takahashirinta.kanesumi.controls.MetroProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.takahashirinta.ncrust.auth.CookieManager
import com.takahashirinta.ncrust.cache.ContentCache
import com.takahashirinta.ncrust.network.CoverUrls
import com.takahashirinta.ncrust.network.PlaylistApi
import com.takahashirinta.ncrust.network.SongItem
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import io.github.takahashirinta.kanesumi.controls.MetroBottomSheet
import io.github.takahashirinta.kanesumi.controls.MetroDivider
import io.github.takahashirinta.kanesumi.core.insets.metroNavigationBarsPadding
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroTypography
import io.github.takahashirinta.kanesumi.core.theme.MetroIcon
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistSheet(
    song: SongItem,
    onDismiss: () -> Unit
) = AddToPlaylistSheet(songs = listOf(song), onDismiss = onDismiss)

@Composable
fun AddToPlaylistSheet(
    songs: List<SongItem>,
    onDismiss: () -> Unit
) {
    if (songs.isEmpty()) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf(ContentCache.userPlaylists ?: emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(playlists.isEmpty()) }
    var addingToPlaylistId by remember { mutableStateOf<Long?>(null) }
    var addedToPlaylistId by remember { mutableStateOf<Long?>(null) }

    // 新建歌单状态
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingLoading by remember { mutableStateOf(false) }

    // 加载自建歌单
    LaunchedEffect(Unit) {
        if (!CookieManager.hasCookie(context)) {
            isLoadingPlaylists = false
            return@LaunchedEffect
        }
        try {
            val uid = PlaylistApi.getCurrentUserId()
            val result = PlaylistApi.getUserPlaylists(uid, limit = 100)
            // 过滤出用户创建的歌单（creatorUserId == uid）或所有自建普通歌单
            val createdOnly = result.playlists.filter { it.creatorUserId == 0L || it.creatorUserId == uid }
            val list = if (createdOnly.isNotEmpty()) createdOnly else result.playlists
            playlists = list
            ContentCache.userPlaylists = list
        } catch (_: Exception) {
            // 保留既有缓存
        } finally {
            isLoadingPlaylists = false
        }
    }

    fun submitNewPlaylist() {
        val trimmed = newPlaylistName.trim()
        if (trimmed.isEmpty() || isCreatingLoading) return
        isCreatingLoading = true
        coroutineScope.launch {
            try {
                val newId = PlaylistApi.createPlaylist(trimmed)
                if (newId != null && newId != 0L) {
                    // 立即将歌曲加入新歌单
                    PlaylistApi.addSongsToPlaylist(newId, songs.map { it.id })
                    ContentCache.evictPlaylist(newId)
                    // 刷新用户歌单缓存
                    try {
                        val uid = PlaylistApi.getCurrentUserId()
                        val result = PlaylistApi.getUserPlaylists(uid, limit = 100)
                        ContentCache.userPlaylists = result.playlists
                    } catch (_: Exception) {}

                    val msg = if (songs.size == 1) {
                        strings.addToPlaylistSuccess(trimmed)
                    } else {
                        "已将 ${songs.size} 首歌曲加入「$trimmed」"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    delay(300)
                    onDismiss()
                } else {
                    Toast.makeText(context, "创建歌单失败，请重试", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "创建失败", Toast.LENGTH_SHORT).show()
            } finally {
                isCreatingLoading = false
            }
        }
    }

    MetroBottomSheet(
        onDismiss = onDismiss,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fluent 风格顶部拖拽指示条
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.35f))
                )

                Spacer(Modifier.height(14.dp))

                // 当前选中的目标歌曲信息卡片（单曲或批量预览）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalMetroColors.current.surfaceVariant.copy(alpha = 0.35f))
                        .border(
                            width = 0.75.dp,
                            color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstSong = songs.first()
                    AsyncImage(
                        model = CoverUrls.small(firstSong.album?.picUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = 0.75.dp,
                                color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (songs.size == 1) {
                            MetroText(
                                text = firstSong.name,
                                color = LocalMetroColors.current.onBackground,
                                style = LocalMetroTypography.current.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            MetroText(
                                text = firstSong.artists?.joinToString("/") { it.name } ?: strings.unknownArtist,
                                color = LocalMetroColors.current.onSurfaceVariant,
                                style = LocalMetroTypography.current.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            MetroText(
                                text = "已选 ${songs.size} 首歌曲",
                                color = LocalMetroColors.current.onBackground,
                                style = LocalMetroTypography.current.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            MetroText(
                                text = "包含：${songs.take(2).joinToString("、") { it.name }}${if (songs.size > 2) " 等" else ""}",
                                color = LocalMetroColors.current.onSurfaceVariant,
                                style = LocalMetroTypography.current.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) {
        MetroDivider()

        // 新建歌单快速入口
        AnimatedContent(
            targetState = isCreatingNew,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "CreateNewPlaylistRow"
        ) { creating ->
            if (!creating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 0.75.dp,
                            color = LocalMetroColors.current.primary.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { isCreatingNew = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroIcon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = LocalMetroColors.current.primary,
                        sizeDp = 20.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    MetroText(
                        text = strings.createNewPlaylist,
                        color = LocalMetroColors.current.primary,
                        style = LocalMetroTypography.current.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalMetroColors.current.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = 0.75.dp,
                            color = LocalMetroColors.current.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = LocalMetroColors.current.onBackground,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(LocalMetroColors.current.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitNewPlaylist() }),
                        decorationBox = { innerTextField ->
                            if (newPlaylistName.isEmpty()) {
                                MetroText(
                                    text = strings.newPlaylistNamePlaceholder,
                                    color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = LocalMetroTypography.current.bodyMedium
                                )
                            }
                            innerTextField()
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isCreatingLoading) {
                        MetroProgressIndicator(
                            sizeDp = 18.dp,
                            color = LocalMetroColors.current.primary
                        )
                    } else {
                        // 确认按钮
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { submitNewPlaylist() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            MetroText(
                                text = strings.confirmText,
                                color = LocalMetroColors.current.primary,
                                style = LocalMetroTypography.current.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // 取消按钮
                        MetroIcon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = LocalMetroColors.current.onSurfaceVariant,
                            sizeDp = 18.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    isCreatingNew = false
                                    newPlaylistName = ""
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        // 歌单列表
        if (isLoadingPlaylists) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                MetroProgressIndicator(
                    sizeDp = 24.dp,
                    color = LocalMetroColors.current.primary
                )
            }
        } else if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                MetroText(
                    text = strings.noPlaylists,
                    color = LocalMetroColors.current.onSurfaceVariant,
                    style = LocalMetroTypography.current.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    val isAdding = addingToPlaylistId == pl.id
                    val isAdded = addedToPlaylistId == pl.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 0.75.dp,
                                color = if (isAdded) {
                                    LocalMetroColors.current.primary.copy(alpha = 0.4f)
                                } else {
                                    LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.10f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isAdded) {
                                    LocalMetroColors.current.primary.copy(alpha = 0.08f)
                                } else {
                                    LocalMetroColors.current.surfaceVariant.copy(alpha = 0.2f)
                                }
                            )
                            .clickable(enabled = !isAdding && !isAdded) {
                                addingToPlaylistId = pl.id
                                coroutineScope.launch {
                                    val res = PlaylistApi.addSongsToPlaylist(pl.id, songs.map { it.id })
                                    when (res) {
                                        PlaylistApi.TrackOpResult.SUCCESS -> {
                                            addedToPlaylistId = pl.id
                                            addingToPlaylistId = null
                                            ContentCache.evictPlaylist(pl.id)
                                            val msg = if (songs.size == 1) {
                                                strings.addToPlaylistSuccess(pl.name)
                                            } else {
                                                "已将 ${songs.size} 首歌曲加入「${pl.name}」"
                                            }
                                            Toast.makeText(
                                                context,
                                                msg,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            delay(420)
                                            onDismiss()
                                        }
                                        PlaylistApi.TrackOpResult.ALREADY_EXISTS -> {
                                            addingToPlaylistId = null
                                            Toast.makeText(
                                                context,
                                                if (songs.size == 1) strings.songAlreadyInPlaylist else "部分歌曲已在歌单中",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        PlaylistApi.TrackOpResult.FAILED -> {
                                            addingToPlaylistId = null
                                            Toast.makeText(
                                                context,
                                                "添加失败，请重试",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = CoverUrls.small(pl.coverImgUrl),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = 0.75.dp,
                                    color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            MetroText(
                                text = pl.name,
                                color = LocalMetroColors.current.onBackground,
                                style = LocalMetroTypography.current.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            MetroText(
                                text = strings.trackCountSongs(pl.trackCount),
                                color = LocalMetroColors.current.onSurfaceVariant,
                                style = LocalMetroTypography.current.bodySmall
                            )
                        }

                        // 右侧状态微动效反馈
                        AnimatedVisibility(
                            visible = isAdding,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            MetroProgressIndicator(
                                sizeDp = 20.dp,
                                color = LocalMetroColors.current.primary
                            )
                        }

                        AnimatedVisibility(
                            visible = isAdded,
                            enter = scaleIn(spring()) + fadeIn(),
                            exit = fadeOut()
                        ) {
                            MetroIcon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = LocalMetroColors.current.primary,
                                sizeDp = 22.dp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.metroNavigationBarsPadding())
    }
}
