package com.openlist.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferFormatTest {

    @Test
    fun `formats byte sizes with binary units`() {
        assertEquals("0 B", TransferFormat.bytes(0))
        assertEquals("1023 B", TransferFormat.bytes(1023))
        assertEquals("1.0 KB", TransferFormat.bytes(1024))
        assertEquals("1.5 KB", TransferFormat.bytes(1536))
        assertEquals("1.0 MB", TransferFormat.bytes(1024L * 1024))
        assertEquals("1.50 GB", TransferFormat.bytes(3L * 1024 * 1024 * 1024 / 2))
    }

    @Test
    fun `formats speed and handles unknown values`() {
        assertEquals("—", TransferFormat.speed(0))
        assertEquals("—", TransferFormat.speed(-5))
        assertEquals("1.5 KB/s", TransferFormat.speed(1536))
        assertEquals("2.0 MB/s", TransferFormat.speed(2L * 1024 * 1024))
    }

    @Test
    fun `computes eta and rounds up to the next second`() {
        assertEquals("0 秒", TransferFormat.eta(0, 10))
        assertEquals("10 秒", TransferFormat.eta(100, 10))
        assertEquals("11 秒", TransferFormat.eta(101, 10))
        assertNull(TransferFormat.eta(100, 0))
    }

    @Test
    fun `formats durations for minutes hours and days`() {
        assertEquals("59 秒", TransferFormat.duration(59))
        assertEquals("1 分", TransferFormat.duration(60))
        assertEquals("1 分 5 秒", TransferFormat.duration(65))
        assertEquals("1 小时", TransferFormat.duration(3600))
        assertEquals("1 小时 1 分", TransferFormat.duration(3660))
        assertEquals("1 天 1 小时", TransferFormat.duration(90_000))
    }
}
