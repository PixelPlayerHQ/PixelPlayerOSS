package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JellyfinDao {

    @Query("SELECT * FROM jellyfin_songs ORDER BY date_added DESC")
    fun getAllJellyfinSongs(): Flow<List<JellyfinSongEntity>>

    /**
     * One row per Jellyfin song, newest first: its library row, or for a song that is only in
     * playlists, one of its playlist rows. The cache keeps a row per playlist a song is in, and
     * the unified library needs each song once.
     */
    @Query("""
        SELECT * FROM jellyfin_songs
        WHERE playlist_id = :libraryPlaylistId
            OR id IN (
                SELECT MIN(id) FROM jellyfin_songs
                WHERE jellyfin_id NOT IN (
                    SELECT jellyfin_id FROM jellyfin_songs WHERE playlist_id = :libraryPlaylistId
                )
                GROUP BY jellyfin_id
            )
        ORDER BY date_added DESC
    """)
    suspend fun getDistinctJellyfinSongs(libraryPlaylistId: String): List<JellyfinSongEntity>

    @Query("SELECT * FROM jellyfin_songs WHERE playlist_id = :playlistId ORDER BY date_added DESC")
    fun getSongsByPlaylist(playlistId: String): Flow<List<JellyfinSongEntity>>

    @Query("SELECT * FROM jellyfin_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<JellyfinSongEntity>>

    @Query("SELECT * FROM jellyfin_songs WHERE id IN (:ids)")
    fun getSongsByIds(ids: List<String>): Flow<List<JellyfinSongEntity>>

    @Query("SELECT * FROM jellyfin_songs WHERE jellyfin_id = :jellyfinId LIMIT 1")
    suspend fun getSongByJellyfinId(jellyfinId: String): JellyfinSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<JellyfinSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: JellyfinSongEntity)

    @Query("DELETE FROM jellyfin_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM jellyfin_songs WHERE playlist_id = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: JellyfinPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<JellyfinPlaylistEntity>)

    @Query("SELECT * FROM jellyfin_playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<JellyfinPlaylistEntity>>

    @Query("SELECT * FROM jellyfin_playlists")
    suspend fun getAllPlaylistsList(): List<JellyfinPlaylistEntity>

    @Query("SELECT * FROM jellyfin_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): JellyfinPlaylistEntity?

    @Query("DELETE FROM jellyfin_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM jellyfin_songs WHERE playlist_id = :playlistId")
    suspend fun clearSongsByPlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM jellyfin_playlists")
    suspend fun getPlaylistCount(): Int

    @Query("SELECT DISTINCT jellyfin_id FROM jellyfin_songs")
    suspend fun getAllDistinctJellyfinIds(): List<String>

    @Query("DELETE FROM jellyfin_songs WHERE playlist_id = '__library__'")
    suspend fun clearLibrarySongs()

    /**
     * Atomically replaces the cached library songs — clear+insert as two separate calls
     * leaves the cache empty if the process dies in between.
     */
    @Transaction
    suspend fun replaceLibrarySongs(songs: List<JellyfinSongEntity>) {
        clearLibrarySongs()
        insertSongs(songs)
    }

    @Query("DELETE FROM jellyfin_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM jellyfin_playlists")
    suspend fun clearAllPlaylists()
}
