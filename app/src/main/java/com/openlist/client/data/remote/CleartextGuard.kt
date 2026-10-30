package com.openlist.client.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 明文 HTTP 访问策略：只允许私有网络/本机地址，公网地址必须走 HTTPS。
 *
 * Android 的 networkSecurityConfig 不支持按 CIDR 匹配私有网段，而服务器地址由用户在登录时
 * 填写，无法静态枚举，因此平台层保持明文可用，实际限制在这里统一执行。
 * 该策略同时被 API 客户端、下载客户端、Coil 图片加载和媒体流式预览复用。
 */
object CleartextGuard {

    private val PRIVATE_DOMAIN_SUFFIXES = listOf(
        ".local", ".lan", ".home", ".internal", ".localhost", ".home.arpa"
    )

    /** 校验任意 URL：HTTPS 或私有网络地址放行，公网 HTTP 拒绝 */
    fun isAllowed(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return true
        return parsed.scheme == "https" || isPrivateHost(parsed.host)
    }

    fun isPrivateHost(rawHost: String): Boolean {
        val host = rawHost.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (host.isEmpty()) return false
        if (host == "localhost" || host == "::1" || host == "0:0:0:0:0:0:0:1") return true
        if (host.contains(':')) return isPrivateIpv6(host)
        if (isPrivateIpv4(host)) return true
        if (PRIVATE_DOMAIN_SUFFIXES.any { host.endsWith(it) }) return true
        // 单标签主机名（如 http://nas:5244）通常是内网主机
        return !host.contains('.')
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 192 && b == 168) ||
            (a == 169 && b == 254) || (a == 172 && b in 16..31)
    }

    private fun isPrivateIpv6(host: String): Boolean =
        host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") ||
            host.startsWith("fd") || host.startsWith("::ffff:127.")

    class Interceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val url = chain.request().url
            if (url.scheme == "http" && !isPrivateHost(url.host)) {
                throw IOException("出于安全考虑，仅允许对私有网络地址使用 HTTP；访问公网服务请改用 HTTPS（${url.host}）")
            }
            return chain.proceed(chain.request())
        }
    }
}
