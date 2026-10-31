package com.openlist.client.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * 把文件保存到系统"下载"目录：
 * - Android 10+（API 29+）：走 MediaStore.Downloads，无需任何权限
 * - Android 9 及以下：写公共下载目录（需 WRITE_EXTERNAL_STORAGE 权限，Manifest 已声明）
 */
object Downloader {

    fun saveToDownloads(context: Context, source: File, displayName: String): Uri? {
        val cleanName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("\u0000", "")
            .trim()
            .takeUnless { it == "." || it == ".." || it.isBlank() }
            ?: "download_${System.currentTimeMillis()}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, cleanName)
        } else {
            saveToLegacyDownloads(source, cleanName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, source: File, name: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, guessMime(name))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        } catch (e: Exception) {
            return null
        }
        return try {
            val out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("无法写入下载目录")
            out.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            // 失败时删除未完成的条目，避免下载目录留下空文件
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDownloads(source: File, name: String): Uri? {
        var target: File? = null
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            target = File(dir, name)
            source.copyTo(target, overwrite = true)
            Uri.fromFile(target)
        } catch (e: Exception) {
            target?.delete()
            null
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "pdf" -> "application/pdf"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
