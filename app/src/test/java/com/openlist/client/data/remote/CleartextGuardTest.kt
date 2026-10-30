package com.openlist.client.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextGuardTest {

    @Test
    fun `private ipv4 ranges are allowed`() {
        assertTrue(CleartextGuard.isPrivateHost("192.168.1.100"))
        assertTrue(CleartextGuard.isPrivateHost("10.0.0.2"))
        assertTrue(CleartextGuard.isPrivateHost("172.16.0.1"))
        assertTrue(CleartextGuard.isPrivateHost("172.31.255.255"))
        assertTrue(CleartextGuard.isPrivateHost("169.254.1.1"))
    }

    @Test
    fun `public ipv4 ranges are rejected`() {
        assertFalse(CleartextGuard.isPrivateHost("8.8.8.8"))
        assertFalse(CleartextGuard.isPrivateHost("172.32.0.1"))
        assertFalse(CleartextGuard.isPrivateHost("1.1.1.1"))
    }

    @Test
    fun `loopback and local hostnames are allowed`() {
        assertTrue(CleartextGuard.isPrivateHost("localhost"))
        assertTrue(CleartextGuard.isPrivateHost("127.0.0.1"))
        assertTrue(CleartextGuard.isPrivateHost("::1"))
        assertTrue(CleartextGuard.isPrivateHost("nas.local"))
        assertTrue(CleartextGuard.isPrivateHost("openlist"))
    }

    @Test
    fun `public domain is rejected`() {
        assertFalse(CleartextGuard.isPrivateHost("example.com"))
        assertFalse(CleartextGuard.isPrivateHost("drive.google.com"))
    }

    @Test
    fun `url policy prefers https and allows private http`() {
        assertTrue(CleartextGuard.isAllowed("https://example.com/file"))
        assertFalse(CleartextGuard.isAllowed("http://example.com/file"))
        assertTrue(CleartextGuard.isAllowed("http://192.168.1.100:5244/api"))
        assertTrue(CleartextGuard.isAllowed("http://nas:5244/api"))
    }
}
