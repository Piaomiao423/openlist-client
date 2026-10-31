package com.openlist.client.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.favoritesDataStore by preferencesDataStore(name = "openlist_favorites")

/**
 * 本地收藏夹：按服务器隔离、按文件路径收藏，持久化在 DataStore。
 * 同一服务器下的多个账户共享收藏，切换服务器不会串数据。
 */
class FavoritesManager(private val context: Context) {

    private companion object {
        /** 当前格式：JSON 对象，key 为服务器地址，value 为该服务器的收藏路径数组 */
        val KEY_BY_SERVER = stringPreferencesKey("favorites_by_server")

        /** 旧版格式：未区分服务器的路径集合，首次打开时迁移到当前服务器 */
        val KEY_LEGACY = stringSetPreferencesKey("favorites")
    }

    fun favorites(serverUrl: String): Flow<Set<String>> =
        context.favoritesDataStore.data.map { prefs ->
            decode(prefs[KEY_BY_SERVER])[serverUrl] ?: emptySet()
        }

    suspend fun isFavorite(serverUrl: String, path: String): Boolean =
        favorites(serverUrl).first().contains(path)

    /** 切换收藏状态，返回切换后是否已收藏 */
    suspend fun toggle(serverUrl: String, path: String): Boolean {
        var nowFavorite = false
        context.favoritesDataStore.edit { prefs ->
            val map = decode(prefs[KEY_BY_SERVER])
            val current = map[serverUrl].orEmpty()
            nowFavorite = path !in current
            val updated = if (nowFavorite) current + path else current - path
            if (updated.isEmpty()) map.remove(serverUrl) else map[serverUrl] = updated
            prefs[KEY_BY_SERVER] = encode(map)
        }
        return nowFavorite
    }

    suspend fun remove(serverUrl: String, path: String) {
        context.favoritesDataStore.edit { prefs ->
            val map = decode(prefs[KEY_BY_SERVER])
            val updated = map[serverUrl].orEmpty() - path
            if (updated.isEmpty()) map.remove(serverUrl) else map[serverUrl] = updated
            prefs[KEY_BY_SERVER] = encode(map)
        }
    }

    /** 旧版收藏无法区分服务器，归入首次打开时登录的服务器 */
    suspend fun migrateLegacy(serverUrl: String) {
        if (serverUrl.isBlank()) return
        context.favoritesDataStore.edit { prefs ->
            val legacy = prefs[KEY_LEGACY] ?: return@edit
            prefs.remove(KEY_LEGACY)
            if (legacy.isEmpty()) return@edit
            val map = decode(prefs[KEY_BY_SERVER])
            map[serverUrl] = map[serverUrl].orEmpty() + legacy
            prefs[KEY_BY_SERVER] = encode(map)
        }
    }

    private fun decode(raw: String?): MutableMap<String, Set<String>> {
        if (raw.isNullOrBlank()) return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { server ->
                    val arr = obj.optJSONArray(server) ?: return@forEach
                    put(server, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun encode(map: Map<String, Set<String>>): String = JSONObject().apply {
        map.forEach { (server, paths) ->
            put(server, JSONArray().apply { paths.forEach { put(it) } })
        }
    }.toString()
}