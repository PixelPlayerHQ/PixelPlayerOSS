package com.lostf1sh.pixelplayeross.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Normalizer
import java.util.Locale

class GenreParserTest {

    @Test
    fun `split separates semicolon, comma, pipe, backslash and NUL lists`() {
        assertEquals(listOf("Alternative", "Indie Rock"), GenreParser.split("Alternative; Indie Rock"))
        assertEquals(listOf("Rock", "Pop"), GenreParser.split("Rock,Pop"))
        assertEquals(listOf("House", "Electronic"), GenreParser.split("House | Electronic"))
        assertEquals(listOf("Rock", "Pop"), GenreParser.split("Rock\\Pop"))
        assertEquals(listOf("Rock", "Pop"), GenreParser.split("Rock\u0000Pop"))
    }

    @Test
    fun `split keeps slash and ampersand genres whole`() {
        assertEquals(listOf("Rap/Hip-Hop"), GenreParser.split("Rap/Hip-Hop"))
        assertEquals(listOf("R&B/Soul", "Drum & Bass"), GenreParser.split("R&B/Soul; Drum & Bass"))
    }

    @Test
    fun `split drops the dangling ampersand of a Discogs style list`() {
        assertEquals(listOf("Folk", "World", "Country"), GenreParser.split("Folk, World, & Country"))
    }

    @Test
    fun `split returns nothing for missing, blank and unknown genres`() {
        assertTrue(GenreParser.split(null).isEmpty())
        assertTrue(GenreParser.split("   ").isEmpty())
        assertTrue(GenreParser.split("<unknown>").isEmpty())
        assertTrue(GenreParser.split("Unknown").isEmpty())
        assertTrue(GenreParser.split(" ; , ").isEmpty())
        assertEquals(listOf("Rock"), GenreParser.split("Unknown; Rock"))
    }

    @Test
    fun `split returns nothing for values made of tabs, Unicode spaces and NUL`() {
        assertTrue(GenreParser.split(";\t|").isEmpty())
        assertTrue(GenreParser.split("　").isEmpty())
        assertTrue(GenreParser.split(" ; ").isEmpty())
        assertTrue(GenreParser.split("\u0000Unknown").isEmpty())
    }

    @Test
    fun `noGenreCharacters lists every character split drops on its own except NUL`() {
        // The Unknown query trims values with this list, so a character missing from it would
        // count a song under Unknown without listing it there.
        val dropped = (1..Char.MAX_VALUE.code)
            .map(Int::toChar)
            .filter { GenreParser.split(it.toString()).isEmpty() }
            .toSet()

        assertEquals(dropped, GenreParser.noGenreCharacters.toSet())
        assertTrue('\t' in dropped)
        assertTrue('　' in dropped)
        assertFalse('\u0000' in GenreParser.noGenreCharacters)
    }

    @Test
    fun `split removes case and spacing duplicates keeping the first spelling`() {
        assertEquals(listOf("Rock"), GenreParser.split("Rock; rock;ROCK"))
        assertEquals(listOf("Indie Rock"), GenreParser.split("Indie Rock, Indie   rock"))
    }

    @Test
    fun `key keeps the legacy genre ids`() {
        assertEquals("hip_hop", GenreParser.key("Hip Hop"))
        assertEquals("rap_hip-hop", GenreParser.key("Rap/Hip-Hop"))
        assertEquals("indie_rock", GenreParser.key("  Indie   Rock "))
    }

    @Test
    fun `key does not depend on the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("indie", GenreParser.key("INDIE"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `key treats composed and decomposed accents as the same genre`() {
        assertEquals(GenreParser.key("Électro"), GenreParser.key("Électro"))
    }

    @Test
    fun `matches finds a genre inside a multi-genre value only as a whole genre`() {
        assertTrue(GenreParser.matches("Alternative; Indie Rock", "alternative"))
        assertTrue(GenreParser.matches("Alternative; Indie Rock", "indie_rock"))
        assertFalse(GenreParser.matches("Alternative Rock", "Alternative"))
        assertFalse(GenreParser.matches(null, "Rock"))
    }

    @Test
    fun `joinValues keeps a single value as written and joins several`() {
        assertEquals("Rock, Pop", GenreParser.joinValues(listOf("Rock, Pop")))
        assertEquals("Electronic; House", GenreParser.joinValues(listOf(" Electronic ", "House", "house", "")))
        assertNull(GenreParser.joinValues(listOf(null, " ")))
        assertNull(GenreParser.joinValues(emptyList()))
    }

    @Test
    fun `dominantGenre counts every genre of every song`() {
        assertEquals("House", GenreParser.dominantGenre(listOf("House", "Deep House; House", null)))
        assertEquals("Indie Rock", GenreParser.dominantGenre(listOf("Alternative; Indie Rock", "Indie Rock")))
    }

    @Test
    fun `dominantGenre breaks ties by first appearance and ignores untagged songs`() {
        assertEquals("Jazz", GenreParser.dominantGenre(listOf("Jazz", "Soul")))
        assertNull(GenreParser.dominantGenre(listOf(null, "", "<unknown>")))
    }

    @Test
    fun `dominantGenre skips the placeholders cloud sync stores for songs without a genre`() {
        assertNull(GenreParser.dominantGenre(listOf("Jellyfin", "Jellyfin")))
        assertNull(GenreParser.dominantGenre(listOf("Navidrome")))
        assertEquals("Soul", GenreParser.dominantGenre(listOf("Jellyfin", "Jellyfin", "Soul")))
    }

    @Test
    fun `isSourcePlaceholder matches the cloud placeholders by key only`() {
        assertTrue(GenreParser.isSourcePlaceholder("Jellyfin"))
        assertTrue(GenreParser.isSourcePlaceholder(" navidrome "))
        assertTrue(GenreParser.isSourcePlaceholder("JELLYFIN"))
        assertFalse(GenreParser.isSourcePlaceholder("Jellyfin Jazz"))
        assertFalse(GenreParser.isSourcePlaceholder("Indie Rock"))
    }

    @Test
    fun `likePattern widens spacing, slashes and non ASCII letters to wildcards`() {
        assertEquals("%Indie%Pop%", GenreParser.likePattern("Indie Pop"))
        assertEquals("%Rap%Hip-Hop%", GenreParser.likePattern("Rap/Hip-Hop"))
        assertEquals("%lectro%", GenreParser.likePattern("Électro"))
        assertEquals("%", GenreParser.likePattern("流行"))
    }

    @Test
    fun `likePattern never carries LIKE wildcards or escapes from the name`() {
        assertEquals("%100%Pure%", GenreParser.likePattern("100% Pure"))
        assertEquals("%Lo%Fi%", GenreParser.likePattern("Lo_Fi"))
        assertEquals("%Jazz%Pop%", GenreParser.likePattern("Jazz\\Pop"))
    }

    @Test
    fun `likePattern leaves out k, since key also reads the Kelvin sign as k`() {
        assertEquals("%Roc%", GenreParser.likePattern("Rock"))
        assertEquals("%-Pop%", GenreParser.likePattern("K-Pop"))
    }

    @Test
    fun `likePattern leaves out an ASCII letter that a combining accent follows`() {
        // A precomposed "Électro" holds no plain 'E', so a decomposed spelling must not ask for one.
        assertEquals("%lectro%", GenreParser.likePattern("E\u0301lectro"))
        assertEquals("%lectro%", GenreParser.likePattern("\u00C9lectro"))
        assertEquals("%Caf%", GenreParser.likePattern("Cafe\u0301"))
    }

    @Test
    fun `likePattern matches every stored spelling that matches accepts`() {
        // Every character with another spelling under key: precomposed and decomposed accents,
        // NFC singletons such as the Kelvin sign, and case pairs such as "İ" and "i" + U+0307.
        val misses = mutableListOf<String>()
        for (codePoint in 128..Character.MAX_CODE_POINT) {
            if (codePoint in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code) continue
            val char = String(Character.toChars(codePoint))
            val spellings = listOf(
                char,
                Normalizer.normalize(char, Normalizer.Form.NFC),
                Normalizer.normalize(char, Normalizer.Form.NFD)
            ).flatMap { listOf(it, it.lowercase(Locale.ROOT), it.uppercase(Locale.ROOT)) }.distinct()
            if (spellings.size == 1) continue
            val names = spellings.map { "Ab${it}cd" }
            names.forEach { genre ->
                val pattern = GenreParser.likePattern(genre)
                names.filter { stored -> GenreParser.matches(stored, genre) && !sqliteLike(pattern, stored) }
                    .forEach { stored -> misses += "U+%04X: %s misses %s".format(codePoint, pattern, stored) }
            }
        }

        assertEquals(emptyList<String>(), misses.take(10))
    }

    /** SQLite's default LIKE: '%' matches any run of characters, and only ASCII letters ignore case. */
    private fun sqliteLike(pattern: String, value: String): Boolean {
        fun foldAscii(text: String) = String(CharArray(text.length) { index ->
            text[index].let { char -> if (char in 'A'..'Z') char.lowercaseChar() else char }
        })
        assertFalse('_' in pattern)
        val regex = foldAscii(pattern).split('%').joinToString(".*") { Regex.escape(it) }
        return Regex(regex, RegexOption.DOT_MATCHES_ALL).matches(foldAscii(value))
    }
}
