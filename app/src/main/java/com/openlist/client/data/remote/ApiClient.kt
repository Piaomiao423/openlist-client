package com.openlist.client.data.remote

import com.openlist.client.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var baseUrl: String = ""

    /**
     * 全应用共享的底层客户端，复用同一个连接池。
     * 每次新建 OkHttpClient 都要重新做 DNS；在模拟器等环境下系统 DNS 代理可能卡住数十秒，
     * 导致播放器首次取流长时间黑屏，因此媒体流等场景应基于该实例 newBuilder()。
     */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(CleartextGuard.Interceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // 同步上传大文件可能需要较长时间，写入超时放宽到 10 分钟
            .writeTimeout(600, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun getApi(serverUrl: String): OpenListApi {
        val normalized = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val cached = retrofit
        if (cached == null || baseUrl != normalized) {
            baseUrl = normalized
            val clientBuilder = sharedClient.newBuilder()
            // 仅在 Debug 模式打印 HTTP 日志，避免 Release 把 Token 打到 logcat
            if (BuildConfig.DEBUG) {
                clientBuilder.addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
            retrofit = Retrofit.Builder()
                .baseUrl(normalized)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(OpenListApi::class.java)
    }

    /** 大文件下载专用客户端：读超时不限时，连接池与其他客户端共享 */
    fun getDownloadClient(): OkHttpClient =
        sharedClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    @Synchronized
    fun reset() {
        retrofit = null
        baseUrl = ""
    }
}
