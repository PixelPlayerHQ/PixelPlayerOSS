package com.lostf1sh.pixelplayeross.data.navidrome

import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavidromeSongUriTest {

    @Test
    fun `plain ids keep the legacy uri form`() {
        assertEquals("navidrome://song-1_A", NavidromeSongUri.build("song-1_A"))
        assertEquals("song-1_A", NavidromeSongUri.parseId("navidrome://song-1_A"))
    }

    @Test
    fun `aurral style ids round trip and are playable`() {
        listOf("song:abc%20def", "song:a/b?c#d", "a+b c", "song:Björk & Co.")
            .forEach { id ->
                val uri = NavidromeSongUri.build(id)
                assertTrue(uri.removePrefix("navidrome://").matches(Regex("[A-Za-z0-9%._*-]+")), uri)
                assertEquals(id, NavidromeSongUri.parseId(uri))
                assertTrue(CloudStreamSecurity.validateNavidromeSongId(id), id)
            }
        assertEquals("navidrome://song%3Aabc%2520def", NavidromeSongUri.build("song:abc%20def"))
    }

    @Test
    fun `non navidrome or empty uris are rejected`() {
        assertNull(NavidromeSongUri.parseId("jellyfin://abc"))
        assertNull(NavidromeSongUri.parseId("navidrome://"))
        assertNull(NavidromeSongUri.parseId(null))
    }
}
