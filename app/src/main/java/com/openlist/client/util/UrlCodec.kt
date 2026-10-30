package com.openlist.client.util

import java.io.ByteArrayOutputStream

/**
 * 纯 Kotlin 的 URL 编解码工具（不依赖 android.net.Uri，便于 JVM 单元测试）。
 * 编码规则与 android.net.Uri.encode / Uri.decode 保持一致。
 */
object UrlCodec {

    private const val HEX = "0123456789ABCDEF"
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    /** 与 Uri.encode(s) 的默认白名单一致 */
    private const val PATH_ALLOWED = "-_.~!*'()"

    /** 直链 query 保留签名所需字符，已编码的 %XX 不被重复编码 */
    private const val QUERY_ALLOWED = "=&?%+:/,-._~!*'()"

    private val URL_PATTERN =
        Regex("^([A-Za-z][A-Za-z0-9+.\\-]*)://([^/?#]*)([^?#]*)(?:\\?([^#]*))?(?:#.*)?$")

    fun encode(value: String, allowed: String = ""): String {
        val out = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            if (ch in UNRESERVED || allowed.indexOf(ch) >= 0) {
                out.append(ch)
            } else {
                out.append('%')
                out.append(HEX[code shr 4])
                out.append(HEX[code and 0x0F])
            }
        }
        return out.toString()
    }

    fun decode(value: String): String {
        if (value.indexOf('%') < 0) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '%' && i + 2 < value.length) {
                val high = value[i + 1].digitToIntOrNull(16)
                val low = value[i + 2].digitToIntOrNull(16)
                if (high != null && low != null) {
                    out.write((high shl 4) or low)
                    i += 3
                    continue
                }
            }
            val bytes = ch.toString().toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** 按 / 分段 decode→encode，保留分隔符；用于文件路径和 URL path */
    fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment ->
            if (segment.isEmpty()) "" else encode(decode(segment), PATH_ALLOWED)
        }

    /**
     * URL 容错编码：OpenList 直链可能含中文/空格，OkHttp 会拒绝。
     * 对 path 分段 decode→encode，query 保留签名所需字符。
     */
    fun encodeUrl(url: String): String {
        val match = URL_PATTERN.matchEntire(url) ?: return url
        val scheme = match.groupValues[1]
        val authority = match.groupValues[2]
        val path = match.groupValues[3]
        val encodedPath = encodePath(path)
        val encodedQuery = if (match.groups[4] != null) {
            "?" + encode(match.groupValues[4], QUERY_ALLOWED)
        } else {
            ""
        }
        return "$scheme://$authority$encodedPath$encodedQuery"
    }
}
