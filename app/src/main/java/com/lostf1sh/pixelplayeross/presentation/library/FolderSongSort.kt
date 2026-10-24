package com.lostf1sh.pixelplayeross.presentation.library

import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.Song

/**
 * Orders the songs listed inside a folder by one of [SortOption.FOLDER_SONGS].
 *
 * Only the primary key follows the option's direction; equal keys always fall back to the same
 * ascending order, so a direction flip never shuffles ties. Album based sorts keep every album in
 * disc and track order. Any option outside [SortOption.FOLDER_SONGS] sorts by title (A-Z).
 */
internal fun sortFolderSongs(songs: List<Song>, option: SortOption): List<Song> {
    if (songs.size < 2) return songs
    return songs.sortedWith(folderSongComparator(option))
}

private fun folderSongComparator(option: SortOption): Comparator<Song> = when (option) {
    SortOption.FolderSongTitleZA -> titleDescending
    SortOption.FolderSongArtist -> artistAscending
    SortOption.FolderSongArtistDesc -> artistDescending
    SortOption.FolderSongAlbum -> albumAscending
    SortOption.FolderSongAlbumDesc -> albumDescending
    SortOption.FolderSongAlbumArtist -> albumArtistAscending
    SortOption.FolderSongAlbumArtistDesc -> albumArtistDescending
    SortOption.FolderSongDateAdded -> newestFirst
    SortOption.FolderSongDateAddedAsc -> oldestFirst
    SortOption.FolderSongDuration -> longestFirst
    SortOption.FolderSongDurationAsc -> shortestFirst
    else -> titleAscending
}

// Songs without an album artist tag sort under their track artist.
private fun Song.albumArtistSortKey(): String =
    (albumArtist?.takeIf { it.isNotBlank() } ?: artist).lowercase()

private val titleAscending: Comparator<Song> = compareBy<Song> { it.title.lowercase() }
    .thenBy { it.artist.lowercase() }
    .thenBy { it.id }

private val titleDescending: Comparator<Song> = compareByDescending<Song> { it.title.lowercase() }
    .thenBy { it.artist.lowercase() }
    .thenBy { it.id }

// Albums that share a name stay apart; each one keeps the disc and track order of Album detail.
private val albumTracks: Comparator<Song> = compareBy<Song> { it.album.lowercase() }
    .thenBy { it.albumId }
    .thenBy { it.discNumber ?: 1 }
    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
    .then(titleAscending)

private val artistAscending: Comparator<Song> = compareBy<Song> { it.artist.lowercase() }
    .then(albumTracks)

private val artistDescending: Comparator<Song> = compareByDescending<Song> { it.artist.lowercase() }
    .then(albumTracks)

private val albumAscending: Comparator<Song> = albumTracks

private val albumDescending: Comparator<Song> = compareByDescending<Song> { it.album.lowercase() }
    .then(albumTracks)

private val albumArtistAscending: Comparator<Song> = compareBy<Song> { it.albumArtistSortKey() }
    .then(albumTracks)

private val albumArtistDescending: Comparator<Song> = compareByDescending<Song> { it.albumArtistSortKey() }
    .then(albumTracks)

private val newestFirst: Comparator<Song> = compareByDescending<Song> { it.dateAdded }
    .then(titleAscending)

private val oldestFirst: Comparator<Song> = compareBy<Song> { it.dateAdded }
    .then(titleAscending)

private val longestFirst: Comparator<Song> = compareByDescending<Song> { it.duration }
    .then(titleAscending)

private val shortestFirst: Comparator<Song> = compareBy<Song> { it.duration }
    .then(titleAscending)
