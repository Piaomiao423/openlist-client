package com.openlist.client.data.remote

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProgressRequestBodyTest {

    @Test
    fun `writes whole file and reports monotonic progress`() {
        val file = Files.createTempFile("progress", ".bin").toFile()
        try {
            val bytes = ByteArray(200_000) { (it % 251).toByte() }
            file.writeBytes(bytes)
            val events = mutableListOf<Pair<Long, Long>>()
            val body = ProgressRequestBody(file) { uploaded, total ->
                events += uploaded to total
            }
            val sink = Buffer()

            body.writeTo(sink)

            assertEquals(bytes.size.toLong(), sink.size)
            assertEquals(0L to bytes.size.toLong(), events.first())
            assertEquals(bytes.size.toLong() to bytes.size.toLong(), events.last())
            assertTrue(events.size > 2)
            assertTrue(events.zipWithNext().all { (a, b) -> b.first >= a.first })
            assertEquals(bytes.size.toLong(), body.contentLength())
            assertEquals("application/octet-stream", body.contentType().toString())
        } finally {
            file.delete()
        }
    }
}
