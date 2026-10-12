package com.takahashirinta.ncrust.ui.i18n

data class Strings(
    // Navigation tabs
    val tabHome: String,
    val tabLibrary: String,
    val tabSearch: String,
    val tabUser: String,

    // Common
    val cancel: String,
    val close: String,
    val retry: String,
    val loading: String,
    val back: String,

    // Auth / Account
    val accountDialogTitle: String,
    val nicknameLabel: (String) -> String,
    val uidLabel: (String) -> String,
    val logoutButton: String,
    val notLoggedIn: String,
    val loginHint: String,

    // User screen — sections
    val qualitySectionTitle: String,
    val wifiQualityLabel: String,
    val mobileQualityLabel: String,
    val qualityOptions: List<String>,
    val playbackSectionTitle: String,
    val gaplessSectionTitle: String,
    val gaplessDescription: String,
    val lyricsTranslationLabel: String,
    val themeSectionTitle: String,
    val themeModeSectionTitle: String,
    val themeModeSystem: String,
    val themeModeDark: String,
    val themeModeLight: String,
    val themeColorNames: List<String>,
    val languageSectionTitle: String,
    val aboutButton: String,
    val storageSectionTitle: String,
    val cacheSizeLabel: (Long) -> String,
    val clearCache: String,
    val clearCacheConfirm: String,
    val cacheCleared: String,
    val hyperosIslandTitle: String = "Xiaomi HyperOS 焦点通知",
    val hyperosIslandDesc: String = "在小米澎湃 OS 状态栏与胶囊岛展示播放卡片及控制 (Live Updates)",
    val predictiveBackLyricsTitle: String = "歌词预测性手势返回",
    val predictiveBackLyricsDesc: String = "在歌词界面侧滑返回时先收起歌词回到封面，避免误触直接退出播放器",
    val lowPowerModeTitle: String = "性能与低功耗模式",
    val lowPowerModeDesc: String = "降低前后台动画负载，减少电量消耗并提升流畅度",
    val coverCacheLabel: (Long) -> String = { "封面缓存: ${formatCacheBytes(it)}" },
    val lyricsCacheLabel: (Long) -> String = { "歌词缓存: ${formatCacheBytes(it)}" },

    // Home screen
    val dailySongsTitle: String,
    val recommendPlaylistTitle: String,
    val newSongsTitle: String,
    val refreshLabel: String,
    val noMoreContent: String,
    val trackCountSongs: (Int) -> String,
    // 主页推荐歌单里首位的私人 FM 电台卡（标题用用户昵称, 不是 uid）
    val fmRadioTitle: (String) -> String,
    // 拿不到用户资料时电台卡仍常驻的通用标题
    val fmRadioTitleGeneric: String,
    val fmRadioSubtitle: String,

    // Library screen
    val categoryTracks: String,
    val categoryAlbums: String,
    val categoryPlaylists: String,
    val categoryHistory: String,
    val noSavedSongs: String,
    val noSavedAlbums: String,
    val noPlaylists: String,
    val notLoggedInForPlaylists: String,
    val loadFailed: (String?) -> String,
    val trackCount: (Int) -> String,
    val albumArtistAndCount: (String, Int) -> String,

    // Search screen
    val searchCategoryTracks: String,
    val searchCategoryAlbums: String,
    val searchCategoryArtists: String,
    val searchPlaceholder: String,
    val searchSongsEmpty: String,
    val searchAlbumsEmpty: String,
    val searchArtistsEmpty: String,

    // Player controls
    val prevButton: String,
    val playButton: String,
    val pauseButton: String,
    val nextButton: String,
    val lyricsButton: String,
    val queueButton: String,
    val addToLibraryButton: String,

    // Song / queue actions
    val actionAddToLibrary: String,
    val actionInsertNext: String,
    val actionAppendToQueue: String,
    val actionGoToArtist: String,
    val actionGoToAlbum: String,

    // Background activity permission (battery optimization whitelist)
    val batteryTitle: String,
    val batteryMessage: String,
    val batteryAllow: String,
    val batteryLater: String,

    // Native QR login (tablet / large screen)
    val qrLoginTitle: String,
    val qrScanHint: String,
    val qrScannedHint: String,
    val qrExpiredHint: String,
    val qrLoadFailed: String,
    val qrGenericLogin: String,

    // Phone-side QR scan to authorize another device (LAN cookie handoff)
    val scanEntryTitle: String,
    val scanPrompt: String,
    val scanPermissionNeeded: String,
    val scanNoCookie: String,
    val scanSuccess: String,
    val scanFailed: String,
    val scanConnecting: String,

    val clearQueue: String,
    // 播放模式五档的菜单文案（点模式按钮直接选，不再循环点五下）。
    val playModeCycle: String,
    val playModeSingle: String,
    val playModeShuffle: String,
    val playModeLine: String,
    val playModeInfinity: String,
    // 「我喜欢的音乐」播放全部时的第二选项：按红心歌单推荐，而不是按收藏顺序。
    val playModeHeartbeat: String,
    val actionAddToPlaylist: String,
    val actionRemoveFromLibrary: String,
    val actionSaveAlbum: String,
    // 专辑已收藏时的按钮文案(取消收藏)
    val actionUnsaveAlbum: String,
    val unknownArtist: String,
    val playAllButton: String,

    // Play-all dialog
    val songCountFormat: (Int) -> String,
    val playNowTitle: String,
    val playNowDesc: String,
    val insertNextTitle: String,
    val insertNextDesc: String,
    val heartbeatDesc: String,

    // About
    val aboutTitle: String,
    val aboutAppSubtitle: String,
    val aboutSectionProject: String,
    val aboutVersion: String,
    val aboutDeveloper: String,
    val aboutLicense: String,
    val aboutRepository: String,
    val aboutSectionTechStack: String,
    val aboutLangLabel: String,
    val aboutUIFrameworkLabel: String,
    val aboutDesignSystemLabel: String = "Design System",
    val aboutAudioEngineLabel: String,
    val aboutNetworkLabel: String,
    val aboutImageLabel: String,
    val aboutSectionTeam: String,
    val aboutRoleDev: String,
    val aboutRoleTester: String,
    val aboutSectionCredits: String,
    val aboutCreditCli: String,
    val aboutCreditAnim: String,
    val aboutCreditDesign: String,

    // Player UI
    val noLyrics: String,
    val emptyQueue: String,
    val collapsePlayer: String,

    // Detail page titles and content
    val artistDetailTitle: String,
    val albumDetailTitle: String,
    val playlistDetailTitle: String,
    val unknownArtistName: String,
    val noAlbums: String,
    val noHotSongs: String,
    val artistAlbumCount: (Int) -> String,
    val artistSongCount: (Int) -> String,
    val albumReleaseDate: (String) -> String,
    val albumLabel: (String) -> String,
    val artistDataLoadFailed: (Int) -> String,
    val artistStats: (Int, Int) -> String,

    // Accessibility content descriptions
    val coverDesc: String,
    val albumCoverDesc: String,
    val artistAvatarDesc: String,
    val playlistCoverDesc: String,
    val userAvatarDesc: String,

    // Search
    val clearSearchButton: String,

    // Song detail screen
    val songDetailTitle: String,
    val unknownAlbum: String,
    val lyricsLabel: String,

    // Player queue panel
    val queueTitle: String,
    val playModeButton: String,
    val saveAsPlaylist: String,
    val noSongPlaying: String,
    val queueSectionPast: String,
    val queueSectionNow: String,
    val queueSectionUpcoming: String,
    val queueInfinityPlaceholder: String,
    val queueClearAll: String,

    // User screen
    val userIconDesc: String,

    // Search history
    val searchHistoryClear: String,
    val searchHistoryEmpty: String,
    val searchHistoryDelete: String,

    // Feedback toasts
    val addedToLibrary: String,
    val removedFromLibrary: String,

    // Cloud sync state (local-only optimistic likes awaiting server confirmation)
    val pendingSyncCount: (Int) -> String,
    val retrySync: String,

    // Sharing
    val actionShare: String,
    val shareChooserTitle: String,

    // Playlist Edit & Song Management
    val actionAddToUserPlaylist: String = "添加到歌单",
    val actionRemoveFromPlaylist: String = "从歌单中移除",
    val editPlaylistTitle: String = "编辑歌单",
    val editPlaylistNameLabel: String = "歌单名称",
    val editPlaylistDescLabel: String = "歌单描述",
    val createNewPlaylist: String = "新建歌单",
    val newPlaylistNamePlaceholder: String = "请输入新歌单名称",
    val addToPlaylistSuccess: (String) -> String = { "已添加到歌单 $it" },
    val songAlreadyInPlaylist: String = "歌曲已在歌单中",
    val removeFromPlaylistSuccess: String = "已从歌单中移除",
    val removeFromPlaylistConfirm: String = "确定从歌单中移除这首歌吗？",
    val editPlaylistSuccess: String = "歌单信息已更新",
    val saveText: String = "保存",
    val cancelText: String = "取消",
    val confirmText: String = "确定",
)

/** 字节数格式化为人类可读的 B/KB/MB/GB，供 cacheSizeLabel 复用。 */
fun formatCacheBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
