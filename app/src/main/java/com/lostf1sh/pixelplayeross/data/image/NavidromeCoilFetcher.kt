package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

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
    private val cacheDir: File
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

        val sizeParam = uri.getQueryParameter("size")?.toIntOrNull() ?: 500

        val cachedFile = File(cacheDir, "navidrome_cover_${coverArtId}_$sizeParam.jpg")
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
                NavidromeCoilFetcher(data, repository, okHttpClient, cache)
            } else {
                null
            }
        }
    }
}
