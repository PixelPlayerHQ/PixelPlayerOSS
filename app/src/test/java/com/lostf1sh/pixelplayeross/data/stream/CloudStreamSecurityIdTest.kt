package com.lostf1sh.pixelplayeross.data.stream

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the injection-resistant ID validators and the private-IP / SSRF guard that
 * gate cloud streaming. These back-stop the loopback proxy: a crafted song/item ID or
 * an upstream URL pointing at a private host must be rejected before any request fires.
 */
class CloudStreamSecurityIdTest {

    @Test
    fun `navidrome song ids allow url-safe characters within length`() {
        assertTrue(CloudStreamSecurity.validateNavidromeSongId("abc123_-XYZ"))
        assertTrue(CloudStreamSecurity.validateNavidromeSongId("a"))
        assertFalse(CloudStreamSecurity.validateNavidromeSongId(""))
        assertFalse(CloudStreamSecurity.validateNavidromeSongId("a".repeat(101)))
        assertFalse(CloudStreamSecurity.validateNavidromeSongId("has space"))
        assertFalse(CloudStreamSecurity.validateNavidromeSongId("path/traversal"))
        assertFalse(CloudStreamSecurity.validateNavidromeSongId("inject';--"))
    }

    @Test
    fun `jellyfin item ids are strictly alphanumeric`() {
        assertTrue(CloudStreamSecurity.validateJellyfinItemId("a1b2c3d4e5f6A7B8"))
        assertFalse(CloudStreamSecurity.validateJellyfinItemId(""))
        assertFalse(CloudStreamSecurity.validateJellyfinItemId("with-hyphen"))
        assertFalse(CloudStreamSecurity.validateJellyfinItemId("with_underscore"))
        assertFalse(CloudStreamSecurity.validateJellyfinItemId("a".repeat(101)))
    }

    @Test
    fun `private and loopback ipv4 literals are detected`() {
        listOf(
            "10.0.0.1",
            "192.168.1.1",
            "172.16.0.1",
            "172.31.255.255",
            "127.0.0.1",
            "169.254.1.1",
            "0.0.0.0",
            "100.64.0.1",
            "100.127.255.254",
        )
            .forEach { assertTrue(CloudStreamSecurity.isPrivateIpv4Literal(it), "$it should be private") }
    }

    @Test
    fun `public ipv4 literals and non-ip hosts are not flagged private`() {
        listOf("8.8.8.8", "1.2.3.4", "172.15.0.1", "172.32.0.1", "203.0.113.7", "100.63.255.255", "100.128.0.1")
            .forEach { assertFalse(CloudStreamSecurity.isPrivateIpv4Literal(it), "$it should be public") }
        // Not dotted-quad IPv4 at all.
        assertFalse(CloudStreamSecurity.isPrivateIpv4Literal("example.com"))
        assertFalse(CloudStreamSecurity.isPrivateIpv4Literal("256.1.1.1"))
        assertFalse(CloudStreamSecurity.isPrivateIpv4Literal("1.2.3"))
    }
}
