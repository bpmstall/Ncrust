package com.takahashirinta.ncrust.player

/** True only while this fetch still owns the current song's lyric state. */
internal fun ownsLyricsRequest(
    requestGeneration: Long,
    currentGeneration: Long,
    songId: Long,
    currentSongId: Long?
): Boolean = requestGeneration == currentGeneration && songId == currentSongId
