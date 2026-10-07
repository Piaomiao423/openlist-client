package com.openlist.client.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把文件保存到系统"下载"目录：
 * - Android 10+（API 29+）：走 MediaStore.Downloads，无需任何权限
 * - Android 9 及以下：写公共下载目录（需 WRITE_EXTERNAL_STORAGE 权限，Manifest 已声明）
 */
object Downloader {

    fun saveToDownloads(context: Context, source: File, displayName: String): Uri? {
        val cleanName = displayName.substringAfterLast('/').ifBlank { "download_${System.currentTimeMillis()}" }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                    put(MediaStore.Downloads.MIME_TYPE, guessMime(cleanName))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, cleanName)
                source.copyTo(target, overwrite = true)
                Uri.fromFile(target)
            }
        } catch (e: Exception) {
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
