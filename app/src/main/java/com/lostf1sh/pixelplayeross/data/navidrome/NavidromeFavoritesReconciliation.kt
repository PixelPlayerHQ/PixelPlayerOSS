package com.lostf1sh.pixelplayeross.data.navidrome

import com.lostf1sh.pixelplayeross.data.database.NavidromePendingFavoriteEntity

data class NavidromeFavoritesReconciliation(
    val toFavorite: List<Long>,
    val toUnfavorite: List<Long>
)

/**
 * Computes local favorites changes for Navidrome-sourced songs.
 * Server starred state wins, except songs with a pending un-pushed local op,
 * where the pending action wins.
 */
internal fun reconcileNavidromeFavorites(
    serverStarredIds: Set<String>,
    pendingOps: List<NavidromePendingFavoriteEntity>,
    localFavoriteIds: Set<Long>,
    toUnifiedSongId: (String) -> Long
): NavidromeFavoritesReconciliation {
    val desired = serverStarredIds.mapTo(mutableSetOf(), toUnifiedSongId)
    pendingOps.forEach { op ->
        val unifiedId = toUnifiedSongId(op.navidromeSongId)
        if (op.isStar) desired.add(unifiedId) else desired.remove(unifiedId)
    }

    return NavidromeFavoritesReconciliation(
        toFavorite = (desired - localFavoriteIds).toList(),
        toUnfavorite = (localFavoriteIds - desired).toList()
    )
}
