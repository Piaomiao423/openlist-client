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

    @Synchronized
    fun getApi(serverUrl: String): OpenListApi {
        val normalized = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val cached = retrofit
        if (cached == null || baseUrl != normalized) {
            baseUrl = normalized
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                // 同步上传大文件可能需要较长时间，写入超时放宽到 10 分钟
                .writeTimeout(600, TimeUnit.SECONDS)
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

    /** 大文件下载专用客户端：读超时不限时 */
    fun getDownloadClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    @Synchronized
    fun reset() {
        retrofit = null
        baseUrl = ""
    }
}
