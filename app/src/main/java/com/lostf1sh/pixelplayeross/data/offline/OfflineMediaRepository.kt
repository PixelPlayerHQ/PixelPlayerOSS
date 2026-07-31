@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lostf1sh.pixelplayeross.data.offline

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.withTransaction
import com.lostf1sh.pixelplayeross.data.database.CachedCollectionEntity
import com.lostf1sh.pixelplayeross.data.database.CachedCollectionTrackCrossRef
import com.lostf1sh.pixelplayeross.data.database.CachedTrackEntity
import com.lostf1sh.pixelplayeross.data.database.OfflineMediaDao
import com.lostf1sh.pixelplayeross.data.database.PixelPlayerDatabase
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinStreamProxy
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeStreamProxy
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.service.player.PlaybackAudioCache
import com.lostf1sh.pixelplayeross.data.service.player.MAX_TRANSIENT_PLAYBACK_CACHE_BYTES
import com.lostf1sh.pixelplayeross.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

enum class CachedCollectionType { SONG, ALBUM, PLAYLIST }

enum class OfflineItemStatus { QUEUED, DOWNLOADING, COMPLETE, PAUSED, FAILED }

data class CachedCollection(
    val id: String,
    val type: CachedCollectionType,
    val sourceId: String,
    val title: String,
    val subtitle: String?,
    val artworkUri: String?,
    val songs: List<Song>,
    val status: OfflineItemStatus,
    val progress: Float,
    val downloadedBytes: Long,
)

data class OfflineMediaUiState(
    val collections: List<CachedCollection> = emptyList(),
    val offlineBytes: Long = 0L,
    val transientBytes: Long = 0L,
    val transientLimitBytes: Long = MAX_TRANSIENT_PLAYBACK_CACHE_BYTES,
    val limitBytes: Long = UserPreferencesRepository.DEFAULT_OFFLINE_CACHE_LIMIT_BYTES,
)

sealed interface OfflineCacheRequestResult {
    data object Accepted : OfflineCacheRequestResult
    data object AlreadyCached : OfflineCacheRequestResult
    data class LimitExceeded(val requiredBytes: Long, val availableBytes: Long) : OfflineCacheRequestResult
    data class LowStorage(val requiredBytes: Long, val availableBytes: Long) : OfflineCacheRequestResult
    data object NoRemoteMedia : OfflineCacheRequestResult
}

internal const val OFFLINE_STOP_REASON_QUOTA = 1001
internal const val OFFLINE_STOP_REASON_LOW_STORAGE = 1002
private const val MIN_FREE_BYTES = 1024L * 1024L * 1024L

internal fun estimateOfflineBytes(songs: List<Song>): Long = songs.sumOf { song ->
    val bitrate = song.bitrate?.takeIf { it > 0 } ?: 320_000
    val durationMs = song.duration.coerceAtLeast(60_000L)
    ((bitrate.toLong() * durationMs) / 8_000L * 11L / 10L).coerceAtLeast(1L)
}

internal fun availableForOfflineCache(limitBytes: Long, usedBytes: Long): Long =
    if (limitBytes == 0L) Long.MAX_VALUE else (limitBytes - usedBytes).coerceAtLeast(0L)

@OptIn(UnstableApi::class)
@Singleton
class OfflineMediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: PixelPlayerDatabase,
    private val dao: OfflineMediaDao,
    private val playbackAudioCache: PlaybackAudioCache,
    private val preferences: UserPreferencesRepository,
    private val navidromeStreamProxy: NavidromeStreamProxy,
    private val jellyfinStreamProxy: JellyfinStreamProxy,
    @param:AppScope private val appScope: CoroutineScope,
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutationMutex = Mutex()
    private val downloadSnapshot = MutableStateFlow<Map<String, Download>>(emptyMap())
    private val downloadListener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            mainScope.launch { refreshDownloads() }
        }

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                runCatching { playbackAudioCache.removeTransientResource(download.request.id) }
                    .onFailure { Timber.w(it, "Could not trim transient copy for %s", download.request.id) }
            }
            finalException?.let { Timber.w(it, "Offline media cache failed for %s", download.request.id) }
            mainScope.launch { refreshDownloads() }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            mainScope.launch { refreshDownloads() }
        }
    }
    private val metadataReady = appScope.async {
        val marker = context.filesDir.resolve("offline_cache_metadata_v1")
        if (!marker.exists()) {
            database.withTransaction {
                dao.clearCollections()
                dao.clearTracks()
            }
            marker.writeText("device-local")
        }
    }

    val downloadManager: DownloadManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val directFactory = DefaultDataSource.Factory(context)
        val resolvingFactory = ResolvingDataSource.Factory(directFactory) { dataSpec ->
            val original = dataSpec.uri.toString()
            val resolved = when (dataSpec.uri.scheme?.lowercase()) {
                "navidrome" -> runBlocking(Dispatchers.IO) {
                    navidromeStreamProxy.ensureReady()
                    navidromeStreamProxy.warmUpStreamUrl(original)
                    navidromeStreamProxy.resolveNavidromeUri(original)
                }
                "jellyfin" -> runBlocking(Dispatchers.IO) {
                    jellyfinStreamProxy.ensureReady()
                    jellyfinStreamProxy.warmUpStreamUrl(original)
                    jellyfinStreamProxy.resolveJellyfinUri(original)
                }
                else -> null
            }
            resolved?.let { dataSpec.buildUpon().setUri(it).build() } ?: dataSpec
        }
        DownloadManager(
            context,
            playbackAudioCache.media3DatabaseProvider,
            playbackAudioCache.offlineCache,
            playbackAudioCache.createDownloadUpstreamFactory(resolvingFactory),
            Executors.newFixedThreadPool(2),
        ).apply {
            maxParallelDownloads = 2
            addListener(downloadListener)
        }
    }

    val uiState: StateFlow<OfflineMediaUiState> = combine(
        dao.observeCollections(),
        dao.observeTracks(),
        dao.observeCrossRefs(),
        downloadSnapshot,
        preferences.offlineCacheLimitBytesFlow,
    ) { collections, tracks, refs, downloads, limit ->
        val tracksById = tracks.associateBy(CachedTrackEntity::trackId)
        val refsByCollection = refs.groupBy(CachedCollectionTrackCrossRef::collectionId)
        OfflineMediaUiState(
            collections = collections.map { collection ->
                val collectionTracks = refsByCollection[collection.collectionId]
                    .orEmpty()
                    .sortedBy(CachedCollectionTrackCrossRef::sortOrder)
                    .mapNotNull { tracksById[it.trackId] }
                collection.toModel(collectionTracks, downloads)
            },
            offlineBytes = playbackAudioCache.offlineCacheBytes,
            transientBytes = playbackAudioCache.transientCacheBytes,
            limitBytes = limit,
        )
    }.stateIn(appScope, SharingStarted.Eagerly, OfflineMediaUiState())

    init {
        // Repository injection happens from an Application/UI/service main thread. Constructing
        // Media3's database and caches performs disk I/O, so warm the manager on AppScope's IO
        // dispatcher before the polling loop needs it.
        appScope.launch {
            downloadManager
            refreshDownloads()
            launch {
                preferences.offlineCacheLimitBytesFlow.collect {
                    enforceRuntimeLimits()
                }
            }
            while (true) {
                val active = downloadSnapshot.value.values.any {
                    it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED ||
                        it.state == Download.STATE_RESTARTING
                }
                delay(if (active) 750L else 5_000L)
                refreshDownloads()
                enforceRuntimeLimits()
            }
        }
    }

    fun collectionId(type: CachedCollectionType, sourceId: String, songs: List<Song>): String {
        val identity = songs.mapNotNull { song ->
            playbackAudioCache.sourceScopeFor(Uri.parse(song.contentUriString))
        }.distinct().sorted().ifEmpty { listOf("local") }.joinToString("\n")
        return "${type.name.lowercase()}:" + sha256("${type.name}\n$sourceId\n$identity")
    }

    fun isCollectionCached(type: CachedCollectionType, sourceId: String, songs: List<Song>): Boolean {
        val id = collectionId(type, sourceId, songs)
        return uiState.value.collections.any { it.id == id }
    }

    suspend fun cacheCollection(
        type: CachedCollectionType,
        sourceId: String,
        title: String,
        subtitle: String?,
        artworkUri: String?,
        songs: List<Song>,
    ): OfflineCacheRequestResult = mutationMutex.withLock {
        cacheCollectionLocked(type, sourceId, title, subtitle, artworkUri, songs)
    }

    private suspend fun cacheCollectionLocked(
        type: CachedCollectionType,
        sourceId: String,
        title: String,
        subtitle: String?,
        artworkUri: String?,
        songs: List<Song>,
    ): OfflineCacheRequestResult {
        metadataReady.await()
        if (songs.isEmpty()) return OfflineCacheRequestResult.NoRemoteMedia
        val collectionId = collectionId(type, sourceId, songs)
        if (uiState.value.collections.any { it.id == collectionId }) {
            return OfflineCacheRequestResult.AlreadyCached
        }

        val now = System.currentTimeMillis()
        val trackEntities = songs.map { song -> song.toCachedTrack(now) }
        val newRemoteTracks = trackEntities.filter { track ->
            val existing = downloadSnapshot.value[track.trackId]
            track.isRemote &&
                !playbackAudioCache.isAvailableOffline(track.cacheKey) &&
                (existing == null || existing.state == Download.STATE_FAILED)
        }
        val estimate = estimateOfflineBytes(
            songs.filter { song -> newRemoteTracks.any { it.songId == song.id } }
        )
        val limit = uiState.value.limitBytes
        val availableByLimit = availableForOfflineCache(limit, playbackAudioCache.offlineCacheBytes)
        if (estimate > availableByLimit) {
            return OfflineCacheRequestResult.LimitExceeded(estimate, availableByLimit)
        }
        val stat = StatFs(context.filesDir.path)
        val diskAvailable = stat.availableBytes
        val reserve = maxOf(MIN_FREE_BYTES, stat.totalBytes / 20L)
        val usableDisk = (diskAvailable - reserve).coerceAtLeast(0L)
        if (estimate > usableDisk) {
            return OfflineCacheRequestResult.LowStorage(estimate, usableDisk)
        }

        database.withTransaction {
            dao.upsertCollection(
                CachedCollectionEntity(
                    collectionId = collectionId,
                    collectionType = type.name,
                    sourceId = sourceId,
                    title = title,
                    subtitle = subtitle,
                    artworkUri = artworkUri,
                    createdAtMs = now,
                )
            )
            dao.upsertTracks(trackEntities)
            dao.upsertCrossRefs(trackEntities.mapIndexed { index, track ->
                CachedCollectionTrackCrossRef(collectionId, track.trackId, index)
            })
        }

        newRemoteTracks.forEach { track ->
            val request = DownloadRequest.Builder(track.trackId, Uri.parse(track.contentUri))
                .setMimeType(track.mimeType)
                .setCustomCacheKey(track.cacheKey)
                .build()
            DownloadService.sendAddDownload(
                context,
                OfflineMediaDownloadService::class.java,
                request,
                true,
            )
        }
        return OfflineCacheRequestResult.Accepted
    }

    suspend fun removeCollection(collectionId: String) {
        mutationMutex.withLock { removeCollectionLocked(collectionId) }
    }

    private suspend fun removeCollectionLocked(collectionId: String) {
        metadataReady.await()
        val orphaned = database.withTransaction {
            val tracks = dao.tracksExclusiveToCollection(collectionId)
            dao.deleteTracksExclusiveToCollection(collectionId)
            dao.deleteCollection(collectionId)
            tracks
        }
        orphaned.filter(CachedTrackEntity::isRemote).forEach { track ->
            DownloadService.sendRemoveDownload(
                context,
                OfflineMediaDownloadService::class.java,
                track.trackId,
                false,
            )
        }
    }

    fun resumePendingDownloads() {
        appScope.launch {
            val hasPending = downloadManager.downloadIndex.getDownloads(
                Download.STATE_QUEUED,
                Download.STATE_STOPPED,
                Download.STATE_DOWNLOADING,
                Download.STATE_RESTARTING,
            ).use { it.moveToNext() }
            if (hasPending) {
                mainScope.launch {
                    enforceRuntimeLimits()
                    DownloadService.startForeground(context, OfflineMediaDownloadService::class.java)
                }
            }
        }
    }

    private suspend fun refreshDownloads() {
        appScope.launch {
            val downloads = buildMap {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) put(cursor.download.request.id, cursor.download)
                }
            }
            downloadSnapshot.value = downloads
        }.join()
    }

    private fun enforceRuntimeLimits() {
        mainScope.launch {
            val limit = uiState.value.limitBytes
            val stat = StatFs(context.filesDir.path)
            val reserve = maxOf(MIN_FREE_BYTES, stat.totalBytes / 20L)
            val stopReason = when {
                stat.availableBytes <= reserve -> OFFLINE_STOP_REASON_LOW_STORAGE
                limit != 0L && playbackAudioCache.offlineCacheBytes >= limit -> OFFLINE_STOP_REASON_QUOTA
                else -> Download.STOP_REASON_NONE
            }
            var resumedManagedDownload = false
            downloadManager.currentDownloads.forEach { download ->
                val quotaManaged = download.stopReason == OFFLINE_STOP_REASON_QUOTA ||
                    download.stopReason == OFFLINE_STOP_REASON_LOW_STORAGE
                if (stopReason != Download.STOP_REASON_NONE || quotaManaged) {
                    downloadManager.setStopReason(download.request.id, stopReason)
                    resumedManagedDownload = resumedManagedDownload ||
                        (stopReason == Download.STOP_REASON_NONE && quotaManaged)
                }
            }
            if (
                resumedManagedDownload &&
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                DownloadService.startForeground(context, OfflineMediaDownloadService::class.java)
            }
        }
    }

    private fun Song.toCachedTrack(now: Long): CachedTrackEntity {
        val cacheKey = playbackAudioCache.cacheKeyFor(Uri.parse(contentUriString))
        return CachedTrackEntity(
            trackId = cacheKey ?: "local:${sha256(contentUriString)}",
            cacheKey = cacheKey,
            songId = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            albumArtist = albumArtist,
            path = path,
            contentUri = contentUriString,
            artworkUri = albumArtUriString,
            durationMs = duration,
            genre = genre,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            dateAdded = dateAdded,
            dateModified = dateModified,
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            navidromeId = navidromeId,
            jellyfinId = jellyfinId,
            isRemote = cacheKey != null,
            addedAtMs = now,
        )
    }
}

private fun CachedCollectionEntity.toModel(
    tracks: List<CachedTrackEntity>,
    downloads: Map<String, Download>,
): CachedCollection {
    val remoteDownloads = tracks.filter(CachedTrackEntity::isRemote).mapNotNull { downloads[it.trackId] }
    val status = offlineItemStatus(
        downloadStates = remoteDownloads.map(Download::state),
        remoteTrackCount = tracks.count(CachedTrackEntity::isRemote),
    )
    val progress = if (remoteDownloads.isEmpty()) {
        1f
    } else {
        remoteDownloads.map { download ->
            when {
                download.state == Download.STATE_COMPLETED -> 1f
                download.percentDownloaded >= 0f -> download.percentDownloaded / 100f
                else -> 0f
            }
        }.average().toFloat()
    }
    return CachedCollection(
        id = collectionId,
        type = runCatching { CachedCollectionType.valueOf(collectionType) }
            .getOrDefault(CachedCollectionType.SONG),
        sourceId = sourceId,
        title = title,
        subtitle = subtitle,
        artworkUri = artworkUri,
        songs = tracks.map(CachedTrackEntity::toSong),
        status = status,
        progress = progress.coerceIn(0f, 1f),
        downloadedBytes = remoteDownloads.sumOf { it.bytesDownloaded },
    )
}

internal fun offlineItemStatus(
    downloadStates: List<Int>,
    remoteTrackCount: Int,
): OfflineItemStatus = when {
    downloadStates.any { it == Download.STATE_FAILED } -> OfflineItemStatus.FAILED
    downloadStates.any { it == Download.STATE_REMOVING } -> OfflineItemStatus.QUEUED
    downloadStates.any { it == Download.STATE_DOWNLOADING } -> OfflineItemStatus.DOWNLOADING
    downloadStates.any { it == Download.STATE_STOPPED } -> OfflineItemStatus.PAUSED
    downloadStates.any { it == Download.STATE_QUEUED || it == Download.STATE_RESTARTING } ->
        OfflineItemStatus.QUEUED
    downloadStates.size == remoteTrackCount &&
        downloadStates.all { it == Download.STATE_COMPLETED } -> OfflineItemStatus.COMPLETE
    else -> OfflineItemStatus.QUEUED
}

private fun CachedTrackEntity.toSong(): Song = Song(
    id = songId,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    albumArtist = albumArtist,
    path = path,
    contentUriString = contentUri,
    albumArtUriString = artworkUri,
    duration = durationMs,
    genre = genre,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    dateAdded = dateAdded,
    dateModified = dateModified,
    mimeType = mimeType,
    bitrate = bitrate,
    sampleRate = sampleRate,
    navidromeId = navidromeId,
    jellyfinId = jellyfinId,
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
