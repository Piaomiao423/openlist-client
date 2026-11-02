package com.openlist.client.util

import com.openlist.client.data.model.FileItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeExporterTest {

    private fun file(name: String, size: Long = 0) =
        FileItem(
            name, size, isDir = false, modified = null, created = null,
            sign = null, thumb = null, type = 0, hashInfo = null
        )

    private fun dir(name: String) =
        FileItem(
            name, 0, isDir = true, modified = null, created = null,
            sign = null, thumb = null, type = 0, hashInfo = null
        )

    @Test
    fun `exports tree command style text with totals`() = runTest {
        val entries = mapOf(
            "/" to listOf(file("b.txt", 2048), dir("media"), file("a.txt", 10)),
            "/media" to listOf(dir("photos"), file("movie.mp4", 1024L * 1024)),
            "/media/photos" to listOf(file("p.jpg", 1024))
        )
        val result = TreeExporter(
            lister = { path -> Result.success(entries[path] ?: emptyList()) }
        ).export("/").getOrThrow()

        val expected = """
            /
            ├── media/
            │   ├── photos/
            │   │   └── p.jpg (1.0 KB)
            │   └── movie.mp4 (1.0 MB)
            ├── a.txt (10 B)
            └── b.txt (2.0 KB)

            2 个文件夹, 4 个文件, 总大小 1.0 MB
        """.trimIndent()
        assertEquals(expected, result.text.trim())
        assertEquals(2, result.directories)
        assertEquals(4, result.files)
        assertEquals(2048L + 10 + 1024 * 1024 + 1024, result.totalBytes)
    }

    @Test
    fun `keeps going when a subdirectory cannot be read`() = runTest {
        val entries = mapOf(
            "/" to listOf(dir("ok"), dir("bad")),
            "/ok" to listOf(file("x.txt", 5))
        )
        val result = TreeExporter(
            lister = { path ->
                if (path == "/bad") Result.failure(Exception("拒绝访问"))
                else Result.success(entries[path] ?: emptyList())
            }
        ).export("/").getOrThrow()

        assertTrue(result.text.contains("[无法读取：拒绝访问]"))
        assertEquals(1, result.failedDirectories)
        assertEquals(1, result.files)
        assertTrue(result.text.contains("1 个目录读取失败"))
    }

    @Test
    fun `root failure fails the whole export`() = runTest {
        val result = TreeExporter(
            lister = { Result.failure(Exception("网络错误")) }
        ).export("/docs")

        assertTrue(result.isFailure)
        assertEquals("网络错误", result.exceptionOrNull()?.message)
    }

    @Test
    fun `stops appending entries after the limit`() = runTest {
        val entries = (1..10).map { file("f$it.txt", 1) }
        val result = TreeExporter(
            lister = { Result.success(entries) },
            maxEntries = 3
        ).export("/").getOrThrow()

        assertEquals(3, result.files)
        assertTrue(result.truncated)
        assertTrue(result.text.contains("结果已截断"))
    }

    @Test
    fun `never recurses past max depth`() = runTest {
        val entries = mapOf(
            "/" to listOf(dir("a")),
            "/a" to listOf(dir("b")),
            "/a/b" to listOf(file("deep.txt", 1))
        )
        val result = TreeExporter(
            lister = { path -> Result.success(entries[path] ?: emptyList()) },
            maxDepth = 2
        ).export("/").getOrThrow()

        assertTrue(result.truncated)
        assertTrue(result.text.contains("[达到最大深度]"))
        assertEquals(0, result.files)
    }

    @Test
    fun `reports every visited directory`() = runTest {
        val visited = mutableListOf<String>()
        val entries = mapOf(
            "/" to listOf(dir("a")),
            "/a" to emptyList<FileItem>()
        )
        TreeExporter(
            lister = { path -> Result.success(entries[path] ?: emptyList()) },
            onProgress = { _, _, path -> visited += path }
        ).export("/").getOrThrow()

        assertEquals(listOf("/", "/a"), visited)
    }
}
