package com.openlist.client.data.remote

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

/**
 * 按块写入文件并回报已发送字节数。
 * 回调发生在 OkHttp 的工作线程上，调用方需自行保证线程安全。
 */
class ProgressRequestBody(
    private val file: File,
    private val onProgress: (uploaded: Long, total: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType = "application/octet-stream".toMediaType()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var uploaded = 0L
        // 连接重试会再次调用 writeTo，进度需要从头计算
        onProgress(0L, total)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read
                onProgress(uploaded, total)
            }
        }
    }
}
