package com.lostf1sh.pixelplayeross.data.jellyfin

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinLibrary
import com.lostf1sh.pixelplayeross.data.network.jellyfin.JellyfinResponseParser
import org.json.JSONObject
import org.junit.jupiter.api.Test

class JellyfinRepositoryTest {

    @Test
    fun `selected libraries defaults to all available libraries when saved selection is empty`() {
        val libraries = listOf(
            JellyfinLibrary(id = "lossless", name = "Lossless", collectionType = "music"),
            JellyfinLibrary(id = "lossy", name = "Lossy", collectionType = "music")
        )

        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = libraries,
            savedLibraryIds = emptySet()
        )

        assertThat(selectedIds).containsExactly("lossless", "lossy")
    }

    @Test
    fun `selected libraries keeps valid saved subset`() {
        val libraries = listOf(
            JellyfinLibrary(id = "lossless", name = "Lossless", collectionType = "music"),
            JellyfinLibrary(id = "lossy", name = "Lossy", collectionType = "music")
        )

        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = libraries,
            savedLibraryIds = setOf("lossless")
        )

        assertThat(selectedIds).containsExactly("lossless")
    }

    @Test
    fun `selected libraries falls back to all when saved ids are stale`() {
        val libraries = listOf(
            JellyfinLibrary(id = "lossless", name = "Lossless", collectionType = "music"),
            JellyfinLibrary(id = "lossy", name = "Lossy", collectionType = "music")
        )

        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = libraries,
            savedLibraryIds = setOf("deleted-library")
        )

        assertThat(selectedIds).containsExactly("lossless", "lossy")
    }

    @Test
    fun `selected libraries returns empty when server exposes no libraries`() {
        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = emptyList(),
            savedLibraryIds = setOf("lossless")
        )

        assertThat(selectedIds).isEmpty()
    }

    @Test
    fun `only music collection type counts as music library`() {
        val music = JellyfinLibrary(id = "1", name = "Music", collectionType = "music")
        val movies = JellyfinLibrary(id = "2", name = "Movies", collectionType = "movies")
        val mixed = JellyfinLibrary(id = "3", name = "Mixed", collectionType = null)

        assertThat(music.isMusic).isTrue()
        assertThat(movies.isMusic).isFalse()
        assertThat(mixed.isMusic).isFalse()
    }

    @Test
    fun `parseLibraries maps views response items`() {
        val items = listOf(
            JSONObject(
                """{"Id": "abc", "Name": "Music", "CollectionType": "music"}"""
            ),
            JSONObject(
                """{"Id": "def", "Name": "Movies", "CollectionType": "movies"}"""
            ),
            JSONObject(
                """{"Id": "ghi", "Name": "Mixed"}"""
            )
        )

        val libraries = JellyfinResponseParser.parseLibraries(items)

        assertThat(libraries).containsExactly(
            JellyfinLibrary(id = "abc", name = "Music", collectionType = "music"),
            JellyfinLibrary(id = "def", name = "Movies", collectionType = "movies"),
            JellyfinLibrary(id = "ghi", name = "Mixed", collectionType = null)
        ).inOrder()
    }

    @Test
    fun `full sync is due when none has finished yet`() {
        assertThat(isJellyfinFullSyncDue(lastFullSyncMs = 0L, nowMs = 1_761_955_200_000L)).isTrue()
    }

    @Test
    fun `full sync is not due again within a day`() {
        val lastSync = 10 * JELLYFIN_SYNC_THRESHOLD_MS
        val oneHourLater = lastSync + 60 * 60 * 1000L

        assertThat(isJellyfinFullSyncDue(lastFullSyncMs = lastSync, nowMs = oneHourLater)).isFalse()
    }

    @Test
    fun `full sync is due once a day has passed`() {
        val lastSync = 10 * JELLYFIN_SYNC_THRESHOLD_MS
        val nextDay = lastSync + JELLYFIN_SYNC_THRESHOLD_MS + 1

        assertThat(isJellyfinFullSyncDue(lastFullSyncMs = lastSync, nowMs = nextDay)).isTrue()
    }
}
