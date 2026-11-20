package com.lostf1sh.pixelplayeross.data.backup.module

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SmartPlaylistRule
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.fromPlaylistSource
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylistSource
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.PreferenceBackupEntry
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.di.AppScope
import com.lostf1sh.pixelplayeross.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Playlists and their ordering preferences.
 *
 * Song ids are matched against the current library on restore (see [BackupSongResolver]). A
 * restore made before the library scan has indexed the songs can't match them yet, so the songs
 * left unmatched are kept in [PENDING_RESTORE_FILE_NAME] and added to the restored playlists as
 * later library syncs find them, until every song the backup has metadata for resolves. Songs
 * it has none for aren't waited on (see [resolvableSongIds]).
 */
@Singleton
class PlaylistsModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val songResolver: BackupSongResolver,
    @BackupGson private val gson: Gson,
    private val syncManagerProvider: Provider<SyncManager>,
    @AppScope private val appScope: CoroutineScope
) : BackupModuleHandler {

    private val pendingRestoreMutex = Mutex()
    private val pendingRestoreFile: File
        get() = File(context.filesDir, PENDING_RESTORE_FILE_NAME)

    init {
        appScope.launch { retryPendingWhenIdle() }
    }

    override val section = BackupSection.PLAYLISTS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val allPlaylists = playlistPreferencesRepository.getPlaylistsOnce()

        val playlists = allPlaylists.filter { isBackedUpPlaylistSource(it.source) }

        val cloudSongIds = buildCloudSongIdSet()

        val filteredPlaylists = playlists.map { playlist ->
            playlist.copy(songIds = playlist.songIds.filter { id -> id !in cloudSongIds })
        }
        val songMetadata = songResolver.metadataFor(filteredPlaylists.flatMap { it.songIds })

        val coverImages = mutableMapOf<String, String>()
        filteredPlaylists.forEach { playlist ->
            val uri = playlist.coverImageUri ?: return@forEach
            readFileAsBase64(uri)?.let { coverImages[playlist.id] = it }
        }

        val backedUpIds = filteredPlaylists.map { it.id }.toSet()
        val payload = PlaylistsBackupPayload(
            playlists = filteredPlaylists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            mixEnabledPlaylistIds = playlistPreferencesRepository.mixEnabledPlaylistIdsFlow.first()
                .filter { it in backedUpIds }
                .ifEmpty { null },
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            songMetadata = songMetadata.ifEmpty { null },
            coverImages = coverImages.ifEmpty { null }
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
            .count { isBackedUpPlaylistSource(it.source) }
        val orderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first()
        val sortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        playlists + orderModes.size + if (sortOption.isNotBlank()) 1 else 0
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        val payload = PlaylistsBackupPayload(
            playlists = playlistPreferencesRepository.getPlaylistsOnce(),
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            mixEnabledPlaylistIds = playlistPreferencesRepository.mixEnabledPlaylistIdsFlow.first().toList(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        )
        gson.toJson(payload)
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntries(payload)
            return@withContext
        }

        val parsed = runCatching {
            gson.fromJson(payload, PlaylistsBackupPayload::class.java)
        }.getOrElse { e ->
            throw IllegalStateException("Playlists payload could not be parsed: ${e.message}", e)
        } ?: throw IllegalStateException("Playlists payload could not be parsed: empty JSON document")

        val backupPlaylists = parsed.playlists.orEmpty()
        val songMetadata = parsed.songMetadata
        val coverImages = parsed.coverImages

        // Held from the library snapshot through the playlist write, so a retry after a sync that
        // finishes meanwhile runs either before this restore or after its playlists are saved.
        // In between, it would act on the old playlists and drop the new pending restore.
        pendingRestoreMutex.withLock {
            val resolution = if (songMetadata != null && songMetadata.isNotEmpty()) {
                resolvePlaylists(backupPlaylists, songMetadata)
            } else {
                PlaylistsResolution(backupPlaylists, resolvedSongIds = emptyMap())
            }
            val pending = songMetadata?.let {
                pendingRestoreOf(backupPlaylists, it, resolution.unresolvedSongIds)
            }
            // An earlier restore's pending file left in place would fill these playlists in
            // from the other backup.
            if (!writePendingRestore(pending)) writePendingRestore(null)

            val finalPlaylists = if (coverImages != null && coverImages.isNotEmpty()) {
                restoreCoverImages(resolution.playlists, coverImages)
            } else {
                resolution.playlists
            }

            playlistPreferencesRepository.replaceAllPlaylists(finalPlaylists)
        }
        playlistPreferencesRepository.setPlaylistSongOrderModes(parsed.playlistSongOrderModes.orEmpty())
        // Mix status belongs to the playlists: restoring them restores which ones are mixes.
        playlistPreferencesRepository.setMixEnabledPlaylistIds(parsed.mixEnabledPlaylistIds.orEmpty().toSet())
        playlistPreferencesRepository.setPlaylistsSortOption(
            parsed.playlistsSortOption ?: SortOption.PlaylistNameAZ.storageKey
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    /**
     * Retries the pending restore on every distinct idle sync state: the first one at startup,
     * and each one a sync leaves behind. A running -> idle edge isn't required, because a short
     * sync can finish before its running state is ever observed.
     *
     * A retry that fails keeps the pending file for the next idle state, which counts songs the
     * failed one already added as the backup's copies instead of adding them again. Its
     * exception isn't let out: that would end this collector, and crash the app, since
     * [appScope] has no exception handler.
     */
    private suspend fun retryPendingWhenIdle() {
        syncManagerProvider.get().syncProgress
            .distinctUntilChanged()
            .filter { !it.isRunning }
            .collect {
                try {
                    resolvePendingPlaylists()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Pending playlist restore failed; retrying after the next sync")
                }
            }
    }

    /**
     * Saves [pending] for [resolvePendingPlaylists], or drops the pending restore when it's null.
     * Callers hold [pendingRestoreMutex].
     *
     * The file is written beside the pending one and then moved over it, so a process killed or
     * a disk filling up midway leaves the earlier pending restore whole instead of cut off.
     *
     * @return false when [pending] couldn't be saved; the earlier pending restore, if any, is
     * left as it was.
     */
    private fun writePendingRestore(pending: PendingPlaylistRestore?): Boolean {
        val pendingFile = pendingRestoreFile
        if (pending == null) {
            if (pendingFile.exists()) pendingFile.delete()
            return true
        }
        val tempFile = File(context.filesDir, "$PENDING_RESTORE_FILE_NAME.tmp")
        return try {
            FileOutputStream(tempFile).use { out ->
                out.write(gson.toJson(pending).toByteArray())
                out.fd.sync()
            }
            Files.move(
                tempFile.toPath(), pendingFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
            Timber.tag(TAG).i("Saved pending playlist restore for songs not in the library yet")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save pending playlist restore file")
            tempFile.delete()
            false
        }
    }

    /**
     * What a restore of [playlists] still has to fill in once the library has the
     * [unresolvedSongIds], or null when no playlist is missing a song that can still resolve.
     */
    private fun pendingRestoreOf(
        playlists: List<Playlist>,
        songMetadata: Map<String, BackupSongMetadata>,
        unresolvedSongIds: Set<String>
    ): PendingPlaylistRestore? {
        val resolvable = resolvableSongIds(unresolvedSongIds, songMetadata)
        val waiting = playlists.filter { playlist -> playlist.songIds.any { it in resolvable } }
        if (waiting.isEmpty()) return null
        val waitingSongIds = waiting.flatMapTo(HashSet()) { it.songIds }
        return PendingPlaylistRestore(
            playlists = waiting,
            songMetadata = songMetadata.filterKeys { it in waitingSongIds },
            unresolvedSongIds = resolvable.filter { it in waitingSongIds }
        )
    }

    /**
     * The [songIds] that [songMetadata] describes, in order. The backup only has metadata for
     * songs that were in its library, so the others can only ever match a library song by id,
     * with nothing to check it against. On the device the backup came from, that can be the
     * right song, such as one on an SD card that was out when the backup was made, which keeps
     * its id when the card is scanned again. On any other device it's whatever song is given
     * that id, and new songs keep taking ids for as long as the restore would wait. The two
     * can't be told apart, so these songs aren't waited on: a restore keeps one only when the
     * library already has its id, and loses the SD card's songs when made before they're
     * scanned. A playlist entry for a song deleted before the backup then doesn't keep the
     * pending restore around forever either.
     */
    private fun resolvableSongIds(
        songIds: Collection<String>,
        songMetadata: Map<String, BackupSongMetadata>
    ): Set<String> = songIds.filterTo(LinkedHashSet()) { it in songMetadata }

    /**
     * Re-matches the pending restore's unresolved songs against the library and adds the ones it
     * has gained to their restored playlists. Songs already in a playlist are left alone, so
     * songs the user removed, moved or added since the restore stay that way. The pending file
     * is dropped once every song that can resolve has, or the playlists missing songs are gone.
     */
    private suspend fun resolvePendingPlaylists() = pendingRestoreMutex.withLock {
        val pendingFile = pendingRestoreFile
        if (!pendingFile.exists()) return@withLock

        val json = try {
            pendingFile.readText()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read pending playlist restore file")
            return@withLock
        }
        // A file this can't parse is dropped, and so is one from before unresolvedSongIds was
        // recorded, which holds the whole backup payload instead: it doesn't say which songs its
        // restore left out, so filling them in could bring back songs the user removed since.
        val pending = runCatching { gson.fromJson(json, PendingPlaylistRestore::class.java) }.getOrNull()
        val songMetadata = pending?.songMetadata.orEmpty()
        val unresolvedSongIds = resolvableSongIds(pending?.unresolvedSongIds.orEmpty(), songMetadata)
        if (pending == null || unresolvedSongIds.isEmpty()) {
            pendingFile.delete()
            Timber.tag(TAG).i("Pending playlist restore: dropped, nothing it can still fill in")
            return@withLock
        }

        val backupPlaylists = pending.playlists.orEmpty()
        val resolution = resolvePlaylists(backupPlaylists, songMetadata)
        val newlyResolved = unresolvedSongIds.filterTo(HashSet()) { resolution.resolvedSongIds[it] != null }

        val currentById = playlistPreferencesRepository.getPlaylistsOnce().associateBy { it.id }
        // Playlists deleted since the restore are left out, and so are smart playlists with a
        // rule, which rebuild their songs from it.
        val waiting = backupPlaylists.filter { backup ->
            val current = currentById[backup.id]
            current != null && SmartPlaylistRule.fromPlaylistSource(current.source) == null
        }
        var grownCount = 0
        if (newlyResolved.isNotEmpty()) {
            waiting.forEach { backup ->
                val current = currentById.getValue(backup.id)
                val songIds = insertNewlyResolvedSongs(
                    current.songIds, backup.songIds, newlyResolved, resolution.resolvedSongIds
                )
                // Only this playlist's songs are written: its name, cover and lastModified, and
                // the other playlists, keep any edit made since they were read.
                if (songIds != current.songIds &&
                    playlistPreferencesRepository.replacePlaylistSongs(current.id, songIds)
                ) {
                    grownCount++
                }
            }
        }

        val stillPending = pendingRestoreOf(waiting, songMetadata, unresolvedSongIds - newlyResolved)
        if (grownCount > 0) {
            Timber.tag(TAG).i(
                "Pending playlist restore: $grownCount playlists grew, " +
                    "${stillPending?.unresolvedSongIds.orEmpty().size} songs still unresolved"
            )
        }
        if (stillPending == null) {
            pendingFile.delete()
            Timber.tag(TAG).i("Pending playlist restore: nothing left to fill in")
        } else if (stillPending != pending) {
            // When this fails, the next retry reads the earlier file again, and counts the songs
            // this one added as the backup's copies instead of adding them twice.
            writePendingRestore(stillPending)
        }
    }

    /**
     * [current] plus a copy of its song for each [newlyResolved] position of [backupSongIds], so
     * a song the backup repeats is repeated here too. Each goes right after the copy that the
     * nearest earlier backup position put in the playlist, or first when there is none. Copies
     * the user added before the song resolved count as backup copies instead of getting another.
     * An unedited playlist ends up in backup order; songs the user removed, moved or added
     * stay as they are.
     */
    private fun insertNewlyResolvedSongs(
        current: List<String>,
        backupSongIds: List<String>,
        newlyResolved: Set<String>,
        resolvedSongIds: Map<String, String?>
    ): List<String> {
        val resolved = backupSongIds.map { resolvedSongIds[it] }
        // The copies of each song beyond the ones the backup's earlier resolved positions put
        // there were added by the user.
        val userCopies = HashMap<String, Int>()
        current.forEach { userCopies.merge(it, 1, Int::plus) }
        backupSongIds.forEachIndexed { index, backupId ->
            val songId = resolved[index]
            if (songId != null && backupId !in newlyResolved) userCopies.merge(songId, -1, Int::plus)
        }

        val songIds = current.toMutableList()
        // Which copy of its song each backup position stands for: 1 for the first, and so on.
        val copyNumbers = IntArray(backupSongIds.size)
        val copiesSoFar = HashMap<String, Int>()
        backupSongIds.forEachIndexed { index, backupId ->
            val songId = resolved[index] ?: return@forEachIndexed
            copyNumbers[index] = copiesSoFar.merge(songId, 1, Int::plus) ?: 1
            if (backupId !in newlyResolved) return@forEachIndexed
            val spare = userCopies[songId] ?: 0
            if (spare > 0) {
                userCopies[songId] = spare - 1
                return@forEachIndexed
            }
            val anchor = (index - 1 downTo 0).asSequence()
                .mapNotNull { previous ->
                    resolved[previous]?.let { songIds.indexOfCopy(it, copyNumbers[previous]) }
                }
                .firstOrNull { it >= 0 }
            songIds.add(anchor?.plus(1) ?: 0, songId)
        }
        return songIds
    }

    /** The index of copy [copy] of [songId], of its last copy when there are fewer, or -1. */
    private fun List<String>.indexOfCopy(songId: String, copy: Int): Int {
        var lastIndex = -1
        var seen = 0
        forEachIndexed { index, id ->
            if (id != songId) return@forEachIndexed
            lastIndex = index
            if (++seen == copy) return index
        }
        return lastIndex
    }

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read cover image: $path")
            null
        }
    }

    private fun restoreCoverImages(
        playlists: List<Playlist>,
        coverImages: Map<String, String>
    ): List<Playlist> {
        return playlists.map { playlist ->
            val base64 = coverImages[playlist.id] ?: return@map playlist
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val fileName = "playlist_cover_${playlist.id}.jpg"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)
                playlist.copy(coverImageUri = file.absolutePath)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to restore cover image for playlist ${playlist.id}")
                playlist.copy(coverImageUri = null)
            }
        }
    }

    /**
     * Resolves backup song IDs to current device song IDs (see [BackupSongResolver]). Songs
     * without a confident match are left out of the playlist, since keeping their backup ids
     * would risk false matches; [resolvePendingPlaylists] retries them after the next sync.
     */
    private suspend fun resolvePlaylists(
        playlists: List<Playlist>,
        songMetadata: Map<String, BackupSongMetadata>
    ): PlaylistsResolution {
        val library = songResolver.library()

        val resolutionCache = mutableMapOf<String, String?>()
        var totalSongs = 0
        var resolvedCount = 0
        var unresolvedCount = 0

        playlists.forEach { playlist ->
            playlist.songIds.forEach { songId ->
                if (songId !in resolutionCache) {
                    totalSongs++
                    val resolved = library.resolve(songId, songMetadata[songId])
                    resolutionCache[songId] = resolved
                    if (resolved != null) resolvedCount++ else unresolvedCount++
                }
            }
        }

        if (unresolvedCount > 0) {
            Timber.tag(TAG).w("Playlist restore: $resolvedCount/$totalSongs songs resolved, $unresolvedCount unresolved")
        }

        val resolvedPlaylists = playlists.map { playlist ->
            playlist.copy(songIds = playlist.songIds.mapNotNull { songId -> resolutionCache[songId] })
        }
        return PlaylistsResolution(resolvedPlaylists, resolutionCache)
    }

    private data class PlaylistsResolution(
        val playlists: List<Playlist>,
        /** This library's id for each backup song id, or null when no song matches it yet. */
        val resolvedSongIds: Map<String, String?>
    ) {
        val unresolvedSongIds: Set<String>
            get() = resolvedSongIds.filterValues { it == null }.keys
    }

    private suspend fun buildCloudSongIdSet(): Set<String> {
        val cloudIds = mutableSetOf<String>()
        musicDao.getAllNavidromeSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllJellyfinSongIds().mapTo(cloudIds) { it.toString() }
        return cloudIds
    }

    private suspend fun restoreLegacyPreferenceEntries(payload: String) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)

        val playlists = entries.firstOrNull { it.key == LEGACY_USER_PLAYLISTS_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val playlistType = TypeToken.getParameterized(List::class.java, Playlist::class.java).type
                    gson.fromJson<List<Playlist>>(raw, playlistType)
                }.getOrDefault(emptyList())
            }
            .orEmpty()

        val playlistSongOrderModes = entries.firstOrNull { it.key == LEGACY_PLAYLIST_ORDER_MODES_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val mapType = TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                    gson.fromJson<Map<String, String>>(raw, mapType)
                }.getOrDefault(emptyMap())
            }
            .orEmpty()

        val playlistsSortOption = entries.firstOrNull { it.key == LEGACY_PLAYLIST_SORT_OPTION_KEY }
            ?.stringValue
            ?: SortOption.PlaylistNameAZ.storageKey

        pendingRestoreMutex.withLock {
            // These playlists replace the ones an earlier restore was still filling in.
            writePendingRestore(null)
            playlistPreferencesRepository.replaceAllPlaylists(playlists)
        }
        playlistPreferencesRepository.setPlaylistSongOrderModes(playlistSongOrderModes)
        playlistPreferencesRepository.setPlaylistsSortOption(playlistsSortOption)
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    private data class PlaylistsBackupPayload(
        val playlists: List<Playlist>? = null,
        val playlistSongOrderModes: Map<String, String>? = null,
        /** Ids of the playlists that are DJ-style mixes. Null in backups made before mixes existed. */
        val mixEnabledPlaylistIds: List<String>? = null,
        val playlistsSortOption: String? = null,
        /** Song metadata for cross-device matching. Key = songId from backup. Null in legacy/snapshot payloads. */
        val songMetadata: Map<String, BackupSongMetadata>? = null,
        /** Base64-encoded cover images. Key = playlist ID. Null if no custom covers. */
        val coverImages: Map<String, String>? = null
    )

    /** The part of a restore kept in [PENDING_RESTORE_FILE_NAME] until its songs resolve. */
    private data class PendingPlaylistRestore(
        /** The backed-up playlists still missing songs, with their backup song ids. */
        val playlists: List<Playlist>? = null,
        val songMetadata: Map<String, BackupSongMetadata>? = null,
        /** Backup song ids [songMetadata] describes that no library song has matched yet. */
        val unresolvedSongIds: List<String>? = null
    )

    companion object {
        private const val TAG = "PlaylistsModuleHandler"
        private const val PENDING_RESTORE_FILE_NAME = "pending_playlists_restore.json"

        /** Playlist sources that are backed up. Cloud-sourced playlists are excluded. */
        private fun isBackedUpPlaylistSource(source: String): Boolean =
            source == "LOCAL" || isSmartPlaylistSource(source)

        const val LEGACY_USER_PLAYLISTS_KEY = "user_playlists_json_v1"
        const val LEGACY_PLAYLIST_ORDER_MODES_KEY = "playlist_song_order_modes"
        const val LEGACY_PLAYLIST_SORT_OPTION_KEY = "playlists_sort_option"
        const val MIX_ENABLED_PLAYLISTS_KEY = "mix_enabled_playlist_ids"
        val PLAYLIST_KEYS = setOf(
            LEGACY_USER_PLAYLISTS_KEY,
            LEGACY_PLAYLIST_ORDER_MODES_KEY,
            LEGACY_PLAYLIST_SORT_OPTION_KEY,
            // Per-playlist state: kept out of global settings and carried by the playlists payload.
            MIX_ENABLED_PLAYLISTS_KEY
        )
    }
}
