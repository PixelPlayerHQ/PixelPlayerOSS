package com.lostf1sh.pixelplayeross.data.service.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal const val PLAYBACK_CACHE_KEY_PREFIX = "pixelplayer:audio:v1:"
internal const val ORIGINAL_CLOUD_URI_EXTRA =
    "com.lostf1sh.pixelplayeross.playback.ORIGINAL_CLOUD_URI"
const val MAX_TRANSIENT_PLAYBACK_CACHE_BYTES = 256L * 1024L * 1024L

internal fun buildPlaybackCacheKey(sourceIdentity: String, mediaUri: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$sourceIdentity\n$mediaUri".toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return PLAYBACK_CACHE_KEY_PREFIX + digest
}

internal fun shouldUsePlaybackCache(cacheKey: String?): Boolean =
    cacheKey?.startsWith(PLAYBACK_CACHE_KEY_PREFIX) == true

/**
 * Process-wide caches for cloud audio.
 *
 * Normal streaming fills a small LRU cache. User-selected offline media is kept in a separate,
 * non-evicting cache under filesDir. Playback reads the explicit cache first, then the transient
 * cache, then the network. Local MediaStore/file playback never passes through either cache.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackAudioCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
) {
    private companion object {
        private const val TRANSIENT_CACHE_DIRECTORY = "playback_audio"
        private const val OFFLINE_CACHE_DIRECTORY = "cached_media"
    }

    private val databaseProvider by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        StandaloneDatabaseProvider(context)
    }

    private val transientCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimpleCache(
            context.cacheDir.resolve(TRANSIENT_CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(MAX_TRANSIENT_PLAYBACK_CACHE_BYTES),
            databaseProvider,
        )
    }

    val offlineCache: SimpleCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimpleCache(
            context.filesDir.resolve(OFFLINE_CACHE_DIRECTORY),
            NoOpCacheEvictor(),
            databaseProvider,
        )
    }

    val media3DatabaseProvider: StandaloneDatabaseProvider
        get() = databaseProvider

    val transientCacheBytes: Long
        get() = transientCache.cacheSpace

    val offlineCacheBytes: Long
        get() = offlineCache.cacheSpace

    fun cacheKeyFor(uri: Uri): String? {
        val sourceIdentity = sourceIdentityFor(uri) ?: return null
        return buildPlaybackCacheKey(sourceIdentity, uri.toString())
    }

    fun sourceScopeFor(uri: Uri): String? {
        val sourceIdentity = sourceIdentityFor(uri) ?: return null
        return buildPlaybackCacheKey(sourceIdentity, "collection-scope")
    }

    private fun sourceIdentityFor(uri: Uri): String? {
        return when (uri.scheme?.lowercase()) {
            "navidrome" -> sourceIdentity(
                provider = "navidrome",
                serverUrl = navidromeRepository.serverUrl,
                username = navidromeRepository.username,
            )
            "jellyfin" -> sourceIdentity(
                provider = "jellyfin",
                serverUrl = jellyfinRepository.serverUrl,
                username = jellyfinRepository.username,
            )
            else -> null
        }
    }

    fun withCacheKey(mediaItem: MediaItem): MediaItem {
        val localConfiguration = mediaItem.localConfiguration ?: return mediaItem
        val originalUri = mediaItem.mediaMetadata.extras
            ?.getString(ORIGINAL_CLOUD_URI_EXTRA)
            ?.let(Uri::parse)
            ?: localConfiguration.uri
        val cacheKey = cacheKeyFor(originalUri) ?: return mediaItem

        val metadataExtras = Bundle(mediaItem.mediaMetadata.extras ?: Bundle()).apply {
            putString(ORIGINAL_CLOUD_URI_EXTRA, originalUri.toString())
        }
        return mediaItem.buildUpon()
            .setCustomCacheKey(cacheKey)
            .setMediaMetadata(
                mediaItem.mediaMetadata.buildUpon()
                    .setExtras(metadataExtras)
                    .build()
            )
            .build()
    }

    fun createDataSourceFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        val transientFactory = CacheDataSource.Factory()
            .setCache(transientCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val offlineFirstFactory = CacheDataSource.Factory()
            .setCache(offlineCache)
            .setUpstreamDataSourceFactory(transientFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DataSource.Factory {
            CacheRoutingDataSource(
                directDataSource = upstreamFactory.createDataSource(),
                cacheDataSource = offlineFirstFactory.createDataSource(),
            )
        }
    }

    /** Reuses already-streamed bytes while copying a user-requested item to the offline cache. */
    fun createDownloadUpstreamFactory(upstreamFactory: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(transientCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun removeTransientResource(cacheKey: String) {
        transientCache.removeResource(cacheKey)
    }

    fun isAvailableOffline(cacheKey: String?): Boolean {
        if (!shouldUsePlaybackCache(cacheKey)) return false
        val contentLength = ContentMetadata.getContentLength(offlineCache.getContentMetadata(cacheKey!!))
        return contentLength != C.LENGTH_UNSET.toLong() &&
            contentLength > 0L &&
            offlineCache.isCached(cacheKey, 0L, contentLength)
    }

    private fun sourceIdentity(
        provider: String,
        serverUrl: String?,
        username: String?,
    ): String? {
        val normalizedServerUrl = serverUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: return null
        val normalizedUsername = username?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$provider|$normalizedServerUrl|$normalizedUsername"
    }
}

@OptIn(UnstableApi::class)
private class CacheRoutingDataSource(
    private val directDataSource: DataSource,
    private val cacheDataSource: DataSource,
) : DataSource {
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        directDataSource.addTransferListener(transferListener)
        cacheDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(activeDataSource == null) { "DataSource is already open" }
        val selectedDataSource = if (shouldUsePlaybackCache(dataSpec.key)) {
            cacheDataSource
        } else {
            directDataSource
        }
        activeDataSource = selectedDataSource
        return selectedDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource?.read(buffer, offset, length)
            ?: throw IOException("DataSource is not open")
    }

    override fun getUri(): Uri? = activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        activeDataSource?.responseHeaders.orEmpty()

    override fun close() {
        try {
            activeDataSource?.close()
        } finally {
            activeDataSource = null
        }
    }
}
