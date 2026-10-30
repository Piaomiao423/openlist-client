package com.openlist.client

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.openlist.client.data.remote.CleartextGuard
import com.openlist.client.util.FavoritesManager
import com.openlist.client.util.SessionManager
import okhttp3.OkHttpClient

/**
 * 全局 Application：持有 SessionManager（多账户 Token 持久化 + 登录状态）
 * 与 FavoritesManager（本地收藏夹）。
 */
class OpenListApplication : Application(), ImageLoaderFactory {

    val sessionManager: SessionManager by lazy { SessionManager(this) }
    val favoritesManager: FavoritesManager by lazy { FavoritesManager(this) }

    /** Coil 默认网络栈同样套用明文 HTTP 策略，避免绕过 API 层的限制 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(CleartextGuard.Interceptor())
                    .build()
            }
            .build()
}
