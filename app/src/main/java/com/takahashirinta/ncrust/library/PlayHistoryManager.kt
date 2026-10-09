package com.takahashirinta.ncrust.library

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.takahashirinta.ncrust.network.SongItem

/**
 * 最近播放。存完整 SongItem，点了就能直接播，不用再查。
 * 上限 100，重复播放把旧记录挪到最前。写盘走 apply，不阻塞切歌。
 */
object PlayHistoryManager {
    private const val PREFS_NAME = "play_history"
    private const val KEY = "songs"
    private const val MAX_ITEMS = 100

    private data class Entry(val song: SongItem, val timestamp: Long)

    fun record(context: Context, song: SongItem) {
        val items = load(context)
        items.removeAll { it.song.id == song.id }
        items.add(0, Entry(song, System.currentTimeMillis()))
        if (items.size > MAX_ITEMS) items.subList(MAX_ITEMS, items.size).clear()
        save(context, items)
    }

    fun recent(context: Context): List<SongItem> = load(context).map { it.song }

    fun clear(context: Context) = save(context, mutableListOf())

    private fun load(context: Context): MutableList<Entry> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Entry>>() {}.type
            Gson().fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(context: Context, items: List<Entry>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, Gson().toJson(items)).apply()
    }
}
