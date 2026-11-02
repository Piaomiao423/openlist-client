package com.openlist.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class FileTimeFormatTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `formats utc iso time`() {
        assertEquals("2026-10-01 00:00:00", FileTimeFormat.display("2026-10-01T00:00:00Z", utc))
    }

    @Test
    fun `converts utc time to requested local zone`() {
        assertEquals(
            "2026-10-01 08:00:00",
            FileTimeFormat.display("2026-10-01T00:00:00Z", ZoneId.of("Asia/Shanghai"))
        )
    }

    @Test
    fun `formats epoch seconds and milliseconds`() {
        val expected = "2023-11-14 22:13:20"
        assertEquals(expected, FileTimeFormat.display("1700000000", utc))
        assertEquals(expected, FileTimeFormat.display("1700000000000", utc))
    }

    @Test
    fun `blank and go zero time are unknown`() {
        assertNull(FileTimeFormat.display(null, utc))
        assertNull(FileTimeFormat.display("  ", utc))
        assertNull(FileTimeFormat.display("0001-01-01T00:00:00Z", utc))
    }

    @Test
    fun `unparseable value is returned unchanged`() {
        assertEquals("刚刚", FileTimeFormat.display("刚刚", utc))
    }
}
