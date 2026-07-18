package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Outbox of favorite changes on Navidrome songs awaiting push to the server.
 * One row per song: a later toggle replaces the pending op (latest intent wins).
 */
@Entity(tableName = "navidrome_pending_favorites")
data class NavidromePendingFavoriteEntity(
    @PrimaryKey val navidromeSongId: String,
    val isStar: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)
