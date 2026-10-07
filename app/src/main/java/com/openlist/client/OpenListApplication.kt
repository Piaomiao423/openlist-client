package com.openlist.client

import android.app.Application
import com.openlist.client.util.FavoritesManager
import com.openlist.client.util.SessionManager

/**
 * 全局 Application：持有 SessionManager（多账户 Token 持久化 + 登录状态）
 * 与 FavoritesManager（本地收藏夹）。
 */
class OpenListApplication : Application() {
    val sessionManager: SessionManager by lazy { SessionManager(this) }
    val favoritesManager: FavoritesManager by lazy { FavoritesManager(this) }
}
