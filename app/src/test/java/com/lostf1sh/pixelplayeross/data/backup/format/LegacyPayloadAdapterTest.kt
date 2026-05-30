package com.lostf1sh.pixelplayeross.data.backup.format

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyPayloadAdapterTest {

    private val adapter = LegacyPayloadAdapter()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun `adapts v2 backup with separate playlists and globalSettings`() {
        val json = """
        {
            "formatVersion": 2,
            "exportedAtEpochMs": 1700000000000,
            "availableSections": ["playlists", "global_settings", "favorites"],
            "playlists": [
                {"key": "user_playlists_json_v1", "type": "string", "stringValue": "[]"}
            ],
            "globalSettings": [
                {"key": "app_theme", "type": "string", "stringValue": "dark"}
            ],
            "favorites": [
                {"songId": 123, "addedAt": 1700000000000}
            ]
        }
        """.trimIndent()

        val (manifest, modules) = adapter.adapt(json, gson)

        assertEquals(2, manifest.schemaVersion)
        assertEquals(1700000000000, manifest.createdAt)
        assertEquals(3, modules.size)
        assertTrue(modules.containsKey("playlists"))
        assertTrue(modules.containsKey("global_settings"))
        assertTrue(modules.containsKey("favorites"))
    }

    @Test
    fun `adapts v1 backup with combined preferences field`() {
        val json = """
        {
            "formatVersion": 1,
            "exportedAtEpochMs": 1600000000000,
            "availableSections": ["playlists", "global_settings"],
            "preferences": [
                {"key": "user_playlists_json_v1", "type": "string", "stringValue": "[]"},
                {"key": "app_theme", "type": "string", "stringValue": "dark"},
                {"key": "crossfade_duration", "type": "int", "intValue": 6000}
            ]
        }
        """.trimIndent()

        val (manifest, modules) = adapter.adapt(json, gson)

        assertEquals(1, manifest.schemaVersion)
        assertEquals(2, modules.size)
        assertTrue(modules.containsKey("playlists"))
        assertTrue(modules.containsKey("global_settings"))
    }

    @Test
    fun `skips modules not in availableSections`() {
        val json = """
        {
            "formatVersion": 2,
            "exportedAtEpochMs": 1700000000000,
            "availableSections": ["favorites"],
            "playlists": [
                {"key": "user_playlists_json_v1", "type": "string", "stringValue": "[]"}
            ],
            "favorites": [
                {"songId": 123}
            ]
        }
        """.trimIndent()

        val (_, modules) = adapter.adapt(json, gson)

        assertEquals(1, modules.size)
        assertTrue(modules.containsKey("favorites"))
    }

    @Test
    fun `generates checksums in manifest module info`() {
        val json = """
        {
            "formatVersion": 2,
            "exportedAtEpochMs": 1700000000000,
            "availableSections": ["favorites"],
            "favorites": [
                {"songId": 123}
            ]
        }
        """.trimIndent()

        val (manifest, _) = adapter.adapt(json, gson)

        val favoritesInfo = manifest.modules["favorites"]
        assertTrue(favoritesInfo != null)
        assertTrue(favoritesInfo!!.checksum.startsWith("sha256:"))
        assertTrue(favoritesInfo.entryCount > 0)
        assertTrue(favoritesInfo.sizeBytes > 0)
    }

    @Test
    fun `handles empty backup gracefully`() {
        val json = """
        {
            "formatVersion": 2,
            "exportedAtEpochMs": 1700000000000,
            "availableSections": []
        }
        """.trimIndent()

        val (manifest, modules) = adapter.adapt(json, gson)

        assertEquals(2, manifest.schemaVersion)
        assertTrue(modules.isEmpty())
    }
}
