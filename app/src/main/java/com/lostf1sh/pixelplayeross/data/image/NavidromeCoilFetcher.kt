package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import coil.size.Size
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal fun String.withNavidromeCoverSize(sizePx: Int): String {
    if (!startsWith("navidrome_cover://") && !startsWith("jellyfin_cover://")) return this
    val uri = substringBefore('#')
    val query = uri.substringAfter('?', "").split('&')
        .filter { it.isNotEmpty() && it.substringBefore('=') != "size" } + "size=$sizePx"
    return uri.substringBefore('?') + "?" + query.joinToString("&") + substring(uri.length)
}

/**
 * Resolves the server cover size to request for cloud artwork.
 *
 * Explicit `?size=` parameters win (`0` requests the server's original image). Otherwise, Coil's
 * requested [Size] is mapped into stable cache buckets (`500`, `800`, or `0` for original quality)
 * so hero headers and high-DPI views do not upscale 500px server-recompressed thumbnails.
 */
internal fun resolveCloudCoverSize(explicitSizeParam: Int?, requestedSize: Size?): Int {
    if (explicitSizeParam != null) return explicitSizeParam.coerceAtLeast(0)
    if (requestedSize == null) return RemoteArtworkCache.DEFAULT_COVER_SIZE_PX
    if (requestedSize == Size.ORIGINAL) return 0

    val widthPx = (requestedSize.width as? Dimension.Pixels)?.px ?: 0
    val heightPx = (requestedSize.height as? Dimension.Pixels)?.px ?: 0
    val maxPx = maxOf(widthPx, heightPx)
    if (maxPx <= 0) return 0
    return when {
        maxPx <= RemoteArtworkCache.DEFAULT_COVER_SIZE_PX -> RemoteArtworkCache.DEFAULT_COVER_SIZE_PX
        maxPx <= 800 -> 800
        else -> 0
    }
}

/**
 * Custom Coil Fetcher for Navidrome album art.
 * Handles URIs in format: navidrome_cover://coverArtId
 *
 * Converts the cover art ID to a full HTTP URL using the Navidrome API
 * and downloads the image to a local cache.
 */
class NavidromeCoilFetcher(
    private val uri: Uri,
    private val repository: NavidromeRepository,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File,
    private val requestedSize: Size? = null
) : Fetcher {

    companion object {
        private const val TAG = "NavidromeCoilFetcher"
        private val recentlyLoggedFailures = ConcurrentHashMap<String, Long>()
        private const val LOG_FAILURE_COOLDOWN_MS = 60_000L

        private fun shouldLogFailure(key: String): Boolean {
            val now = System.currentTimeMillis()
            val lastLogged = recentlyLoggedFailures[key]
            return if (lastLogged == null || now - lastLogged > LOG_FAILURE_COOLDOWN_MS) {
                recentlyLoggedFailures[key] = now
                if (recentlyLoggedFailures.size > 100) {
                    recentlyLoggedFailures.entries.removeIf { now - it.value > LOG_FAILURE_COOLDOWN_MS }
                }
                true
            } else {
                false
            }
        }
    }

    override suspend fun fetch(): FetchResult? {
        Timber.v("$TAG: Fetching $uri")

        val coverArtId = uri.host ?: uri.path?.removePrefix("/")
        if (coverArtId.isNullOrBlank()) {
            Timber.w("$TAG: Invalid URI format: $uri")
            return null
        }

        if (!repository.isLoggedIn) {
            Timber.v("$TAG: Not logged in, skipping fetch")
            return null
        }

        val sizeParam = resolveCloudCoverSize(
            explicitSizeParam = uri.getQueryParameter("size")?.toIntOrNull(),
            requestedSize = requestedSize
        )

        val cachedFile = File(
            cacheDir,
            RemoteArtworkCache.cacheFileName("navidrome_cover", coverArtId, sizeParam)
        )
        RemoteArtworkCache.cachedResult(cachedFile)?.let { return it }

        val coverArtUrl = repository.getCoverArtUrl(coverArtId, sizeParam)
        if (coverArtUrl.isNullOrBlank()) {
            if (shouldLogFailure("no_url_$coverArtId")) {
                Timber.w("$TAG: No cover art URL for $coverArtId")
            }
            return null
        }

        return try {
            val request = Request.Builder().url(coverArtUrl).get().build()
            RemoteArtworkCache.download(okHttpClient, request, cachedFile)
        } catch (e: Exception) {
            // A cancelled fetch (its list item scrolled away) is not a failed download.
            if (e is CancellationException) throw e
            if (shouldLogFailure("download_$coverArtId")) {
                Timber.w(e, "$TAG: Failed to download cover art for $coverArtId")
            }
            null
        }
    }

    /**
     * Factory for creating NavidromeCoilFetcher instances.
     * Registered with Coil's ImageLoader to handle navidrome_cover:// URIs.
     */
    class Factory @Inject constructor(
        private val repository: NavidromeRepository,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<Uri> {

        private var cacheDir: File? = null

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "navidrome_cover") {
                val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
                NavidromeCoilFetcher(data, repository, okHttpClient, cache, options.size)
            } else {
                null
            }
        }
    }
}
