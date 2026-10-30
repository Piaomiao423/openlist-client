package com.openlist.client.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlCodecTest {

    @Test
    fun `encodePath percent-encodes chinese and spaces`() {
        assertEquals(
            "/%E4%B8%AD%E6%96%87%20a.txt",
            UrlCodec.encodePath("/中文 a.txt")
        )
    }

    @Test
    fun `decode restores utf8 and keeps plus`() {
        assertEquals("中文 a+b", UrlCodec.decode("%E4%B8%AD%E6%96%87%20a+b"))
    }

    @Test
    fun `encode keeps unreserved characters`() {
        assertEquals(
            "aZ09-_.~",
            UrlCodec.encode("aZ09-_.~")
        )
    }

    @Test
    fun `encodeUrl encodes path but preserves query signature`() {
        assertEquals(
            "http://nas:5244/d/%E4%B8%AD%E6%96%87%20%E6%96%87%E4%BB%B6.txt?sign=abc%2Fdef&x=1",
            UrlCodec.encodeUrl("http://nas:5244/d/中文 文件.txt?sign=abc%2Fdef&x=1")
        )
    }

    @Test
    fun `encodeUrl leaves relative paths untouched`() {
        assertEquals("/relative/path", UrlCodec.encodeUrl("/relative/path"))
    }
}
