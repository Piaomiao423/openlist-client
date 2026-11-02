package com.openlist.client.util

import java.util.Locale

/** 上传速度、剩余时间与文件大小的统一展示格式 */
object TransferFormat {

    fun bytes(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
        size < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024 * 1024))
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "—" else "${bytes(bytesPerSecond)}/s"

    /** 剩余时间；剩余为 0 返回「0 秒」，速度未知返回 null */
    fun eta(remainingBytes: Long, bytesPerSecond: Long): String? {
        if (remainingBytes <= 0) return "0 秒"
        if (bytesPerSecond <= 0) return null
        val seconds = (remainingBytes + bytesPerSecond - 1) / bytesPerSecond
        return duration(seconds)
    }

    fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds 秒"
        seconds < 3600 -> {
            val minutes = seconds / 60
            val rest = seconds % 60
            if (rest == 0L) "$minutes 分" else "$minutes 分 $rest 秒"
        }
        seconds < 86_400 -> {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            if (minutes == 0L) "$hours 小时" else "$hours 小时 $minutes 分"
        }
        else -> {
            val days = seconds / 86_400
            val hours = (seconds % 86_400) / 3600
            if (hours == 0L) "$days 天" else "$days 天 $hours 小时"
        }
    }
}
