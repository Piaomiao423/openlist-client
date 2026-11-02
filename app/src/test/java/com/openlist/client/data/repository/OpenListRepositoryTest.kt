package com.openlist.client.data.repository

import com.openlist.client.data.remote.OpenListApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.InetAddress
import java.nio.file.Files

class OpenListRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        // 显式绑定回环地址，避免本机 localhost 被 hosts 映射到 Docker/虚拟机域名
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        cacheDir = Files.createTempDirectory("openlist-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun serverUrl(): String = "http://127.0.0.1:${server.port}"

    private fun newRepository(): OpenListRepository {
        val base = serverUrl()
        val api = Retrofit.Builder()
            .baseUrl("$base/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenListApi::class.java)
        return OpenListRepository(base, "token-1", cacheDir, api, OkHttpClient())
    }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    // ===== 文件列表 =====

    @Test
    fun `listFiles parses content and sends auth header`() = runTest {
        server.enqueue(
            json(
                """
                {"code":200,"message":"success","data":{"content":[
                  {"name":"docs","size":0,"is_dir":true,"modified":"2026-10-01T00:00:00Z","type":0},
                  {"name":"a.txt","size":12,"is_dir":false,"modified":"2026-10-01T00:00:00Z","type":0}
                ]}}
                """.trimIndent()
            )
        )

        val result = newRepository().listFiles("/")

        assertEquals(listOf("docs", "a.txt"), result.getOrThrow().map { it.name })
        val request = server.takeRequest()
        assertEquals("token-1", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"path\":\"/\""))
    }

    @Test
    fun `listFiles maps 401 to UnauthorizedException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = newRepository().listFiles("/").exceptionOrNull()

        assertTrue(error is UnauthorizedException)
    }

    @Test
    fun `listFiles maps body code 401 to UnauthorizedException`() = runTest {
        server.enqueue(json("""{"code":401,"message":"token is expired","data":null}"""))

        val error = newRepository().listFiles("/").exceptionOrNull()

        assertTrue(error is UnauthorizedException)
    }

    @Test
    fun `listFiles reports transport status on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val error = newRepository().listFiles("/").exceptionOrNull()

        assertTrue(error?.message?.contains("HTTP 500") == true)
    }

    @Test
    fun `search sends keywords and parses hits`() = runTest {
        server.enqueue(
            json(
                """
                {"code":200,"data":{"content":[
                  {"name":"match.mp4","size":1,"is_dir":false,"modified":"","type":0}
                ]}}
                """.trimIndent()
            )
        )

        val result = newRepository().search("mp4", "/media")

        assertEquals("match.mp4", result.getOrThrow().single().name)
        val request = server.takeRequest()
        assertEquals("/api/fs/search", request.path?.substringBefore('?'))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"keywords\":\"mp4\""))
        assertTrue(body.contains("\"parent\":\"/media\""))
        assertTrue(body.contains("\"per_page\":200"))
    }

    // ===== 下载链接解析 =====

    @Test
    fun `download url prefers fs link result`() = runTest {
        server.enqueue(
            json("""{"code":200,"data":{"url":"/d/a.txt","header":"{\"User-Agent\":\"ua\"}"}}""")
        )
        server.enqueue(MockResponse().setResponseCode(404))

        val url = newRepository().getDownloadUrl("/docs/a.txt").getOrThrow()

        assertEquals("${serverUrl()}/d/a.txt", url)
    }

    @Test
    fun `download url falls back to fs get raw_url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(json("""{"code":200,"data":{"raw_url":"/d/raw.txt"}}"""))

        val url = newRepository().getDownloadUrl("/docs/raw.txt").getOrThrow()

        assertEquals("${serverUrl()}/d/raw.txt", url)
    }

    @Test
    fun `download info falls back to authenticated proxy path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val info = newRepository().getDownloadInfo("/中文 文件.txt").getOrThrow()

        assertEquals(
            "${serverUrl()}/p/%E4%B8%AD%E6%96%87%20%E6%96%87%E4%BB%B6.txt",
            info.url
        )
        assertTrue(info.requiresAuth)
    }

    @Test
    fun `download info propagates unauthorized as failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = newRepository().getDownloadInfo("/docs/a.txt").exceptionOrNull()

        assertTrue(error is UnauthorizedException)
    }

    // ===== 下载到缓存 =====

    @Test
    fun `downloadToCache stores file content`() = runTest {
        server.enqueue(json("""{"code":200,"data":{"url":"${serverUrl()}/d/a.txt"}}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("hello world"))

        val file = newRepository().downloadToCache("/d/a.txt").getOrThrow()

        assertEquals("hello world", file.readText())
        assertEquals("txt", file.extension)
        assertEquals("token-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `downloadToCache retries with proxy when direct link fails`() = runTest {
        server.enqueue(json("""{"code":200,"data":{"url":"${serverUrl()}/d/broken.txt"}}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("proxy-body"))

        val file = newRepository().downloadToCache("/d/broken.txt").getOrThrow()

        assertEquals("proxy-body", file.readText())
    }

    @Test
    fun `downloadToCache never sends token to a different host`() = runTest {
        server.enqueue(
            json("""{"code":200,"data":{"url":"http://localhost:${server.port}/d/a.txt"}}""")
        )
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("remote-body"))

        val file = newRepository().downloadToCache("/d/a.txt").getOrThrow()

        assertEquals("remote-body", file.readText())
        val link = server.takeRequest()
        server.takeRequest()
        val direct = server.takeRequest()
        assertEquals("token-1", link.getHeader("Authorization"))
        assertNull(direct.getHeader("Authorization"))
    }

    @Test
    fun `downloadToCache keeps provider headers from direct link`() = runTest {
        server.enqueue(
            json(
                """{"code":200,"data":{"url":"http://localhost:${server.port}/d/a.txt",
                  "header":"{\"Authorization\":\"provider-token\",\"Referer\":\"https://pan.example\"}"}}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("remote-body"))

        newRepository().downloadToCache("/d/a.txt").getOrThrow()

        server.takeRequest()
        server.takeRequest()
        val direct = server.takeRequest()
        assertEquals("provider-token", direct.getHeader("Authorization"))
        assertEquals("https://pan.example", direct.getHeader("Referer"))
    }

    // ===== 上传 =====

    private fun localUploadFile(): File =
        File(cacheDir, "hello.txt").apply { writeText("hello") }

    @Test
    fun `uploadFile sends encoded File-Path header and verifies size`() = runTest {
        server.enqueue(json("""{"code":200,"data":{"task":null}}"""))
        server.enqueue(json("""{"code":200,"data":{"size":5}}"""))

        val result = newRepository().uploadFile(localUploadFile(), "/docs")

        assertTrue(result.isSuccess)
        val put = server.takeRequest()
        assertEquals("/docs/hello.txt", put.getHeader("File-Path"))
        assertEquals("PUT", put.method)
    }

    @Test
    fun `uploadFile reports progress from zero to full size`() = runTest {
        server.enqueue(json("""{"code":200,"data":{"task":null}}"""))
        server.enqueue(json("""{"code":200,"data":{"size":5}}"""))
        val progress = mutableListOf<Pair<Long, Long>>()

        val result = newRepository().uploadFile(localUploadFile(), "/docs") { uploaded, total ->
            progress += uploaded to total
        }

        assertTrue(result.isSuccess)
        assertEquals(0L, progress.first().first)
        assertEquals(5L to 5L, progress.last())
    }

    @Test
    fun `uploadFile polls async task until succeeded`() = runTest {
        server.enqueue(json("""{"code":200,"data":{"task":{"id":"t1","state":"pending"}}}"""))
        server.enqueue(json("""{"code":200,"data":[{"id":"t1","state":"succeeded"}]}"""))
        server.enqueue(json("""{"code":200,"data":{"size":5}}"""))

        val result = newRepository().uploadFile(localUploadFile(), "/")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `uploadFile fails when server size differs`() = runTest {
        server.enqueue(json("""{"code":200,"data":{"task":null}}"""))
        server.enqueue(json("""{"code":200,"data":{"size":3}}"""))

        val error = newRepository().uploadFile(localUploadFile(), "/docs").exceptionOrNull()

        assertTrue(error?.message?.contains("上传不完整") == true)
    }

    @Test
    fun `uploadFile query fallback keeps path encoded once`() = runTest {
        val local = File(cacheDir, "中文 文件.txt").apply { writeText("hello") }
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(json("""{"code":200,"data":null}"""))
        server.enqueue(json("""{"code":200,"data":{"task":null}}"""))
        server.enqueue(json("""{"code":200,"data":{"size":5}}"""))

        val result = newRepository().uploadFile(local, "/docs")

        assertTrue(result.isSuccess)
        server.takeRequest()
        server.takeRequest()
        val queryPut = server.takeRequest()
        val encoded = "%E4%B8%AD%E6%96%87%20%E6%96%87%E4%BB%B6.txt"
        assertTrue(queryPut.path!!.contains(encoded))
        assertFalse(queryPut.path!!.contains("%25E4"))
    }

    // ===== 目录与删除 =====

    @Test
    fun `createDirectory succeeds on 200`() = runTest {
        server.enqueue(json("""{"code":200,"data":null}"""))

        assertTrue(newRepository().createDirectory("/new").isSuccess)
    }

    @Test
    fun `deleteFiles maps 401 to UnauthorizedException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = newRepository().deleteFiles("/", listOf("a.txt")).exceptionOrNull()

        assertTrue(error is UnauthorizedException)
    }

    @Test
    fun `deleteFiles sends every selected name in one request`() = runTest {
        server.enqueue(json("""{"code":200,"data":null}"""))

        val result = newRepository().deleteFiles("/docs", listOf("a.txt", "b.txt", "old"))

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/fs/remove", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"dir\":\"/docs\""))
        assertTrue(body.contains("\"a.txt\""))
        assertTrue(body.contains("\"b.txt\""))
        assertTrue(body.contains("\"old\""))
    }

    @Test
    fun `rename reports failure on error status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = newRepository().rename("/a.txt", "b.txt")

        assertFalse(result.isSuccess)
    }
}
