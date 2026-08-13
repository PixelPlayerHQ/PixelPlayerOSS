package com.lostf1sh.pixelplayeross.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.lostf1sh.pixelplayeross.R

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    @StringRes val titleRes: Int,
    val defaultSort: SortOption
) {
    SONGS("SONGS", R.string.library_tab_songs, SortOption.SongTitleAZ),
    ALBUMS("ALBUMS", R.string.library_tab_albums, SortOption.AlbumTitleAZ),
    ARTISTS("ARTIST", R.string.library_tab_artists, SortOption.ArtistNameAZ),
    PLAYLISTS("PLAYLISTS", R.string.library_tab_playlists, SortOption.PlaylistNameAZ),
    FOLDERS("FOLDERS", R.string.library_tab_folders, SortOption.FolderNameAZ),
    LIKED("LIKED", R.string.library_tab_liked, SortOption.LikedSongDateLiked);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: SONGS
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }