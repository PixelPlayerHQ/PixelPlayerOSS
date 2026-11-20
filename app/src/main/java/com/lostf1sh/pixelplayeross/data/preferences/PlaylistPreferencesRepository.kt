package com.lostf1sh.pixelplayeross.data.preferences

import com.lostf1sh.pixelplayeross.data.database.LocalPlaylistDao
import com.lostf1sh.pixelplayeross.data.database.TransitionDao
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.database.toEntity
import com.lostf1sh.pixelplayeross.data.database.toPlaylist
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylist
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.playlist.mergeVisiblePlaylistOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistPreferencesRepository @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transitionDao: TransitionDao
) {
    private val migrationMutex = Mutex()
    @Volatile
    private var migrationChecked = false

    val userPlaylistsFlow: Flow<List<Playlist>> = localPlaylistDao.observePlaylistsWithSongs()
        .onStart { ensureMigratedIfNeeded() }
        .map { rows ->
            rows.map { row ->
                row.playlist.toPlaylist(
                    songIds = row.songs.sortedBy { it.sortOrder }.map { it.songId }
                )
            }
        }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
        userPreferencesRepository.playlistSongOrderModesFlow
    val playlistsSortOptionFlow: Flow<String> = userPreferencesRepository.playlistsSortOptionFlow

    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        isQueueGenerated: Boolean = false,
        coverImageUri: String? = null,
        coverColorArgb: Int? = null,
        coverIconName: String? = null,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        customId: String? = null,
        source: String = "LOCAL"
    ): Playlist {
        ensureMigratedIfNeeded()
        val now = System.currentTimeMillis()
        val newPlaylist = Playlist(
            id = customId ?: UUID.randomUUID().toString(),
            name = name,
            songIds = songIds,
            createdAt = now,
            lastModified = now,
            isQueueGenerated = isQueueGenerated,
            coverImageUri = coverImageUri,
            coverColorArgb = coverColorArgb,
            coverIconName = coverIconName,
            coverShapeType = coverShapeType,
            coverShapeDetail1 = coverShapeDetail1,
            coverShapeDetail2 = coverShapeDetail2,
            coverShapeDetail3 = coverShapeDetail3,
            coverShapeDetail4 = coverShapeDetail4,
            source = source,
        )
        localPlaylistDao.upsertPlaylist(newPlaylist.toEntity())
        localPlaylistDao.replacePlaylistSongs(newPlaylist.id, newPlaylist.songIds)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        ensureMigratedIfNeeded()
        localPlaylistDao.deletePlaylist(playlistId)
        transitionDao.deleteRulesForPlaylist(playlistId)
        clearPlaylistSongOrderMode(playlistId)
        userPreferencesRepository.setPlaylistMixEnabled(playlistId, false)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        val updated = existing.copy(
            name = newName,
            lastModified = System.currentTimeMillis()
        )
        localPlaylistDao.upsertPlaylist(updated.toEntity())
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        ensureMigratedIfNeeded()
        val previousIds = localPlaylistDao.observePlaylistSongs(playlist.id).first()
            .mapTo(HashSet()) { it.songId }
        val updated = playlist.copy(lastModified = System.currentTimeMillis())
        localPlaylistDao.upsertPlaylist(updated.toEntity())
        localPlaylistDao.replacePlaylistSongs(updated.id, updated.songIds)
        // Rules only go away once no copy of the track is left in the playlist.
        (previousIds - updated.songIds.toHashSet()).forEach { removedId ->
            transitionDao.deleteRulesForTrack(updated.id, removedId)
        }
    }

    /**
     * Saves [songIds] as the songs of [playlistId] and leaves the playlist itself alone: unlike
     * [updatePlaylist], its name, cover and lastModified stay as they are, so a background
     * write neither reorders date-sorted playlists nor undoes an edit made meanwhile. Meant for
     * adding songs: the transition rules of tracks it drops aren't deleted.
     *
     * @return false when the playlist no longer exists, in which case nothing is written.
     */
    suspend fun replacePlaylistSongs(playlistId: String, songIds: List<String>): Boolean {
        ensureMigratedIfNeeded()
        return localPlaylistDao.replaceSongsOfExistingPlaylist(playlistId, songIds)
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        if (existing.isSmartPlaylist) return
        val merged = (existing.songIds + songIdsToAdd).distinct()
        updatePlaylist(existing.copy(songIds = merged))
    }

    suspend fun addOrRemoveSongFromPlaylists(songId: String, playlistIds: List<String>): MutableList<String> {
        ensureMigratedIfNeeded()
        val currentPlaylists = userPlaylistsFlow.first()
        val removedPlaylistIds = mutableListOf<String>()

        currentPlaylists.forEach { playlist ->
            if (playlist.isSmartPlaylist) return@forEach
            val shouldContain = playlist.id in playlistIds
            val hasSong = songId in playlist.songIds
            when {
                shouldContain && !hasSong -> {
                    addSongsToPlaylist(playlist.id, listOf(songId))
                }
                !shouldContain && hasSong -> {
                    removeSongFromPlaylist(playlist.id, songId)
                    removedPlaylistIds.add(playlist.id)
                }
            }
        }
        return removedPlaylistIds
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        if (existing.isSmartPlaylist) return
        updatePlaylist(existing.copy(songIds = existing.songIds.filterNot { it == songIdToRemove }))
    }

    /**
     * Saves a new order for the songs the playlist currently shows. Songs that are missing
     * from [newSongOrderIds] because they are unavailable right now keep their positions.
     *
     * @return the full saved order, or null when the playlist can't be reordered or
     * [newSongOrderIds] doesn't match the playlist's stored songs.
     */
    suspend fun reorderSongsInPlaylist(playlistId: String, newSongOrderIds: List<String>): List<String>? {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return null
        if (existing.isSmartPlaylist) return null
        val merged = mergeVisiblePlaylistOrder(existing.songIds, newSongOrderIds) ?: return null
        if (merged != existing.songIds) {
            updatePlaylist(existing.copy(songIds = merged))
        }
        return merged
    }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) =
        userPreferencesRepository.setPlaylistSongOrderMode(playlistId, modeValue)

    suspend fun clearPlaylistSongOrderMode(playlistId: String) =
        userPreferencesRepository.clearPlaylistSongOrderMode(playlistId)

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) =
        userPreferencesRepository.setPlaylistSongOrderModes(modes)

    suspend fun setPlaylistsSortOption(optionKey: String) =
        userPreferencesRepository.setPlaylistsSortOption(optionKey)

    suspend fun getPlaylistsOnce(): List<Playlist> {
        ensureMigratedIfNeeded()
        return userPlaylistsFlow.first()
    }

    suspend fun replaceAllPlaylists(playlists: List<Playlist>) {
        ensureMigratedIfNeeded()
        localPlaylistDao.replaceAllPlaylistsTransactional(
            playlists.map { playlist -> playlist.toEntity() to playlist.songIds }
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    suspend fun removeSongFromAllPlaylists(songId: String) {
        ensureMigratedIfNeeded()
        val playlists = userPlaylistsFlow.first()
        playlists.forEach { playlist ->
            if (songId in playlist.songIds) {
                updatePlaylist(
                    playlist.copy(
                        songIds = playlist.songIds.filterNot { it == songId }
                    )
                )
            }
        }
    }

    suspend fun resetPlaylistPreferencesToDefaults() {
        setPlaylistSongOrderModes(emptyMap())
        setPlaylistsSortOption(SortOption.PlaylistNameAZ.storageKey)
    }

    private suspend fun ensureMigratedIfNeeded() {
        if (migrationChecked) return
        migrationMutex.withLock {
            if (migrationChecked) return
            val roomCount = localPlaylistDao.getPlaylistCount()
            if (roomCount == 0) {
                val legacy = userPreferencesRepository.getLegacyUserPlaylistsOnce()
                legacy.forEach { playlist ->
                    localPlaylistDao.upsertPlaylist(playlist.toEntity())
                    localPlaylistDao.replacePlaylistSongs(playlist.id, playlist.songIds)
                }
                if (legacy.isNotEmpty()) {
                    userPreferencesRepository.clearLegacyUserPlaylists()
                }
            }
            migrationChecked = true
        }
    }
}
