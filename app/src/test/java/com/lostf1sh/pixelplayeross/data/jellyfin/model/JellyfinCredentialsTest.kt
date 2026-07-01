package com.lostf1sh.pixelplayeross.data.jellyfin.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JellyfinCredentialsTest {

    private fun creds(url: String) = JellyfinCredentials(serverUrl = url, username = "u", password = "p")

    @Test
    fun `https remote server is accepted`() {
        assertNull(creds("https://jellyfin.example.com").connectionValidationError())
    }

    @Test
    fun `http on a public host is rejected`() {
        val error = creds("http://jellyfin.example.com").connectionValidationError()
        assertNotNull(error)
        assertTrue(error!!.contains("https"), "error should steer the user to https: $error")
    }

    @Test
    fun `http is allowed on local network addresses`() {
        // localhost, loopback, private RFC1918 ranges, Tailscale, and local DNS names may be HTTP-only.
        listOf(
            "http://localhost:8096",
            "http://127.0.0.1:8096",
            "http://192.168.1.50:8096",
            "http://10.0.0.5",
            "http://172.16.4.4",
            "http://100.64.12.34:8096",
            "http://musicbox:8096",
            "http://media.local",
            "http://jellyfin.tailnet.ts.net:8096",
        ).forEach { url ->
            assertNull(creds(url).connectionValidationError(), "expected $url to be allowed over http")
        }
    }

    @Test
    fun `embedded credentials in the url are rejected`() {
        val error = creds("https://user:secret@jellyfin.example.com").connectionValidationError()
        assertNotNull(error)
        assertTrue(error!!.contains("credentials"))
    }

    @Test
    fun `unparseable urls report a format error`() {
        assertEquals("Invalid server URL format", creds("not a url").connectionValidationError())
        assertEquals("Invalid server URL format", creds("").connectionValidationError())
    }

    @Test
    fun `isValid requires a server, username, and either password or token`() {
        assertFalse(JellyfinCredentials.empty().isValid)
        assertFalse(JellyfinCredentials(serverUrl = "", username = "u", password = "p").isValid)
        assertTrue(JellyfinCredentials(serverUrl = "s", username = "u", password = "p").isValid)
        // Token-based auth with no password is still valid.
        assertTrue(
            JellyfinCredentials(serverUrl = "s", username = "u", password = "", accessToken = "tok").isValid
        )
    }

    @Test
    fun `normalization prepends https for public hosts and trims trailing slash`() {
        assertEquals(
            "https://jellyfin.example.com",
            JellyfinCredentials(serverUrl = "jellyfin.example.com/", username = "u", password = "p").normalizedServerUrl
        )
    }

    @Test
    fun `normalization prepends http for local and vpn hosts`() {
        mapOf(
            "192.168.1.50:8096/" to "http://192.168.1.50:8096",
            "100.64.12.34:8096/" to "http://100.64.12.34:8096",
            "jellyfin.tailnet.ts.net:8096/" to "http://jellyfin.tailnet.ts.net:8096",
            "musicbox:8096/" to "http://musicbox:8096"
        ).forEach { (input, expected) ->
            assertEquals(expected, creds(input).normalizedServerUrl)
            assertNull(creds(input).connectionValidationError())
        }
    }
}
