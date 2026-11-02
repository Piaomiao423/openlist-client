package com.openlist.client.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 把服务器返回的 ISO 时间或时间戳转换为本地可读时间 */
object FileTimeFormat {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun display(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        val value = raw?.trim().orEmpty()
        // Go 的零值时间表示服务端没有该字段
        if (value.isEmpty() || value.startsWith("0001-01-01")) return null
        val instant = parse(value) ?: return value
        return formatter.format(instant.atZone(zone))
    }

    private fun parse(value: String): Instant? {
        value.toLongOrNull()?.let { number ->
            if (number <= 0) return null
            return runCatching {
                if (number > 100_000_000_000L) Instant.ofEpochMilli(number)
                else Instant.ofEpochSecond(number)
            }.getOrNull()
        }
        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }
            .getOrNull()
    }
}
