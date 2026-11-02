package com.openlist.client.util

import com.openlist.client.data.model.FileItem
import kotlinx.coroutines.CancellationException
import java.util.Locale

data class TreeResult(
    val text: String,
    val directories: Int,
    val files: Int,
    val totalBytes: Long,
    val truncated: Boolean,
    val failedDirectories: Int
)

/**
 * 递归列目录并生成类似 tree 命令的文本。
 * 根目录读取失败会返回 failure；子目录失败会写入提示并继续，避免整棵树丢失。
 */
class TreeExporter(
    private val lister: suspend (path: String) -> Result<List<FileItem>>,
    private val onProgress: (directories: Int, files: Int, currentPath: String) -> Unit =
        { _, _, _ -> },
    private val maxEntries: Int = 200_000,
    private val maxChars: Int = 8_000_000,
    private val maxDepth: Int = 64
) {

    private val output = StringBuilder()
    private val visited = HashSet<String>()
    private var directories = 0
    private var files = 0
    private var totalBytes = 0L
    private var failedDirectories = 0
    private var entries = 0
    private var truncated = false

    suspend fun export(rootPath: String): Result<TreeResult> = try {
        output.append(if (rootPath == "/") "/" else rootPath).append('\n')
        walk(rootPath, "", 0)
        if (truncated) {
            output.append("\n[内容过多，结果已截断]\n")
        }
        output.append('\n')
        output.append("$directories 个文件夹, $files 个文件")
        if (totalBytes > 0) output.append(", 总大小 ${TransferFormat.bytes(totalBytes)}")
        if (failedDirectories > 0) output.append(", $failedDirectories 个目录读取失败")
        output.append('\n')
        Result.success(
            TreeResult(
                text = output.toString(),
                directories = directories,
                files = files,
                totalBytes = totalBytes,
                truncated = truncated,
                failedDirectories = failedDirectories
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun walk(path: String, prefix: String, depth: Int) {
        if (truncated) return
        if (depth >= maxDepth) {
            truncated = true
            return
        }
        if (!visited.add(path)) {
            output.append(prefix).append("└── [已跳过重复目录]\n")
            return
        }
        val result = lister(path)
        val items = result.fold(
            onSuccess = { list ->
                list.filter { it.name.isNotBlank() }
                    .sortedWith(
                        compareByDescending<FileItem> { it.isDir }
                            .thenBy { it.name.lowercase(Locale.ROOT) }
                    )
            },
            onFailure = { e ->
                if (depth == 0) throw (e as? Exception ?: Exception("读取目录失败"))
                failedDirectories++
                output.append(prefix).append("└── [无法读取：").append(clean(e.message ?: "未知错误")).append("]\n")
                return
            }
        )
        onProgress(directories, files, path)

        for ((index, item) in items.withIndex()) {
            if (truncated) return
            if (entries >= maxEntries || output.length >= maxChars) {
                truncated = true
                return
            }
            entries++
            val isLast = index == items.lastIndex
            val connector = if (isLast) "└── " else "├── "
            val childPrefix = prefix + if (isLast) "    " else "│   "
            if (item.isDir) {
                directories++
                output.append(prefix).append(connector).append(clean(item.name)).append("/\n")
                if (depth + 1 >= maxDepth) {
                    output.append(childPrefix).append("└── [达到最大深度]\n")
                    truncated = true
                } else {
                    walk(childPath(path, item.name), childPrefix, depth + 1)
                }
            } else {
                files++
                totalBytes += item.size
                output.append(prefix).append(connector)
                    .append(clean(item.name))
                    .append(" (").append(TransferFormat.bytes(item.size)).append(")\n")
            }
        }
    }

    private fun childPath(parent: String, name: String): String =
        if (parent == "/") "/$name" else "$parent/$name"

    private fun clean(name: String): String =
        name.replace('\n', ' ').replace('\r', ' ')
}
