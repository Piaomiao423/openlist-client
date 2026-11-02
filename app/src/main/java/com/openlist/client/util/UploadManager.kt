package com.openlist.client.util

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import com.openlist.client.data.repository.OpenListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class UploadStatus {
    QUEUED, PREPARING, UPLOADING, VERIFYING, SUCCESS, FAILED
}

data class UploadTask(
    val id: Long,
    val serverUrl: String,
    val fileName: String,
    val targetDir: String,
    val totalBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val status: UploadStatus = UploadStatus.QUEUED,
    val speedBytesPerSec: Long = 0L,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = status == UploadStatus.QUEUED || status == UploadStatus.PREPARING ||
            status == UploadStatus.UPLOADING || status == UploadStatus.VERIFYING

    val isFinished: Boolean
        get() = status == UploadStatus.SUCCESS || status == UploadStatus.FAILED

    /** 预计剩余秒数；速度或总大小未知时为 null */
    val etaSeconds: Long?
        get() {
            if (status != UploadStatus.UPLOADING || speedBytesPerSec <= 0 || totalBytes <= 0) return null
            val remaining = (totalBytes - uploadedBytes).coerceAtLeast(0)
            return (remaining + speedBytesPerSec - 1) / speedBytesPerSec
        }
}

/**
 * 应用级上传队列：选择多个文件后依次上传，任务状态在页面切换、进入预览时仍然保留。
 * 进度回调来自 OkHttp 工作线程，速度由后台定时采样计算。
 */
class UploadManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private val nextId = AtomicLong(1)
    private val queue = Channel<PendingUpload>(Channel.UNLIMITED)
    private var ticker: Job? = null
    private val tickerLock = Any()

    private data class PendingUpload(
        val taskId: Long,
        val uri: Uri,
        val repository: OpenListRepository,
        val targetDir: String
    )

    init {
        scope.launch {
            for (item in queue) {
                process(item)
            }
        }
    }

    fun enqueue(
        repository: OpenListRepository,
        serverUrl: String,
        uris: List<Uri>,
        targetDir: String
    ) {
        if (uris.isEmpty()) return
        scope.launch {
            cleanupOldCache()
            val created = uris.map { uri ->
                val (rawName, size) = queryOpenable(uri)
                UploadTask(
                    id = nextId.getAndIncrement(),
                    serverUrl = serverUrl,
                    fileName = sanitizeName(rawName),
                    targetDir = targetDir,
                    totalBytes = size
                )
            }
            _tasks.update { current ->
                // 限制历史任务数量，避免长时间使用后无限增长
                val merged = current + created
                if (merged.size <= MAX_TASKS) merged
                else merged.filter { it.isActive } + merged.filter { it.isFinished }.takeLast(MAX_TASKS)
            }
            created.forEachIndexed { index, task ->
                queue.send(PendingUpload(task.id, uris[index], repository, targetDir))
            }
            ensureTicker()
        }
    }

    /** 清除已完成/失败任务；传入 serverUrl 时只清理该服务器的任务 */
    fun clearFinished(serverUrl: String? = null) {
        _tasks.update { list ->
            list.filter { it.isActive || (serverUrl != null && it.serverUrl != serverUrl) }
        }
    }

    private suspend fun process(item: PendingUpload) {
        val task = _tasks.value.firstOrNull { it.id == item.taskId } ?: return
        updateTask(item.taskId) {
            it.copy(status = UploadStatus.PREPARING, error = null, uploadedBytes = 0, speedBytesPerSec = 0)
        }
        val workDir = File(File(appContext.cacheDir, UPLOAD_CACHE_DIR), item.taskId.toString())
        var cacheFile: File? = null
        try {
            workDir.mkdirs()
            val (_, declaredSize) = queryOpenable(item.uri)
            var lastCopyEmit = 0L
            cacheFile = copyToCache(item.uri, task.fileName, workDir) { copied ->
                val now = SystemClock.elapsedRealtime()
                if ((declaredSize > 0 && copied >= declaredSize) || now - lastCopyEmit >= 200) {
                    lastCopyEmit = now
                    updateTask(item.taskId) {
                        it.copy(uploadedBytes = copied, totalBytes = if (declaredSize > 0) declaredSize else 0L)
                    }
                }
            } ?: throw Exception("无法读取所选文件")

            val total = cacheFile.length()
            updateTask(item.taskId) {
                it.copy(
                    status = UploadStatus.UPLOADING,
                    totalBytes = total,
                    uploadedBytes = 0L,
                    speedBytesPerSec = 0L
                )
            }
            ensureTicker()

            var lastEmit = 0L
            val result = item.repository.uploadFile(cacheFile, item.targetDir) { uploaded, totalBytes ->
                val now = SystemClock.elapsedRealtime()
                // 回调非常频繁（每 64KB），限速刷新状态流
                if ((totalBytes > 0 && uploaded >= totalBytes) || now - lastEmit >= 150) {
                    lastEmit = now
                    updateTask(item.taskId) { current ->
                        current.copy(
                            uploadedBytes = uploaded,
                            totalBytes = totalBytes,
                            status = if (totalBytes > 0 && uploaded >= totalBytes) {
                                UploadStatus.VERIFYING
                            } else {
                                UploadStatus.UPLOADING
                            }
                        )
                    }
                }
            }
            result.fold(
                onSuccess = {
                    updateTask(item.taskId) {
                        it.copy(
                            status = UploadStatus.SUCCESS,
                            uploadedBytes = it.totalBytes,
                            speedBytesPerSec = 0L
                        )
                    }
                },
                onFailure = { e ->
                    updateTask(item.taskId) {
                        it.copy(
                            status = UploadStatus.FAILED,
                            speedBytesPerSec = 0L,
                            error = e.message ?: "上传失败"
                        )
                    }
                }
            )
        } catch (e: CancellationException) {
            updateTask(item.taskId) {
                it.copy(status = UploadStatus.FAILED, speedBytesPerSec = 0L, error = "上传已取消")
            }
            throw e
        } catch (e: Exception) {
            updateTask(item.taskId) {
                it.copy(status = UploadStatus.FAILED, speedBytesPerSec = 0L, error = e.message ?: "上传失败")
            }
        } finally {
            cacheFile?.delete()
            workDir.delete()
        }
    }

    private fun updateTask(id: Long, transform: (UploadTask) -> UploadTask) {
        _tasks.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    private fun ensureTicker() {
        synchronized(tickerLock) {
            if (ticker?.isActive == true) return
            ticker = scope.launch {
                tickLoop()
                synchronized(tickerLock) { ticker = null }
            }
        }
    }

    private suspend fun tickLoop() {
        var samples = emptyMap<Long, Pair<Long, Long>>()
        while (true) {
            delay(500)
            val active = _tasks.value.filter { it.status == UploadStatus.UPLOADING }
            val now = SystemClock.elapsedRealtime()
            if (active.isEmpty()) {
                _tasks.update { list ->
                    list.map { if (it.speedBytesPerSec != 0L) it.copy(speedBytesPerSec = 0L) else it }
                }
                break
            }
            _tasks.update { list ->
                list.map { task ->
                    if (task.status != UploadStatus.UPLOADING) {
                        task.copy(speedBytesPerSec = 0L)
                    } else {
                        val previous = samples[task.id]
                        if (previous == null) task else {
                            val elapsedSec = (now - previous.second).coerceAtLeast(1) / 1000.0
                            val instant = (task.uploadedBytes - previous.first).coerceAtLeast(0) / elapsedSec
                            val speed = if (task.speedBytesPerSec <= 0) {
                                instant
                            } else {
                                task.speedBytesPerSec * 0.6 + instant * 0.4
                            }
                            task.copy(speedBytesPerSec = speed.toLong())
                        }
                    }
                }
            }
            samples = _tasks.value
                .filter { it.status == UploadStatus.UPLOADING }
                .associate { it.id to (it.uploadedBytes to now) }
        }
    }

    private fun queryOpenable(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        try {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            // 某些 Provider 不支持查询，回退到默认名称
        }
        return name to size.coerceAtLeast(0)
    }

    private fun sanitizeName(raw: String?): String = raw
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.replace("\u0000", "")
        ?.trim()
        ?.takeUnless { it == "." || it == ".." || it.isBlank() }
        ?: "upload_${System.currentTimeMillis()}"

    private fun copyToCache(
        uri: Uri,
        name: String,
        workDir: File,
        onCopied: (Long) -> Unit
    ): File? {
        val target = File(workDir, name)
        return try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        onCopied(copied)
                    }
                }
            }
            target
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    private fun cleanupOldCache() {
        try {
            val dir = File(appContext.cacheDir, UPLOAD_CACHE_DIR)
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            dir.listFiles()?.forEach { entry ->
                if (entry.lastModified() < cutoff) entry.deleteRecursively()
            }
        } catch (e: Exception) {
            // 缓存清理失败不影响上传
        }
    }

    private companion object {
        const val UPLOAD_CACHE_DIR = "upload"
        const val MAX_TASKS = 100
    }
}
