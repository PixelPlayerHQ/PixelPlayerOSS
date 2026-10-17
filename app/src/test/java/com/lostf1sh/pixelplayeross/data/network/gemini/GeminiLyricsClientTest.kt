package com.lostf1sh.pixelplayeross.data.network.gemini

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GeminiLyricsClientTest {

    private lateinit var client: GeminiLyricsClient
    private val gson = Gson()

    @Before
    fun setUp() {
        client = GeminiLyricsClient(OkHttpClient(), gson)
    }

    @Test
    fun parseStructuredLyricsJson_withStandardJson_parsesCorrectly() {
        val json = """
            [
                {
                    "original": "こんにちは",
                    "romanized": "Konnichiwa",
                    "translated": "Hello"
                },
                {
                    "original": "さようなら",
                    "romanized": "Sayounara",
                    "translated": "Goodbye"
                }
            ]
        """.trimIndent()

        val items = client.parseStructuredLyricsJson(json)
        assertNotNull(items)
        assertEquals(2, items?.size)
        assertEquals("こんにちは", items?.get(0)?.original)
        assertEquals("Konnichiwa", items?.get(0)?.romanized)
        assertEquals("Hello", items?.get(0)?.translated)
        assertEquals("さようなら", items?.get(1)?.original)
        assertEquals("Sayounara", items?.get(1)?.romanized)
        assertEquals("Goodbye", items?.get(1)?.translated)
    }

    @Test
    fun parseStructuredLyricsJson_withMarkdownFences_stripsFencesAndParses() {
        val markdownJson = """
            ```json
            [
                {
                    "original": "你好",
                    "romanized": "Nǐ hǎo",
                    "translated": "Hello"
                }
            ]
            ```
        """.trimIndent()

        val items = client.parseStructuredLyricsJson(markdownJson)
        assertNotNull(items)
        assertEquals(1, items?.size)
        assertEquals("你好", items?.first()?.original)
        assertEquals("Nǐ hǎo", items?.first()?.romanized)
        assertEquals("Hello", items?.first()?.translated)
    }

    @Test
    fun parseGeminiResponse_withFullApiResponse_extractsItemsSuccessfully() {
        val apiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[\n  {\n    \"original\": \"Привет\",\n    \"romanized\": \"Privet\",\n    \"translated\": \"Hello\"\n  }\n]"
                      }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()

        val items = client.parseGeminiResponse(apiResponse)
        assertNotNull(items)
        assertEquals(1, items?.size)
        assertEquals("Привет", items?.first()?.original)
        assertEquals("Privet", items?.first()?.romanized)
        assertEquals("Hello", items?.first()?.translated)
    }

    @Test
    fun parseStructuredLyricsJson_withMalformedJson_returnsNullWithoutCrashing() {
        val malformed = "{ this is not valid json }"
        val items = client.parseStructuredLyricsJson(malformed)
        assertNull(items)
    }

    @Test
    fun parseGeminiResponse_withEmptyCandidates_returnsNull() {
        val emptyCandidates = """{ "candidates": [] }"""
        val items = client.parseGeminiResponse(emptyCandidates)
        assertNull(items)
    }
}
