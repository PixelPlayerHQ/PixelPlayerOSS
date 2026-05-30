package com.lostf1sh.pixelplayeross.data.backup.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentSanitizerTest {

    private val sanitizer = ContentSanitizer()

    @Test
    fun `sanitizeString trims whitespace`() {
        assertEquals("hello", sanitizer.sanitizeString("  hello  "))
    }

    @Test
    fun `sanitizeString truncates to max length`() {
        val long = "a".repeat(2000)
        val result = sanitizer.sanitizeString(long, maxLength = 100)
        assertEquals(100, result.length)
    }

    @Test
    fun `sanitizeString strips control characters but keeps newlines and tabs`() {
        val input = "Hello\tWorld\n\u0000\u0001\u0002Test"
        val result = sanitizer.sanitizeString(input)
        assertTrue(result.contains('\t'))
        assertTrue(result.contains('\n'))
        assertFalse(result.contains('\u0000'))
        assertFalse(result.contains('\u0001'))
    }

    @Test
    fun `sanitizeUrl accepts valid https URL`() {
        val url = "https://cdn.example.com/image.jpg"
        val result = sanitizer.sanitizeUrl(url)
        assertEquals(url, result)
    }

    @Test
    fun `sanitizeUrl accepts valid http URL`() {
        val url = "http://example.com/image.jpg"
        val result = sanitizer.sanitizeUrl(url)
        assertEquals(url, result)
    }

    @Test
    fun `sanitizeUrl returns empty for non-http protocol`() {
        val url = "ftp://files.example.com/image.jpg"
        val result = sanitizer.sanitizeUrl(url)
        assertEquals("", result)
    }

    @Test
    fun `sanitizeUrl truncates overly long URL to max length`() {
        val url = "https://example.com/" + "a".repeat(3000)
        val result = sanitizer.sanitizeUrl(url)
        // sanitizeUrl delegates to sanitizeString which truncates to maxLength (2000)
        assertEquals(2000, result.length)
        assertTrue(result.startsWith("https://"))
    }

    @Test
    fun `isValidModuleKey accepts known keys`() {
        assertTrue(sanitizer.isValidModuleKey("playlists"))
        assertTrue(sanitizer.isValidModuleKey("global_settings"))
        assertTrue(sanitizer.isValidModuleKey("favorites"))
        assertTrue(sanitizer.isValidModuleKey("quick_fill"))
        assertTrue(sanitizer.isValidModuleKey("artist_images"))
        assertTrue(sanitizer.isValidModuleKey("equalizer"))
    }

    @Test
    fun `isValidModuleKey rejects invalid keys`() {
        assertFalse(sanitizer.isValidModuleKey(""))
        assertFalse(sanitizer.isValidModuleKey("../path_traversal"))
        assertFalse(sanitizer.isValidModuleKey("UPPERCASE"))
        assertFalse(sanitizer.isValidModuleKey("has spaces"))
        assertFalse(sanitizer.isValidModuleKey("has-dashes"))
    }
}
