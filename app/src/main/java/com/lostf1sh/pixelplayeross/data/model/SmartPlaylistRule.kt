package com.lostf1sh.pixelplayeross.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class SmartPlaylistRule(
    val storageKey: String,
    val title: String,
    val subtitle: String
) {
    TOP_PLAYED(
        storageKey = "top_played",
        title = "Top Played",
        subtitle = "Your most played tracks."
    ),
    RECENTLY_PLAYED(
        storageKey = "recently_played",
        title = "Recently Played",
        subtitle = "Songs you listened to most recently."
    ),
    FORGOTTEN_FAVORITES(
        storageKey = "forgotten_favorites",
        title = "Forgotten Favorites",
        subtitle = "Favorite tracks you haven't played in a while."
    ),
    NEW_GEMS(
        storageKey = "new_gems",
        title = "New Gems",
        subtitle = "Recently added tracks with low play counts."
    );

    companion object {
        fun fromStorageKey(key: String?): SmartPlaylistRule? {
            if (key.isNullOrBlank()) return null
            return entries.firstOrNull { it.storageKey == key }
        }
    }
}

const val SMART_PLAYLIST_SOURCE_LEGACY = "SMART"
const val SMART_PLAYLIST_SOURCE_PREFIX = "$SMART_PLAYLIST_SOURCE_LEGACY:"

fun SmartPlaylistRule.toPlaylistSource(): String = "$SMART_PLAYLIST_SOURCE_PREFIX$storageKey"

fun SmartPlaylistRule.Companion.fromPlaylistSource(source: String): SmartPlaylistRule? {
    if (!source.startsWith(SMART_PLAYLIST_SOURCE_PREFIX)) return null
    return fromStorageKey(source.removePrefix(SMART_PLAYLIST_SOURCE_PREFIX))
}

fun isSmartPlaylistSource(source: String): Boolean =
    source == SMART_PLAYLIST_SOURCE_LEGACY || source.startsWith(SMART_PLAYLIST_SOURCE_PREFIX)

val Playlist.isSmartPlaylist: Boolean
    get() = isSmartPlaylistSource(source)
