@file:Suppress("DEPRECATION")
package com.lostf1sh.pixelplayeross.data.jellyfin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lostf1sh.pixelplayeross.BuildConfig
import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.ArtistEntity
import com.lostf1sh.pixelplayeross.data.database.FavoritesDao
import com.lostf1sh.pixelplayeross.data.database.FavoritesEntity
import com.lostf1sh.pixelplayeross.data.database.JellyfinDao
import com.lostf1sh.pixelplayeross.data.database.JellyfinPlaylistEntity
import com.lostf1sh.pixelplayeross.data.database.JellyfinSongEntity
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongArtistCrossRef
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.SourceType
import com.lostf1sh.pixelplayeross.data.database.toEntity
import com.lostf1sh.pixelplayeross.data.database.toSong
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinCredentials
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinLibrary
import com.lostf1sh.pixelplayeross.data.jellyfin.model.JellyfinSong
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.network.jellyfin.JellyfinApiService
import com.lostf1sh.pixelplayeross.data.network.jellyfin.JellyfinResponseParser
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.stream.BulkSyncResult
import com.lostf1sh.pixelplayeross.data.stream.CloudMusicUtils
import com.lostf1sh.pixelplayeross.utils.GenreParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Suppress("DEPRECATION")
@Singleton
class JellyfinRepository @Inject constructor(
    private val api: JellyfinApiService,
    private val dao: JellyfinDao,
    private val musicDao: MusicDao,
    private val favoritesDao: FavoritesDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val TAG = "JellyfinRepo"
        private const val PREFS_NAME = "jellyfin_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_FULL_SYNC = "last_full_sync"
        private const val KEY_SONG_CACHE_REVISION = "song_cache_revision"
        private const val KEY_UNIFIED_LIBRARY_STATE = "unified_library_state"
        private const val KEY_LAST_SYNCED_FAVORITE_IDS = "last_synced_favorite_ids"
        private const val KEY_HAS_FAVORITE_SYNC_BASELINE = "has_favorite_sync_baseline"
        private const val KEY_FAVORITE_SYNC_ACCOUNT = "favorite_sync_account"
        private const val KEY_PENDING_FAVORITE_ADDS = "pending_favorite_adds"
        private const val KEY_PENDING_FAVORITE_REMOVES = "pending_favorite_removes"

        private const val JELLYFIN_SONG_ID_OFFSET = 12_000_000_000_000L
        private const val JELLYFIN_ALBUM_ID_OFFSET = 13_000_000_000_000L
        private const val JELLYFIN_ARTIST_ID_OFFSET = 14_000_000_000_000L
        private const val JELLYFIN_PARENT_DIRECTORY = "/Cloud/Jellyfin"
        private const val JELLYFIN_GENRE = GenreParser.JELLYFIN_PLACEHOLDER
        private const val JELLYFIN_PLAYLIST_PREFIX = "jellyfin_playlist:"
        private const val LIBRARY_PLAYLIST_ID = "__library__"
    }

    private val prefs: SharedPreferences = createCredentialPrefs()

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun createCredentialPrefs(): SharedPreferences = try {
        createEncryptedPrefs()
    } catch (e: Exception) {
        Timber.e(e, "$TAG: EncryptedSharedPreferences unreadable, deleting and recreating")
        context.deleteSharedPreferences(PREFS_NAME)
        try {
            createEncryptedPrefs()
        } catch (e2: Exception) {
            Timber.e(e2, "$TAG: Encrypted prefs unavailable, falling back to plain")
            context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    init {
        initFromSavedCredentials()
    }

    private fun initFromSavedCredentials() {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)

        if (!serverUrl.isNullOrBlank() && !username.isNullOrBlank() &&
            !accessToken.isNullOrBlank() && !userId.isNullOrBlank()) {
            val credentials = JellyfinCredentials(serverUrl, username, "", accessToken, userId)
            val validationError = credentials.connectionValidationError()
            if (validationError != null) {
                Timber.w("$TAG: Ignoring invalid saved Jellyfin server URL: $validationError")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                return
            }
            api.setCredentials(credentials)
            _isLoggedInFlow.value = true
            Timber.d("$TAG: Restored credentials for $username@${credentials.normalizedServerUrl}")
        }
    }

    val isLoggedIn: Boolean
        get() = _isLoggedInFlow.value

    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    /** When the last complete library and playlist sync finished, or 0 if none has. */
    var lastFullSyncTime: Long
        get() = prefs.getLong(KEY_LAST_FULL_SYNC, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_FULL_SYNC, value).apply()

    val isFullSyncDue: Boolean
        get() = isJellyfinFullSyncDue(lastFullSyncTime, System.currentTimeMillis())

    /** Counts writes to the cached Jellyfin songs, so a unified-library rebuild can tell it is due. */
    private val songCacheRevision: Long
        get() = prefs.getLong(KEY_SONG_CACHE_REVISION, 0L)

    /**
     * Held by every write to the cached Jellyfin songs and every unified-library rebuild, so a
     * rebuild never reads a write's new revision together with the rows from before it.
     */
    private val songCacheMutex = Mutex()
    private val favoritesSyncMutex = Mutex()
    private val localFavoritesMutex = Mutex()
    private val accountSessionGeneration = AtomicLong(0L)
    private val favoriteMutationGeneration = AtomicLong(0L)
    private val inFlightLocalFavoriteMutations = ConcurrentHashMap<String, Pair<Long, Boolean>>()

    data class FavoriteMutationToken(
        val sessionGen: Long,
        val accountKey: String,
        val mutationGen: Long
    )

    suspend fun <T> withLocalFavoriteLock(block: suspend () -> T): T =
        localFavoritesMutex.withLock { block() }

    private fun currentAccountKey(): String? {
        val server = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val user = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return "$server|$user"
    }

    private fun isFavoriteSessionCurrent(sessionGen: Long, accountKey: String): Boolean {
        return isLoggedIn &&
            accountSessionGeneration.get() == sessionGen &&
            currentAccountKey() == accountKey
    }

    private fun clearFavoriteSyncPrefs() {
        prefs.edit()
            .remove(KEY_FAVORITE_SYNC_ACCOUNT)
            .remove(KEY_HAS_FAVORITE_SYNC_BASELINE)
            .remove(KEY_LAST_SYNCED_FAVORITE_IDS)
            .remove(KEY_PENDING_FAVORITE_ADDS)
            .remove(KEY_PENDING_FAVORITE_REMOVES)
            .apply()
    }

    /** Runs a write to the cached Jellyfin songs. Logout clears them along with this state. */
    private suspend fun <T> writeSongCache(write: suspend () -> T): T = songCacheMutex.withLock {
        withContext(Dispatchers.IO) {
            // Bumped first, and with commit rather than apply, so a write that outlives an
            // unsaved bump cannot leave the unified library looking current.
            prefs.edit().putLong(KEY_SONG_CACHE_REVISION, songCacheRevision + 1).commit()
        }
        write()
    }

    fun getAuthorizationHeader(): String? = api.getAuthorizationHeader()

    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    suspend fun login(serverUrl: String, username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Attempting login to $serverUrl as $username")

                val credentials = JellyfinCredentials(serverUrl, username, password)
                val validationError = credentials.connectionValidationError()
                if (validationError != null) {
                    api.clearCredentials()
                    return@withContext Result.failure(IllegalArgumentException(validationError))
                }

                val authResult = api.authenticateByName(
                    credentials.normalizedServerUrl, username, password
                )
                if (authResult.isFailure) {
                    api.clearCredentials()
                    return@withContext Result.failure(
                        authResult.exceptionOrNull() ?: Exception("Authentication failed")
                    )
                }

                val (accessToken, userId) = authResult.getOrThrow()
                val fullCredentials = credentials.copy(accessToken = accessToken, userId = userId)
                favoritesSyncMutex.withLock {
                    accountSessionGeneration.incrementAndGet()
                    inFlightLocalFavoriteMutations.clear()
                    api.setCredentials(fullCredentials)

                    prefs.edit()
                        .putString(KEY_SERVER_URL, fullCredentials.normalizedServerUrl)
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_ACCESS_TOKEN, accessToken)
                        .putString(KEY_USER_ID, userId)
                        .remove(KEY_FAVORITE_SYNC_ACCOUNT)
                        .remove(KEY_HAS_FAVORITE_SYNC_BASELINE)
                        .remove(KEY_LAST_SYNCED_FAVORITE_IDS)
                        .remove(KEY_PENDING_FAVORITE_ADDS)
                        .remove(KEY_PENDING_FAVORITE_REMOVES)
                        .apply()

                    _isLoggedInFlow.value = true
                }
                Timber.d("$TAG: Login successful for $username@$serverUrl")
                Result.success(username)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Login failed")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                Result.failure(e)
            }
        }
    }

    suspend fun logout() {
        Timber.d("$TAG: Logging out")
        // Invalidate the current session immediately so any in-flight favourite push or sync
        // discards its result instead of persisting retries after credentials are cleared.
        accountSessionGeneration.incrementAndGet()
        inFlightLocalFavoriteMutations.clear()
        _isLoggedInFlow.value = false

        favoritesSyncMutex.withLock {
            api.clearCredentials()
            prefs.edit().clear().apply()
            userPreferencesRepository.clearJellyfinSelectedLibraryIds()
            userPreferencesRepository.clearJellyfinSyncFavorites()
        }

        val playlistsToDelete = dao.getAllPlaylistsList()
        playlistsToDelete.forEach { playlist ->
            deleteAppPlaylistForJellyfinPlaylist(playlist.id)
        }

        dao.clearAllSongs()
        musicDao.clearAllJellyfinSongs()
        dao.clearAllPlaylists()
    }

    val selectedLibraryIdsFlow: Flow<Set<String>> =
        userPreferencesRepository.jellyfinSelectedLibraryIdsFlow

    val syncFavoritesFlow: Flow<Boolean> =
        userPreferencesRepository.jellyfinSyncFavoritesFlow

    suspend fun setSyncFavoritesEnabled(enabled: Boolean) {
        userPreferencesRepository.setJellyfinSyncFavorites(enabled)
        if (!enabled) {
            inFlightLocalFavoriteMutations.clear()
            favoritesSyncMutex.withLock {
                clearFavoriteSyncPrefs()
            }
        }
    }

    /**
     * Records a local favourite toggle synchronously before awaiting the network lock, binding
     * the pending push to the currently logged-in account session so neither an in-flight
     * [syncFavorites] pass nor a subsequent logout/account-switch can misapply it.
     */
    fun recordLocalFavoriteMutation(jellyfinId: String, isFavorite: Boolean): FavoriteMutationToken? {
        val sessionGen = accountSessionGeneration.get()
        val accountKey = currentAccountKey()
        if (!isLoggedIn || accountKey == null || jellyfinId.isBlank()) return null
        val gen = favoriteMutationGeneration.incrementAndGet()
        inFlightLocalFavoriteMutations[jellyfinId] = gen to isFavorite
        return FavoriteMutationToken(
            sessionGen = sessionGen,
            accountKey = accountKey,
            mutationGen = gen
        )
    }

    fun clearLocalFavoriteMutation(
        jellyfinId: String,
        isFavorite: Boolean,
        token: FavoriteMutationToken
    ) {
        inFlightLocalFavoriteMutations.remove(jellyfinId, token.mutationGen to isFavorite)
    }

    suspend fun pushFavoriteChange(
        jellyfinId: String,
        isFavorite: Boolean,
        recordedToken: FavoriteMutationToken? = null
    ) {
        val token = recordedToken ?: recordLocalFavoriteMutation(jellyfinId, isFavorite) ?: return
        val sessionGen = token.sessionGen
        val accountKey = token.accountKey
        val mutationGen = token.mutationGen

        try {
            if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return
            val enabled = userPreferencesRepository.jellyfinSyncFavoritesFlow.first()
            if (!enabled) return

            favoritesSyncMutex.withLock {
                withContext(Dispatchers.IO) {
                    if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext
                    if (!userPreferencesRepository.jellyfinSyncFavoritesFlow.first()) return@withContext
                    // Skip superseded pushes when the user toggled the same song again while waiting.
                    val latest = inFlightLocalFavoriteMutations[jellyfinId]
                    if (latest != null && latest.first != mutationGen) return@withContext

                    val result = if (isFavorite) {
                        api.markFavorite(jellyfinId)
                    } else {
                        api.unmarkFavorite(jellyfinId)
                    }

                    // Re-check session after network I/O so logout/account-switch never stores stale retries.
                    if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext
                    if (!userPreferencesRepository.jellyfinSyncFavoritesFlow.first()) return@withContext

                    val storedAccount = prefs.getString(KEY_FAVORITE_SYNC_ACCOUNT, null)
                    if (storedAccount != null && storedAccount != accountKey) {
                        clearFavoriteSyncPrefs()
                    }

                    val pendingAdds = prefs.getStringSet(KEY_PENDING_FAVORITE_ADDS, null)
                        .orEmpty().toMutableSet()
                    val pendingRemoves = prefs.getStringSet(KEY_PENDING_FAVORITE_REMOVES, null)
                        .orEmpty().toMutableSet()

                    if (result.isSuccess) {
                        pendingAdds.remove(jellyfinId)
                        pendingRemoves.remove(jellyfinId)
                        val editor = prefs.edit()
                            .putString(KEY_FAVORITE_SYNC_ACCOUNT, accountKey)
                            .putStringSet(KEY_PENDING_FAVORITE_ADDS, pendingAdds)
                            .putStringSet(KEY_PENDING_FAVORITE_REMOVES, pendingRemoves)
                        if (prefs.getBoolean(KEY_HAS_FAVORITE_SYNC_BASELINE, false)) {
                            val baseline = prefs.getStringSet(KEY_LAST_SYNCED_FAVORITE_IDS, null)
                                .orEmpty().toMutableSet()
                            if (isFavorite) baseline.add(jellyfinId) else baseline.remove(jellyfinId)
                            editor.putStringSet(KEY_LAST_SYNCED_FAVORITE_IDS, baseline)
                        }
                        editor.apply()
                    } else {
                        Timber.w(
                            result.exceptionOrNull(),
                            "$TAG: Failed pushing favorite=$isFavorite for $jellyfinId; queueing retry"
                        )
                        if (isFavorite) {
                            pendingAdds.add(jellyfinId)
                            pendingRemoves.remove(jellyfinId)
                        } else {
                            pendingRemoves.add(jellyfinId)
                            pendingAdds.remove(jellyfinId)
                        }
                        prefs.edit()
                            .putString(KEY_FAVORITE_SYNC_ACCOUNT, accountKey)
                            .putStringSet(KEY_PENDING_FAVORITE_ADDS, pendingAdds)
                            .putStringSet(KEY_PENDING_FAVORITE_REMOVES, pendingRemoves)
                            .apply()
                    }
                }
            }
        } finally {
            inFlightLocalFavoriteMutations.remove(jellyfinId, mutationGen to isFavorite)
        }
    }

    suspend fun syncFavorites(): Result<Unit> {
        val sessionGen = accountSessionGeneration.get()
        val accountKey = currentAccountKey()
        if (!isLoggedIn || accountKey == null) return Result.success(Unit)
        val enabled = userPreferencesRepository.jellyfinSyncFavoritesFlow.first()
        if (!enabled) return Result.success(Unit)

        return favoritesSyncMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)
                    if (!userPreferencesRepository.jellyfinSyncFavoritesFlow.first()) return@withContext Result.success(Unit)

                    val storedAccount = prefs.getString(KEY_FAVORITE_SYNC_ACCOUNT, null)
                    if (storedAccount != null && storedAccount != accountKey) {
                        clearFavoriteSyncPrefs()
                    }

                    // 1. Flush pending offline favorite mutations first (only for this account).
                    val pendingAdds = prefs.getStringSet(KEY_PENDING_FAVORITE_ADDS, null)
                        .orEmpty().toMutableSet()
                    val pendingRemoves = prefs.getStringSet(KEY_PENDING_FAVORITE_REMOVES, null)
                        .orEmpty().toMutableSet()
                    val stillPending = mutableSetOf<String>()
                    val hasBaseline = prefs.getBoolean(KEY_HAS_FAVORITE_SYNC_BASELINE, false)
                    val baselineSet = if (hasBaseline) {
                        prefs.getStringSet(KEY_LAST_SYNCED_FAVORITE_IDS, null).orEmpty().toMutableSet()
                    } else {
                        null
                    }

                    pendingAdds.toList().forEach { id ->
                        if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)
                        if (inFlightLocalFavoriteMutations.containsKey(id)) {
                            stillPending.add(id)
                            return@forEach
                        }
                        if (api.markFavorite(id).isSuccess) {
                            pendingAdds.remove(id)
                            baselineSet?.add(id)
                        } else {
                            stillPending.add(id)
                        }
                    }
                    pendingRemoves.toList().forEach { id ->
                        if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)
                        if (inFlightLocalFavoriteMutations.containsKey(id)) {
                            stillPending.add(id)
                            return@forEach
                        }
                        if (api.unmarkFavorite(id).isSuccess) {
                            pendingRemoves.remove(id)
                            baselineSet?.remove(id)
                        } else {
                            stillPending.add(id)
                        }
                    }

                    // 2. Fetch all remote Jellyfin favorite audio IDs.
                    val remoteFavoriteIds = mutableSetOf<String>()
                    var startIndex = 0
                    val pageSize = 500
                    while (true) {
                        if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)
                        val page = api.getFavoriteAudioIds(startIndex = startIndex, limit = pageSize)
                        val (_, ids) = page.getOrElse { error ->
                            if (isFavoriteSessionCurrent(sessionGen, accountKey)) {
                                prefs.edit()
                                    .putString(KEY_FAVORITE_SYNC_ACCOUNT, accountKey)
                                    .putStringSet(KEY_PENDING_FAVORITE_ADDS, pendingAdds)
                                    .putStringSet(KEY_PENDING_FAVORITE_REMOVES, pendingRemoves)
                                    .apply()
                            }
                            Timber.w(error, "$TAG: Failed fetching Jellyfin favourites")
                            return@withContext Result.failure(error)
                        }
                        if (ids.isEmpty()) break
                        remoteFavoriteIds.addAll(ids)
                        startIndex += ids.size
                        if (ids.size < pageSize) break
                    }

                    if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)

                    // 3. Map synced unified Jellyfin songs and read current local favourites.
                    val syncedMappings = musicDao.getAllJellyfinSongUriMappings()
                    val jellyfinIdToSongId = HashMap<String, Long>(syncedMappings.size)
                    syncedMappings.forEach { mapping ->
                        val jellyfinId = mapping.contentUriString.removePrefix("jellyfin://")
                        if (jellyfinId.isNotBlank() && jellyfinId != mapping.contentUriString) {
                            jellyfinIdToSongId[jellyfinId] = mapping.id
                        }
                    }

                    // 3–5. Atomically snapshot local favourites + in-flight toggles, compute the
                    // diff, and apply local Room updates under localFavoritesMutex so concurrent
                    // setFavoriteStatus calls cannot interleave between read and write.
                    val (diff, agreedFavorites) = localFavoritesMutex.withLock {
                        if (!isFavoriteSessionCurrent(sessionGen, accountKey)) {
                            return@withContext Result.success(Unit)
                        }

                        val allLocalFavIds = favoritesDao.getFavoriteSongIdsOnce().toHashSet()
                        val localJellyfinFavs = jellyfinIdToSongId
                            .filterValues { songId -> songId in allLocalFavIds }
                            .keys
                            .toSet()

                        // Protect both failed offline retries and any local toggles that happened
                        // while the network request was in flight so the merge never resurrects an unlike.
                        val protectedIds = stillPending + inFlightLocalFavoriteMutations.keys

                        val computedDiff = computeJellyfinFavoriteSyncDiff(
                            knownJellyfinIds = jellyfinIdToSongId.keys,
                            localFavorites = localJellyfinFavs,
                            remoteFavorites = remoteFavoriteIds,
                            baseline = baselineSet,
                            protectedPendingIds = protectedIds
                        )

                        val agreed = computedDiff.resultingFavorites.toMutableSet()
                        computedDiff.addLocally.forEach { jellyfinId ->
                            if (inFlightLocalFavoriteMutations.containsKey(jellyfinId)) {
                                agreed.remove(jellyfinId)
                                return@forEach
                            }
                            val songId = jellyfinIdToSongId[jellyfinId] ?: return@forEach
                            favoritesDao.setFavorite(FavoritesEntity(songId = songId, isFavorite = true))
                        }
                        computedDiff.removeLocally.forEach { jellyfinId ->
                            if (inFlightLocalFavoriteMutations.containsKey(jellyfinId)) {
                                agreed.add(jellyfinId)
                                return@forEach
                            }
                            val songId = jellyfinIdToSongId[jellyfinId] ?: return@forEach
                            favoritesDao.removeFavorite(songId)
                        }
                        computedDiff to agreed
                    }

                    // 6. Push local changes to Jellyfin; queue failures for retry.
                    diff.addRemotely.forEach { jellyfinId ->
                        if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)
                        if (inFlightLocalFavoriteMutations.containsKey(jellyfinId)) return@forEach
                        if (api.markFavorite(jellyfinId).isFailure) {
                            pendingAdds.add(jellyfinId)
                            pendingRemoves.remove(jellyfinId)
                            agreedFavorites.remove(jellyfinId)
                        }
                    }
                    diff.removeRemotely.forEach { jellyfinId ->
                        if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)
                        if (inFlightLocalFavoriteMutations.containsKey(jellyfinId)) return@forEach
                        if (api.unmarkFavorite(jellyfinId).isFailure) {
                            pendingRemoves.add(jellyfinId)
                            pendingAdds.remove(jellyfinId)
                            agreedFavorites.add(jellyfinId)
                        }
                    }

                    if (!isFavoriteSessionCurrent(sessionGen, accountKey)) return@withContext Result.success(Unit)

                    prefs.edit()
                        .putString(KEY_FAVORITE_SYNC_ACCOUNT, accountKey)
                        .putBoolean(KEY_HAS_FAVORITE_SYNC_BASELINE, true)
                        .putStringSet(KEY_LAST_SYNCED_FAVORITE_IDS, agreedFavorites)
                        .putStringSet(KEY_PENDING_FAVORITE_ADDS, pendingAdds)
                        .putStringSet(KEY_PENDING_FAVORITE_REMOVES, pendingRemoves)
                        .apply()

                    Timber.d(
                        "$TAG: Favourites synced (+${diff.addLocally.size}/-${diff.removeLocally.size} local, " +
                            "+${diff.addRemotely.size}/-${diff.removeRemotely.size} remote)"
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "$TAG: Failed to sync Jellyfin favourites")
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Fetch the user's media libraries (views) from the server. Includes
     * non-music views so the picker can show them disabled.
     */
    suspend fun getLibraries(): Result<List<JellyfinLibrary>> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

        return withContext(Dispatchers.IO) {
            api.getUserViews().map { views ->
                JellyfinResponseParser.parseLibraries(views)
            }
        }
    }

    suspend fun setSelectedLibraryIds(libraryIds: Set<String>) {
        userPreferencesRepository.setJellyfinSelectedLibraryIds(
            libraryIds.filter { it.isNotBlank() }.toSet()
        )
    }

    suspend fun syncPlaylists(): Result<List<JellyfinPlaylistEntity>> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing playlists")
                val result = api.getPlaylists()
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlists")
                    )
                }

                val jsonObjects = result.getOrThrow()
                val playlists = JellyfinResponseParser.parsePlaylists(jsonObjects)

                if (playlists.isEmpty() && jsonObjects.isNotEmpty()) {
                    Timber.w("$TAG: Parser returned empty playlists but JSON had items. Aborting.")
                    return@withContext Result.failure(Exception("Playlist parsing error"))
                }

                if (playlists.isEmpty()) {
                    val localCount = dao.getPlaylistCount()
                    if (localCount > 0) {
                        Timber.w("$TAG: Server returned empty playlists but we have $localCount locally. Aborting sync.")
                        return@withContext Result.success(emptyList())
                    }
                    Timber.w("$TAG: Server reported no audio playlists for this user")
                }

                val entities = playlists.map { playlist ->
                    JellyfinPlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        songCount = playlist.songCount,
                        duration = playlist.duration,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                val stalePlaylists = if (entities.isNotEmpty() || jsonObjects.isEmpty()) {
                    localPlaylists.filter { it.id !in remoteIds }
                } else {
                    emptyList()
                }

                if (stalePlaylists.isNotEmpty()) {
                    Timber.d("$TAG: Removing ${stalePlaylists.size} stale playlists")
                    stalePlaylists.forEach { stale ->
                        writeSongCache { dao.deleteSongsByPlaylist(stale.id) }
                        dao.deletePlaylist(stale.id)
                        deleteAppPlaylistForJellyfinPlaylist(stale.id)
                    }
                }

                entities.forEach { dao.insertPlaylist(it) }

                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromJellyfin()
                }

                Timber.d("$TAG: Synced ${entities.size} playlists")
                Result.success(entities)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Failed to sync playlists")
                Result.failure(e)
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing songs for playlist $playlistId")
                val result = api.getPlaylistItems(playlistId)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlist")
                    )
                }

                val songJsons = result.getOrThrow()
                val songs = JellyfinResponseParser.parseSongs(songJsons)

                if (songs.isEmpty() && songJsons.isNotEmpty()) {
                    Timber.w("$TAG: FAILED to parse songs for playlist $playlistId. Aborting.")
                    return@withContext Result.failure(Exception("Parsing error"))
                }

                val entities = songs.map { song: JellyfinSong ->
                    song.toEntity(playlistId)
                }

                if (entities.isNotEmpty()) {
                    writeSongCache {
                        dao.deleteSongsByPlaylist(playlistId)
                        dao.insertSongs(entities)
                    }
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForJellyfinPlaylist(playlistId, playlistName, entities)
                } else if (songJsons.isEmpty()) {
                    writeSongCache { dao.deleteSongsByPlaylist(playlistId) }
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForJellyfinPlaylist(playlistId, playlistName, emptyList())
                }

                Timber.d("$TAG: Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Failed to sync playlist songs")
                Result.failure(e)
            }
        }
    }

    suspend fun syncLibrarySongs(): Result<Int> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing library songs from server")
                val allSongs = mutableListOf<JellyfinSong>()
                val pageSize = 500

                val musicLibraries = getLibraries()
                    .map { libraries -> libraries.filter { it.isMusic } }
                    .getOrElse { error ->
                        Timber.w(error, "$TAG: Failed to load libraries; falling back to all-library sync")
                        emptyList()
                    }
                val selectedLibraryIds = selectedJellyfinLibraryIds(
                    availableLibraries = musicLibraries,
                    savedLibraryIds = userPreferencesRepository.jellyfinSelectedLibraryIdsFlow.first()
                )
                val libraryFilterIds = if (
                    musicLibraries.isNotEmpty() &&
                    selectedLibraryIds.isNotEmpty() &&
                    selectedLibraryIds.size < musicLibraries.size
                ) {
                    selectedLibraryIds
                } else {
                    emptySet()
                }

                val parentIds: List<String?> =
                    if (libraryFilterIds.isEmpty()) listOf(null) else libraryFilterIds.toList()

                for (parentId in parentIds) {
                    var startIndex = 0
                    while (true) {
                        val result = api.getMusicItems(
                            startIndex = startIndex,
                            limit = pageSize,
                            parentId = parentId
                        )
                        val (_, items) = result.getOrElse { error ->
                            Timber.w(error, "$TAG: Song fetch failed, keeping cached library")
                            return@withContext Result.failure(error)
                        }
                        if (items.isEmpty()) break

                        val songs = JellyfinResponseParser.parseSongs(items)
                        allSongs.addAll(songs)
                        startIndex += items.size
                        if (items.size < pageSize) break
                    }
                }

                if (allSongs.isEmpty()) {
                    Timber.d("$TAG: No library songs found on server")
                    return@withContext Result.success(0)
                }

                val uniqueSongs = allSongs.distinctBy { it.id }
                val entities = uniqueSongs.map { song -> song.toEntity(LIBRARY_PLAYLIST_ID) }

                writeSongCache { dao.replaceLibrarySongs(entities) }

                Timber.d("$TAG: Synced ${entities.size} library songs")
                Result.success(entities.size)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Failed to sync library songs")
                Result.failure(e)
            }
        }
    }

    suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            var syncedSongCount = 0
            var failedPlaylistCount = 0

            val libResult = syncLibrarySongs()
            libResult.fold(
                onSuccess = { count -> syncedSongCount += count },
                onFailure = { Timber.w(it, "$TAG: Failed syncing library songs") }
            )

            val playlistResult = syncPlaylists().getOrElse {
                try {
                    syncUnifiedLibrarySongsFromJellyfin()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "$TAG: Failed to sync unified library after playlist fetch failure")
                }
                return@withContext Result.success(
                    BulkSyncResult(playlistCount = 0, syncedSongCount = syncedSongCount, failedPlaylistCount = 0)
                )
            }

            playlistResult.forEach { playlist ->
                val songSyncResult = syncPlaylistSongs(playlist.id)
                songSyncResult.fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "$TAG: Failed syncing playlist ${playlist.id}")
                    }
                )
            }

            val unifiedLibrarySynced = try {
                syncUnifiedLibrarySongsFromJellyfin()
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Failed to sync unified library")
                false
            }

            if (unifiedLibrarySynced) {
                syncFavorites()
            }

            // Anything short of songs reaching the shared library has the next dashboard
            // visit sync again instead of waiting out the threshold.
            if (libResult.isSuccess && failedPlaylistCount == 0 && unifiedLibrarySynced) {
                lastFullSyncTime = System.currentTimeMillis()
            }

            Result.success(
                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            )
        }
    }

    fun getPlaylists(): Flow<List<JellyfinPlaylistEntity>> = dao.getAllPlaylists()

    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        writeSongCache { dao.clearSongsByPlaylist(playlistId) }
        dao.deletePlaylist(playlistId)
        syncUnifiedLibrarySongsFromJellyfin()
    }

    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllJellyfinSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

        return withContext(Dispatchers.IO) {
            try {
                val result = api.searchSongs(query, limit)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Search failed")
                    )
                }
                val jellyfinSongs = JellyfinResponseParser.parseSongs(result.getOrThrow())
                Result.success(jellyfinSongs.map { it.toDisplaySong() })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Search failed")
                Result.failure(e)
            }
        }
    }

    fun searchLocalSongs(query: String): Flow<List<Song>> {
        return dao.searchSongs(query).map { entities ->
            entities.map { it.toSong() }
        }
    }

    fun getStreamUrl(songId: String, maxBitRate: Int = 0): String {
        return api.getStreamUrl(songId, maxBitRate)
    }

    fun getImageUrl(itemId: String?, size: Int = 500): String? {
        if (itemId.isNullOrBlank()) return null
        return api.getImageUrl(itemId, maxWidth = size)
    }

    /** Cover item id of the album [songItemId] belongs to, or null if unknown. */
    suspend fun getAlbumCoverItemId(songItemId: String): String? {
        val albumArtUri = withContext(Dispatchers.IO) {
            musicDao.getAlbumArtUriByContentUri("jellyfin://$songItemId")
        } ?: return null
        return albumArtUri.removePrefix("jellyfin_cover://")
            .takeIf { it != albumArtUri && it.isNotBlank() }
    }

    suspend fun getLyrics(songId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.getLyrics(songId)
                if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                    return@withContext result
                }
                Result.failure(Exception("No lyrics found"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "$TAG: Failed to get lyrics for song $songId")
                Result.failure(e)
            }
        }
    }

    /**
     * [syncUnifiedLibrarySongsFromJellyfin], skipped while nothing it reads has changed since the
     * last rebuild. Every sync worker run calls this, including the one queued on each cold
     * start, and a rebuild holds the whole Jellyfin library in memory twice and rewrites all of
     * its rows, which reloads the app's whole song list.
     */
    suspend fun syncUnifiedLibrarySongsFromJellyfinIfChanged() = songCacheMutex.withLock {
        val groupByAlbumArtist = userPreferencesRepository.groupByAlbumArtistFlow.first()
        val currentState = unifiedJellyfinLibraryState(
            songCacheRevision = songCacheRevision,
            groupByAlbumArtist = groupByAlbumArtist,
            appBuild = APP_BUILD,
            unifiedSongCount = musicDao.getJellyfinSongCount()
        )
        if (prefs.getString(KEY_UNIFIED_LIBRARY_STATE, null) == currentState) {
            Timber.d("$TAG: Cached songs unchanged since the last unified library rebuild")
            return@withLock
        }
        rebuildAndSaveState()
    }

    suspend fun syncUnifiedLibrarySongsFromJellyfin() = songCacheMutex.withLock {
        rebuildAndSaveState()
    }

    private suspend fun rebuildAndSaveState() {
        // Under the lock no cache write is under way, so this revision describes the rows read.
        val revision = songCacheRevision
        val groupByAlbumArtist = userPreferencesRepository.groupByAlbumArtistFlow.first()
        rebuildUnifiedLibrary(groupByAlbumArtist)
        val state = unifiedJellyfinLibraryState(
            songCacheRevision = revision,
            groupByAlbumArtist = groupByAlbumArtist,
            appBuild = APP_BUILD,
            unifiedSongCount = musicDao.getJellyfinSongCount()
        )
        prefs.edit().putString(KEY_UNIFIED_LIBRARY_STATE, state).apply()
    }

    private suspend fun rebuildUnifiedLibrary(groupByAlbumArtist: Boolean) {
        val jellyfinSongs = dao.getDistinctJellyfinSongs(LIBRARY_PLAYLIST_ID)
        val existingUnifiedIds = musicDao.getAllJellyfinSongIds()

        if (jellyfinSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllJellyfinSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(jellyfinSongs.size)
        val favoriteSongIds = favoritesDao.getFavoriteSongIdsOnce().toHashSet()
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        jellyfinSongs.forEach { jellyfinSong ->
            val songId = toUnifiedSongId(jellyfinSong.jellyfinId)
            val artistNames = parseArtistNames(jellyfinSong.artist)
            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
            val primaryArtistId = toUnifiedArtistId(primaryArtistName)

            val effectiveAlbumArtistName = jellyfinSong.albumArtist
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: primaryArtistName
            val albumArtistId = toUnifiedArtistId(effectiveAlbumArtistName)
            artists.putIfAbsent(
                albumArtistId,
                ArtistEntity(
                    id = albumArtistId,
                    name = effectiveAlbumArtistName,
                    trackCount = 0,
                    imageUrl = null
                )
            )
            val albumDisplayArtistName = if (groupByAlbumArtist) effectiveAlbumArtistName else primaryArtistName
            val albumDisplayArtistId = if (groupByAlbumArtist) albumArtistId else primaryArtistId

            artistNames.forEachIndexed { index, artistName ->
                val artistId = toUnifiedArtistId(artistName)
                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(
                        id = artistId,
                        name = artistName,
                        trackCount = 0,
                        imageUrl = null
                    )
                )
                crossRefs.add(
                    SongArtistCrossRef(
                        songId = songId,
                        artistId = artistId,
                        isPrimary = index == 0
                    )
                )
            }

            val albumId = toUnifiedAlbumId(jellyfinSong.albumId, jellyfinSong.album)
            val albumName = jellyfinSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = albumDisplayArtistName,
                    artistId = albumDisplayArtistId,
                    songCount = 0,
                    dateAdded = jellyfinSong.dateAdded,
                    year = jellyfinSong.year,
                    albumArtUriString = "jellyfin_cover://${jellyfinSong.jellyfinId}",
                    albumArtist = jellyfinSong.albumArtist?.takeIf { it.isNotBlank() }
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = jellyfinSong.title,
                    artistName = jellyfinSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = jellyfinSong.albumArtist?.takeIf { it.isNotBlank() },
                    albumArtistId = albumArtistId,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "jellyfin://${jellyfinSong.jellyfinId}",
                    albumArtUriString = "jellyfin_cover://${jellyfinSong.jellyfinId}",
                    duration = jellyfinSong.duration,
                    genre = jellyfinSong.genre ?: JELLYFIN_GENRE,
                    filePath = jellyfinSong.path,
                    parentDirectoryPath = JELLYFIN_PARENT_DIRECTORY,
                    isFavorite = songId in favoriteSongIds,
                    lyrics = null,
                    trackNumber = jellyfinSong.trackNumber,
                    discNumber = CloudMusicUtils.normalizeDiscNumber(jellyfinSong.discNumber),
                    year = jellyfinSong.year,
                    dateAdded = jellyfinSong.dateAdded.takeIf { it > 0 }
                        ?: System.currentTimeMillis(),
                    mimeType = jellyfinSong.mimeType,
                    bitrate = jellyfinSong.bitRate?.let { it * 1000 },
                    sampleRate = null,
                    sourceType = SourceType.JELLYFIN
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedIds = songs.map { it.id }.toSet()
        val deletedUnifiedIds = existingUnifiedIds.filter { it !in currentUnifiedIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedIds
        )
    }

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    private fun toUnifiedSongId(jellyfinId: String): Long {
        return -(JELLYFIN_SONG_ID_OFFSET + jellyfinId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: String?, albumName: String): Long {
        val normalized = if (!albumId.isNullOrBlank()) {
            albumId.hashCode().toLong().absoluteValue
        } else {
            albumName.lowercase().hashCode().toLong().absoluteValue
        }
        return -(JELLYFIN_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(JELLYFIN_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    private suspend fun updateAppPlaylistForJellyfinPlaylist(
        jellyfinPlaylistId: String,
        playlistName: String,
        songs: List<JellyfinSongEntity>
    ) {
        val appPlaylistId = "$JELLYFIN_PLAYLIST_PREFIX$jellyfinPlaylistId"
        val songIds = songs.map { toUnifiedSongId(it.jellyfinId).toString() }

        val existingPlaylist = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow.map { playlists ->
                playlists.find { it.id == appPlaylistId }
            }.first()
        }

        if (existingPlaylist != null) {
            playlistPreferencesRepository.updatePlaylist(
                existingPlaylist.copy(
                    name = playlistName,
                    songIds = songIds,
                    lastModified = System.currentTimeMillis(),
                    source = "JELLYFIN"
                )
            )
        } else {
            playlistPreferencesRepository.createPlaylist(
                name = playlistName,
                songIds = songIds,
                customId = appPlaylistId,
                source = "JELLYFIN"
            )
        }
    }

    private suspend fun deleteAppPlaylistForJellyfinPlaylist(jellyfinPlaylistId: String) {
        val appPlaylistId = "$JELLYFIN_PLAYLIST_PREFIX$jellyfinPlaylistId"
        playlistPreferencesRepository.deletePlaylist(appPlaylistId)
    }

    private fun JellyfinSong.toDisplaySong(): Song {
        return Song(
            id = "jellyfin_search_$id",
            title = title,
            artist = artist,
            artistId = -1L,
            album = album,
            albumId = -1L,
            path = path,
            contentUriString = "jellyfin://$id",
            albumArtUriString = "jellyfin_cover://$id",
            duration = duration,
            genre = genre,
            mimeType = resolvedMimeType,
            bitrate = bitRate?.let { it * 1000 },
            sampleRate = null,
            year = year,
            trackNumber = trackNumber,
            discNumber = CloudMusicUtils.normalizeDiscNumber(discNumber),
            dateAdded = 0,
            isFavorite = false
        )
    }
}

internal const val JELLYFIN_SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L

/** A new build may map cached songs into the unified library differently, so it rebuilds once. */
private val APP_BUILD = "${BuildConfig.VERSION_CODE}-${BuildConfig.VERSION_NAME}"

/**
 * What a unified-library rebuild depends on: the cached songs (through their write count), the
 * album-artist grouping, the app build, and how many Jellyfin songs the unified library holds,
 * since other code can remove them. Equal states mean a rebuild would change nothing.
 */
internal fun unifiedJellyfinLibraryState(
    songCacheRevision: Long,
    groupByAlbumArtist: Boolean,
    appBuild: String,
    unifiedSongCount: Int
): String = "$songCacheRevision|$groupByAlbumArtist|$appBuild|$unifiedSongCount"

/**
 * Whether opening the Jellyfin dashboard should sync again. A full sync of a large library
 * can take most of an hour and holds the whole library in memory, so it runs at most daily
 * on its own; the dashboard's sync button still forces one.
 */
internal fun isJellyfinFullSyncDue(lastFullSyncMs: Long, nowMs: Long): Boolean =
    nowMs - lastFullSyncMs > JELLYFIN_SYNC_THRESHOLD_MS

internal fun selectedJellyfinLibraryIds(
    availableLibraries: List<JellyfinLibrary>,
    savedLibraryIds: Set<String>
): Set<String> {
    val availableIds = availableLibraries.map { it.id }.filter { it.isNotBlank() }.toSet()
    if (availableIds.isEmpty()) return emptySet()

    val validSavedIds = savedLibraryIds.intersect(availableIds)
    return validSavedIds.ifEmpty { availableIds }
}

internal data class JellyfinFavoriteSyncDiff(
    val addLocally: Set<String>,
    val removeLocally: Set<String>,
    val addRemotely: Set<String>,
    val removeRemotely: Set<String>,
    val resultingFavorites: Set<String>
)

internal fun computeJellyfinFavoriteSyncDiff(
    knownJellyfinIds: Set<String>,
    localFavorites: Set<String>,
    remoteFavorites: Set<String>,
    baseline: Set<String>?,
    protectedPendingIds: Set<String> = emptySet()
): JellyfinFavoriteSyncDiff {
    val scopedLocal = localFavorites.intersect(knownJellyfinIds)
    val scopedRemote = remoteFavorites.intersect(knownJellyfinIds)

    if (baseline == null) {
        val addLocally = (scopedRemote - scopedLocal) - protectedPendingIds
        val addRemotely = (scopedLocal - scopedRemote) - protectedPendingIds
        val resulting = (scopedLocal + addLocally)
        return JellyfinFavoriteSyncDiff(
            addLocally = addLocally,
            removeLocally = emptySet(),
            addRemotely = addRemotely,
            removeRemotely = emptySet(),
            resultingFavorites = resulting
        )
    }

    val scopedBaseline = baseline.intersect(knownJellyfinIds)
    val addedRemotely = (scopedRemote - scopedBaseline) - protectedPendingIds
    val removedRemotely = (scopedBaseline - scopedRemote) - protectedPendingIds
    val addedLocally = (scopedLocal - scopedBaseline) - protectedPendingIds
    val removedLocally = (scopedBaseline - scopedLocal) - protectedPendingIds

    val addLocally = addedRemotely - scopedLocal
    val removeLocally = (removedRemotely.intersect(scopedLocal)) - addedLocally
    val addRemotely = addedLocally - scopedRemote
    val removeRemotely = (removedLocally.intersect(scopedRemote)) - addedRemotely

    val resulting = ((scopedLocal + addLocally) - removeLocally)
    return JellyfinFavoriteSyncDiff(
        addLocally = addLocally,
        removeLocally = removeLocally,
        addRemotely = addRemotely,
        removeRemotely = removeRemotely,
        resultingFavorites = resulting
    )
}
