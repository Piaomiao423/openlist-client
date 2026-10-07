package com.openlist.client.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = "openlist_favorites")

/** 本地收藏夹：按文件路径收藏，持久化在 DataStore */
class FavoritesManager(private val context: Context) {

    private companion object {
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
    }

    val favorites: Flow<Set<String>> = context.favoritesDataStore.data
        .map { it[KEY_FAVORITES] ?: emptySet() }

    suspend fun isFavorite(path: String): Boolean = favorites.first().contains(path)

    /** 切换收藏状态，返回切换后是否已收藏 */
    suspend fun toggle(path: String): Boolean {
        var nowFavorite = false
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES] ?: emptySet()
            nowFavorite = !current.contains(path)
            prefs[KEY_FAVORITES] = if (nowFavorite) current + path else current - path
        }
        return nowFavorite
    }

    suspend fun remove(path: String) {
        context.favoritesDataStore.edit { prefs ->
            prefs[KEY_FAVORITES] = (prefs[KEY_FAVORITES] ?: emptySet()) - path
        }
    }
}
