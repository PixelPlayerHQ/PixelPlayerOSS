package com.lostf1sh.pixelplayeross.data.repository

import android.net.Uri
import androidx.paging.PagingData
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SearchFilterType
import com.lostf1sh.pixelplayeross.data.model.SearchHistoryItem
import com.lostf1sh.pixelplayeross.data.model.SearchResultItem
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    /**
     * Returns the list of audio files (songs) filtered by allowed directories.
     * @return Flow that emits a complete list of Song objects.
     */
    fun getAudioFiles(): Flow<List<Song>>

    /**
     * Returns paginated songs for efficient display of large libraries.
     * @return Flow of PagingData<Song> for use with LazyPagingItems.
     */
    fun getPaginatedSongs(sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption, storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter): Flow<PagingData<Song>>

    /**
     * Returns paginated albums for efficient display in library tabs.
     */
    fun getPaginatedAlbums(
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL,
        minTracks: Int = 1
    ): Flow<PagingData<Album>>

    /**
     * Returns paginated artists for efficient display in library tabs.
     */
    fun getPaginatedArtists(
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): Flow<PagingData<Artist>>

    /**
     * Returns paginated favorite songs for efficient display.
     * @return Flow of PagingData<Song> for use with LazyPagingItems.
     */
    fun getPaginatedFavoriteSongs(
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): Flow<PagingData<Song>>

    /**
     * Returns all favorite songs as a list (for playback queue on shuffle).
     */
    suspend fun getFavoriteSongsOnce(
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): List<Song>

    /**
     * Returns a bounded favorites page without materializing the full favorites list.
     */
    suspend fun getFavoriteSongsPage(
        limit: Int,
        offset: Int,
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption = com.lostf1sh.pixelplayeross.data.model.SortOption.LikedSongTitleAZ,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): List<Song>

    /**
     * Returns the count of favorite songs (reactive).
     */
    fun getFavoriteSongCountFlow(
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): Flow<Int>

    /**
     * Returns the count of songs in the library.
     * @return Flow emitting the current song count.
     */
    fun getSongCountFlow(): Flow<Int>

    /**
     * Returns the count of cloud songs in the library.
     */
    fun getCloudSongCountFlow(): Flow<Int>

    /**
     * Returns a random selection of songs for efficient shuffle.
     * Uses database-level RANDOM() for performance.
     * @param limit Maximum number of songs to return.
     * @return List of randomly selected songs.
     */
    suspend fun getRandomSongs(limit: Int): List<Song>

    /**
     * Returns a bounded song page without materializing the full library.
     */
    suspend fun getSongsPage(
        limit: Int,
        offset: Int,
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption = com.lostf1sh.pixelplayeross.data.model.SortOption.SongDefaultOrder,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): List<Song>

    /**
     * Returns a bounded album page without materializing the full albums list.
     */
    suspend fun getAlbumsPage(
        limit: Int,
        offset: Int,
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption = com.lostf1sh.pixelplayeross.data.model.SortOption.AlbumTitleAZ,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL,
        minTracks: Int = 1
    ): List<Album>

    /**
     * Returns a bounded artist page without materializing the full artists list.
     */
    suspend fun getArtistsPage(
        limit: Int,
        offset: Int,
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption = com.lostf1sh.pixelplayeross.data.model.SortOption.ArtistNameAZ,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): List<Artist>

    /**
     * Returns a single playable song without materializing the entire library.
     * Useful for startup and fallback playback paths.
     */
    suspend fun getFirstPlayableSong(): Song?

    /**
     * Returns the filtered list of albums.
     * @return Flow that emits a complete list of Album objects.
     */
    fun getAlbums(
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL,
        minTracks: Int = 1
    ): Flow<List<Album>>

    /**
     * Returns the filtered list of artists.
     * @return Flow that emits a complete list of Artist objects.
     */
    fun getArtists(
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): Flow<List<Artist>>

    /**
     * Returns the complete list of songs once.
     * @return List of Song objects.
     */
    suspend fun getAllSongsOnce(): List<Song>

    /**
     * Returns one representative song per unique album art URI for maintenance tools that
     * operate on artwork-derived palettes.
     */
    fun getDistinctAlbumArtSongs(): Flow<List<Song>>

    /**
     * Returns a bounded preview sample for Home without materializing the full library in UI.
     */
    fun getHomeMixPreviewSongs(limit: Int): Flow<List<Song>>

    /**
     * Returns the complete list of albums once.
     * @return List of Album objects.
     */
    suspend fun getAllAlbumsOnce(
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL,
        minTracks: Int = 1
    ): List<Album>

    /**
     * Returns the complete list of artists once.
     * @return List of Artist objects.
     */
    suspend fun getAllArtistsOnce(): List<Artist>

    /**
     * Returns a specific album by its ID.
     * @param id The album ID.
     * @return Flow that emits the Album object, or null if not found.
     */
    fun getAlbumById(id: Long): Flow<Album?>

    /**
     * Returns the filtered list of artists.
     * @return Flow that emits a complete list of Artist objects.
     */

    /**
     * Returns the list of songs for a specific album (NOT paginated, for the playback queue).
     * @param albumId The album ID.
     * @return Flow that emits a list of Song objects belonging to the album.
     */
    fun getSongsForAlbum(albumId: Long): Flow<List<Song>>

    /**
     * Returns the list of songs for a specific artist (NOT paginated, for the playback queue).
     * @param artistId The artist ID.
     * @return Flow that emits a list of Song objects belonging to the artist.
     */
    fun getSongsForArtist(artistId: Long): Flow<List<Song>>

    /**
     * Returns a list of songs by their IDs.
     * @param songIds List of song IDs.
     * @return Flow that emits a list of Song objects matching the IDs, in the same order.
     */
    fun getSongsByIds(songIds: List<String>): Flow<List<Song>>

    /**
     * Returns a song by its file path.
     * @param path The file path.
     * @return The Song object, or null if not found.
     */
    suspend fun getSongByPath(path: String): Song?

    /**
     * Returns all unique directories that contain audio files.
     * This is mainly used for the initial directory setup.
     * It also handles the initial save of allowed directories on first run.
     * @return Set of unique directory paths.
     */
    suspend fun getAllUniqueAudioDirectories(): Set<String>

    fun getAllUniqueAlbumArtUris(): Flow<List<Uri>>

    suspend fun invalidateCachesDependentOnAllowedDirectories()

    fun searchSongs(query: String): Flow<List<Song>>
    fun searchAlbums(query: String, minTracks: Int = 1): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    suspend fun searchPlaylists(query: String): List<Playlist>
    fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>>

    suspend fun addSearchHistoryItem(query: String)
    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem>
    suspend fun deleteSearchHistoryItemByQuery(query: String)
    suspend fun clearSearchHistory()

    /**
     * Returns the list of songs for a specific genre (placeholder implementation).
     * @param genreId The genre ID (e.g., "pop", "rock").
     * @return Flow that emits a list of Song objects (simulated for this genre).
     */
    fun getMusicByGenre(genreId: String): Flow<List<Song>>

    /**
     * Toggles the favorite status of a song.
     * @param songId The song ID.
     * @return The new favorite status (true if favorite, false otherwise).
     */
    suspend fun toggleFavoriteStatus(songId: String): Boolean

    /**
     * Explicitly sets the favorite status of a song.
     * @param songId The song ID.
     * @param isFavorite Target status.
     */
    suspend fun setFavoriteStatus(songId: String, isFavorite: Boolean)

    /**
     * Returns favorite song IDs directly from Room (favorites table).
     */
    suspend fun getFavoriteSongIdsOnce(): Set<String>

    /**
     * Reactive stream of favorite song IDs from Room favorites table.
     */
    fun getFavoriteSongIdsFlow(): Flow<Set<String>>

    /**
     * Returns a specific song by its ID.
     * @param songId The song ID.
     * @return Flow that emits the Song object, or null if not found.
     */
    fun getSong(songId: String): Flow<Song?>
    fun getArtistById(artistId: Long): Flow<Artist?>
    suspend fun getArtistIdByName(name: String): Long?
    fun getArtistsForSong(songId: Long): Flow<List<Artist>>

    /**
     * Returns the list of genres, either mocked or read from metadata.
     * @return Flow that emits a list of Genre objects.
     */
    fun getGenres(): Flow<List<com.lostf1sh.pixelplayeross.data.model.Genre>>

    suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Lyrics?

    suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>?

    suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>>

    /**
     * Search for lyrics remotely, less specific than `getLyricsFromRemote` but more lenient
     * @param song The song to search lyrics for
     * @return The search query and the results
     */
    suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>>

    /**
     * Search for lyrics remotely using query provided, and not use song metadata
     * @param query The query for searching, typically song title and artist name
     * @return The search query and the results
     */
    suspend fun searchRemoteLyricsByQuery(title: String, artist: String? = null): Result<Pair<String, List<LyricsSearchResult>>>

    suspend fun updateLyrics(songId: Long, lyrics: String)

    suspend fun resetLyrics(songId: Long)

    suspend fun resetAllLyrics()

    fun getMusicFolders(
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter = com.lostf1sh.pixelplayeross.data.model.StorageFilter.ALL
    ): Flow<List<com.lostf1sh.pixelplayeross.data.model.MusicFolder>>

    suspend fun deleteById(id: Long)

    suspend fun getSongIdsSorted(
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter
    ): List<Long>

    suspend fun getFavoriteSongIdsSorted(
        sortOption: com.lostf1sh.pixelplayeross.data.model.SortOption,
        storageFilter: com.lostf1sh.pixelplayeross.data.model.StorageFilter
    ): List<Long>

    /**
     * Resolves the unified-table song id for a content URI. Returns null if no
     * matching row exists. Used by locate-current-song to recover from playback
     * sessions where `Song.id` is a non-numeric source-specific string.
     */
    suspend fun getSongIdByContentUri(contentUri: String): Long?
}
