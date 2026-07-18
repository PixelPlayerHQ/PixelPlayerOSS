package com.lostf1sh.pixelplayeross.data.navidrome

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.database.NavidromePendingFavoriteEntity
import org.junit.jupiter.api.Test

class NavidromeFavoritesReconciliationTest {

    private val toUnifiedSongId: (String) -> Long = { id -> -(9_000_000_000_000L + id.hashCode().toLong()) }

    private fun pendingOp(navidromeSongId: String, isStar: Boolean) =
        NavidromePendingFavoriteEntity(
            navidromeSongId = navidromeSongId,
            isStar = isStar,
            createdAt = 1L,
            attempts = 0
        )

    @Test
    fun `server starred song not favorited locally gets favorited`() {
        val result = reconcileNavidromeFavorites(
            serverStarredIds = setOf("nd-1"),
            pendingOps = emptyList(),
            localFavoriteIds = emptySet(),
            toUnifiedSongId = toUnifiedSongId
        )

        assertThat(result.toFavorite).containsExactly(toUnifiedSongId("nd-1"))
        assertThat(result.toUnfavorite).isEmpty()
    }

    @Test
    fun `local favorite absent from server with no pending op gets removed`() {
        val localId = toUnifiedSongId("nd-2")

        val result = reconcileNavidromeFavorites(
            serverStarredIds = emptySet(),
            pendingOps = emptyList(),
            localFavoriteIds = setOf(localId),
            toUnifiedSongId = toUnifiedSongId
        )

        assertThat(result.toFavorite).isEmpty()
        assertThat(result.toUnfavorite).containsExactly(localId)
    }

    @Test
    fun `pending star op protects local favorite not yet on server`() {
        val localId = toUnifiedSongId("nd-3")

        val result = reconcileNavidromeFavorites(
            serverStarredIds = emptySet(),
            pendingOps = listOf(pendingOp("nd-3", isStar = true)),
            localFavoriteIds = setOf(localId),
            toUnifiedSongId = toUnifiedSongId
        )

        assertThat(result.toFavorite).isEmpty()
        assertThat(result.toUnfavorite).isEmpty()
    }

    @Test
    fun `pending unstar op overrides server starred state`() {
        val localId = toUnifiedSongId("nd-4")

        val result = reconcileNavidromeFavorites(
            serverStarredIds = setOf("nd-4"),
            pendingOps = listOf(pendingOp("nd-4", isStar = false)),
            localFavoriteIds = setOf(localId),
            toUnifiedSongId = toUnifiedSongId
        )

        assertThat(result.toFavorite).isEmpty()
        assertThat(result.toUnfavorite).containsExactly(localId)
    }

    @Test
    fun `pending unstar op prevents import of server starred song`() {
        val result = reconcileNavidromeFavorites(
            serverStarredIds = setOf("nd-5"),
            pendingOps = listOf(pendingOp("nd-5", isStar = false)),
            localFavoriteIds = emptySet(),
            toUnifiedSongId = toUnifiedSongId
        )

        assertThat(result.toFavorite).isEmpty()
        assertThat(result.toUnfavorite).isEmpty()
    }

    @Test
    fun `already consistent state produces no changes`() {
        val localId = toUnifiedSongId("nd-6")

        val result = reconcileNavidromeFavorites(
            serverStarredIds = setOf("nd-6"),
            pendingOps = emptyList(),
            localFavoriteIds = setOf(localId),
            toUnifiedSongId = toUnifiedSongId
        )

        assertThat(result.toFavorite).isEmpty()
        assertThat(result.toUnfavorite).isEmpty()
    }
}
