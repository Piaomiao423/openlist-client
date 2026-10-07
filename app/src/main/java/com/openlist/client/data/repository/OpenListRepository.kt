package com.openlist.client.data.repository

import android.net.Uri
import com.openlist.client.data.model.*
import com.openlist.client.data.remote.ApiClient
import com.openlist.client.data.remote.OpenListApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

/** 表示 Token 失效，UI 层应引导用户重新登录 */
class UnauthorizedException : Exception("登录已过期，请重新登录")

/** 解析后的下载信息：真实地址 + 需要携带的请求头 + 是否需要认证 */
data class DownloadInfo(
    val url: String,
    val headers: Map<String, String>,
    /** true = 直链需携带 Authorization 才能访问（如 /p/ 代理路由），不能用于无头播放器流式预览 */
    val requiresAuth: Boolean = false
)

class OpenListRepository(
    private val serverUrl: String,
    private val token: String,
    private val cacheDir: File
) {

    private val api: OpenListApi get() = ApiClient.getApi(serverUrl)

    private fun authHeader(): String = token

    /** OpenList 的 raw_url 是相对路径，需要拼接服务器地址 */
    private fun normalizeUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw
        } else {
            serverUrl.trimEnd('/') + if (raw.startsWith("/")) raw else "/$raw"
        }
    }

    /**
     * URL 容错编码：OpenList 直链可能含中文/空格，OkHttp 会拒绝。
     * 对路径段统一 decode→encode，query 保留签名所需字符。
     */
    private fun encodeUrl(url: String): String {
        return try {
            val u = Uri.parse(url)
            val scheme = u.scheme
            val authority = u.authority
            if (scheme == null || authority == null) return url
            val path = u.path
                ?.split('/')
                ?.joinToString("/") { seg -> Uri.encode(Uri.decode(seg)) }
                ?: ""
            val query = u.query?.let {
                "?${Uri.encode(it, "=&?%+:/,-._~!*'()")}"
            } ?: ""
            "$scheme://$authority$path$query"
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            url
        }
    }

    /** 解析 OpenList 返回的 header 字段（JSON 字符串 → Map） */
    private fun parseHeaderJson(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = HashMap<String, String>()
            obj.keys().forEach { k ->
                val v = obj.optString(k)
                if (v.isNotBlank()) result[k] = v
            }
            result
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 收集所有可用的下载候选，按优先级排列：
     * 1. /api/fs/link（AList/OpenList 标准方式，返回真实地址 + 需携带请求头）
     * 2. /api/fs/get 的 raw_url
     * 3. /p/ 代理路由（带认证即可访问，网页端代理流同款，最可靠，必兜底）
     */
    private suspend fun downloadCandidates(path: String): List<DownloadInfo> {
        val candidates = mutableListOf<DownloadInfo>()
        // 方式一：/api/fs/link
        try {
            val resp = api.linkFile(FileLinkRequest(path), authHeader())
            if (resp.code() == 401) throw UnauthorizedException()
            if (resp.isSuccessful) {
                val body = resp.body()
                val url = normalizeUrl(body?.data?.url)
                if (body?.code == 200 && url != null) {
                    candidates += DownloadInfo(encodeUrl(url), parseHeaderJson(body.data?.header))
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: UnauthorizedException) { throw e } catch (e: Exception) {
            // link 接口不可用则走下一级
        }
        // 方式二：/api/fs/get 的 raw_url
        try {
            val resp = api.getFile(FileGetRequest(path), authHeader())
            if (resp.code() == 401) throw UnauthorizedException()
            if (resp.isSuccessful) {
                val body = resp.body()
                val url = normalizeUrl(body?.data?.rawUrl ?: body?.data?.url)
                if (body?.code == 200 && url != null) {
                    candidates += DownloadInfo(encodeUrl(url), parseHeaderJson(body?.data?.header))
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: UnauthorizedException) { throw e } catch (e: Exception) {
            // 继续兜底
        }
        // 方式三：/p/ 代理路由（服务器代理转发，仅需认证头）
        val proxyPath = path
            .split('/')
            .joinToString("/") { seg ->
                if (seg.isEmpty()) "" else Uri.encode(Uri.decode(seg))
            }
        candidates += DownloadInfo(
            url = "${serverUrl.trimEnd('/')}/p/$proxyPath",
            headers = emptyMap(),
            requiresAuth = true
        )
        return candidates
    }

    /** 取第一个可用下载候选（供流式预览 / 获取链接使用） */
    private suspend fun resolveDownload(path: String): Result<DownloadInfo> {
        val list = downloadCandidates(path)
        return if (list.isEmpty()) Result.failure(Exception("无法获取下载链接"))
        else Result.success(list.first())
    }

    /** 获取下载信息（直链 + 需携带请求头），供流式预览/下载使用 */
    suspend fun getDownloadInfo(path: String): Result<DownloadInfo> = resolveDownload(path)

    /** 获取下载链接（用于列表页 Toast 展示） */
    suspend fun getDownloadUrl(path: String): Result<String> = try {
        resolveDownload(path).map { it.url }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 下载文件到本地缓存（用于预览/下载到手机），返回带扩展名的缓存文件。
     * 逐级尝试全部候选方式（link → get → /p/），全部失败时返回带明细的错误。
     */
    suspend fun downloadToCache(path: String): Result<File> {
        return try {
            val candidates = downloadCandidates(path)
            if (candidates.isEmpty()) return Result.failure(Exception("无法获取下载链接"))
        val ext = path.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= 10 && it.all { c -> c.isLetterOrDigit() } }
            ?: ""
        val previewDir = File(cacheDir, "preview").apply { mkdirs() }
        val client = ApiClient.getDownloadClient()
        val errors = mutableListOf<String>()

        for ((index, info) in candidates.withIndex()) {
            val cacheFile = File(
                previewDir,
                "preview_${System.currentTimeMillis()}_$index${if (ext.isNotEmpty()) ".$ext" else ""}"
            )
            try {
                fun buildRequest(withAuth: Boolean): okhttp3.Request {
                    val builder = okhttp3.Request.Builder().url(info.url)
                    info.headers.forEach { (k, v) ->
                        if (!k.equals("Authorization", true)) builder.header(k, v)
                    }
                    if (withAuth && token.isNotBlank()) builder.header("Authorization", authHeader())
                    return builder.build()
                }
                var response = client.newCall(buildRequest(true)).execute()
                if (response.code == 401 || response.code == 403) {
                    // 部分部署直链公开（签名 URL），认证头反而多余，去掉重试一次
                    response.close()
                    response = client.newCall(buildRequest(false)).execute()
                }
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw Exception("HTTP ${resp.code}")
                    }
                    val responseBody = resp.body ?: throw Exception("响应体为空")
                    responseBody.byteStream().use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                return Result.success(cacheFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors += "方式${index + 1}(${info.url.take(60)}): ${e.message ?: "未知错误"}"
            }
        }
        Result.failure(Exception("下载失败（${candidates.size} 种方式均不可用）：${errors.joinToString(" | ")}"))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(path: String = "/"): Result<List<FileItem>> = try {
        val response = api.listFiles(FileListRequest(path = path), authHeader())
        when {
            response.code() == 401 -> Result.failure(UnauthorizedException())
            response.isSuccessful -> {
                val body = response.body()
                if (body?.code == 200) Result.success(body.data?.content ?: emptyList())
                else Result.failure(Exception(body?.message ?: "未知错误"))
            }
            else -> Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(e)
    }

    /** AList/OpenList 要求 File-Path 为 URL 编码的完整路径（保留 / 分隔符） */
    private fun encodeFilePath(path: String): String =
        path.split('/').joinToString("/") { seg ->
            if (seg.isEmpty()) "" else Uri.encode(Uri.decode(seg))
        }

    /**
     * 上传（多层防御，杜绝"假成功"）：
     * 1. File-Path 请求头上传（标准）；失败时清理可能残留的半截文件，回退 query 方式
     * 2. 若返回异步任务（OpenList 强制任务队列），轮询 /api/task/upload/info 直到结束
     * 3. 上传完成后用 fs/get 校验服务器端文件大小，不一致则判定上传不完整
     */
    suspend fun uploadFile(localFile: File, targetDir: String): Result<Unit> {
        return try {
            val remotePath = if (targetDir == "/") "/${localFile.name}" else "$targetDir/${localFile.name}"
            val encodedPath = encodeFilePath(remotePath)
            val expected = localFile.length()

            // 方式一：File-Path 请求头（标准）
            val headerBody: RequestBody =
                localFile.asRequestBody("application/octet-stream".toMediaType())
            val headerResp = api.uploadFile(
                filePath = encodedPath,
                token = authHeader(),
                asTask = "false",
                body = headerBody
            )
            when {
                headerResp.code() == 401 -> return Result.failure(UnauthorizedException())
                headerResp.isSuccessful -> {
                    // 若返回异步任务（OpenList 强制任务队列），轮询直到完成
                    val taskId = headerResp.body()?.data?.task?.id
                    if (taskId != null) {
                        awaitUploadTask(taskId)?.let { return Result.failure(Exception(it)) }
                    }
                    // 大小校验兜底：防止任务"成功"但文件写入不完整
                    verifyUploadSize(remotePath, expected)?.let { return Result.failure(Exception(it)) }
                    return Result.success(Unit)
                }
            }
            // 方式二：清理可能残留的半截文件后回退 query 方式（旧版兼容）
            val dir = remotePath.substringBeforeLast('/', "/").ifEmpty { "/" }
            val name = remotePath.substringAfterLast('/')
            try {
                api.remove(RemoveRequest(dir, listOf(name)), authHeader())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                // 清理失败不阻塞，直接重传覆盖
            }
            val queryBody: RequestBody =
                localFile.asRequestBody("application/octet-stream".toMediaType())
            val queryResp = api.uploadFileWithQuery(
                path = encodedPath,
                token = authHeader(),
                asTask = "false",
                body = queryBody
            )
            when {
                queryResp.code() == 401 -> Result.failure(UnauthorizedException())
                queryResp.isSuccessful -> {
                    val taskId = queryResp.body()?.data?.task?.id
                    if (taskId != null) {
                        awaitUploadTask(taskId)?.let { return Result.failure(Exception(it)) }
                    }
                    verifyUploadSize(remotePath, expected)?.let { return Result.failure(Exception(it)) }
                    Result.success(Unit)
                }
                else -> Result.failure(
                    Exception("上传失败: 头方式 HTTP ${headerResp.code()} ${headerResp.message()}；query 方式 HTTP ${queryResp.code()} ${queryResp.message()}")
                )
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 轮询上传任务直到结束（AList/OpenList 任务状态：pending/running/succeeded/failed/canceled）。
     * 返回 null 表示成功；否则返回错误描述。
     */
    private suspend fun awaitUploadTask(tid: String): String? {
        repeat(180) { // 最多等 3 分钟
            try {
                val resp = api.taskInfo(tid, authHeader())
                if (resp.code() == 401) throw UnauthorizedException()
                val task = resp.body()?.data?.firstOrNull { it.id == tid }
                val state = task?.state
                when (state) {
                    "succeeded" -> return null
                    "failed", "canceled", "errored", "error" -> {
                        val detail = task?.error?.takeIf { it.isNotBlank() } ?: state
                        return "上传任务失败（$detail）"
                    }
                    else -> Unit // pending/running/null：继续等
                }
            } catch (e: CancellationException) { throw e } catch (e: UnauthorizedException) { throw e } catch (e: Exception) {
                // 轮询请求异常继续等待，不打断上传流程
            }
            delay(1000)
        }
        return "上传任务超时（3 分钟）"
    }

    /** 上传后校验服务器端文件大小，防止任务"成功"但文件不完整；返回 null 表示一致 */
    private suspend fun verifyUploadSize(path: String, expected: Long): String? = try {
        val resp = api.getFile(FileGetRequest(path), authHeader())
        when {
            resp.code() == 401 -> throw UnauthorizedException()
            else -> {
                val size = resp.body()?.data?.size
                if (size != null && size != expected) {
                    "上传不完整（服务器仅接收 $size / $expected 字节），请重试"
                } else {
                    null
                }
            }
        }
    } catch (e: CancellationException) { throw e } catch (e: UnauthorizedException) { throw e } catch (e: Exception) {
        null // 校验失败不阻塞主流程
    }

    suspend fun createDirectory(path: String): Result<Unit> = try {
        val response = api.mkdir(MkdirRequest(path), authHeader())
        when {
            response.code() == 401 -> Result.failure(UnauthorizedException())
            response.isSuccessful -> Result.success(Unit)
            else -> Result.failure(Exception("创建目录失败"))
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteFiles(dir: String, names: List<String>): Result<Unit> = try {
        val response = api.remove(RemoveRequest(dir, names), authHeader())
        when {
            response.code() == 401 -> Result.failure(UnauthorizedException())
            response.isSuccessful -> Result.success(Unit)
            else -> Result.failure(Exception("删除失败"))
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun rename(path: String, newName: String): Result<Unit> = try {
        val response = api.rename(RenameRequest(path, newName), authHeader())
        when {
            response.code() == 401 -> Result.failure(UnauthorizedException())
            response.isSuccessful -> Result.success(Unit)
            else -> Result.failure(Exception("重命名失败"))
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun search(keywords: String, path: String = "/"): Result<List<FileItem>> = try {
        val response = api.search(SearchRequest(parent = path, keywords = keywords), authHeader())
        when {
            response.code() == 401 -> Result.failure(UnauthorizedException())
            response.isSuccessful -> {
                val body = response.body()
                if (body?.code == 200) Result.success(body.data?.content ?: emptyList())
                else Result.failure(Exception(body?.message ?: "搜索失败"))
            }
            else -> Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(e)
    }
}
