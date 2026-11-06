package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import androidx.annotation.VisibleForTesting
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class JellyfinCoilFetcher(
    private val uri: Uri,
    private val repository: JellyfinRepository,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File
) : Fetcher {

    companion object {
        private const val TAG = "JellyfinCoilFetcher"
        private val recentlyLoggedFailures = ConcurrentHashMap<String, Long>()
        private const val LOG_FAILURE_COOLDOWN_MS = 60_000L

        /** Skips cover downloads after one could not connect, so offline covers don't each wait. */
        private const val SERVER_UNREACHABLE_BACKOFF_MS = 30_000L

        @Volatile
        private var serverUnreachableUntilNanos = 0L

        private fun isServerUnreachable(): Boolean =
            serverUnreachableUntilNanos != 0L && System.nanoTime() - serverUnreachableUntilNanos < 0

        private fun markServerUnreachable() {
            serverUnreachableUntilNanos =
                System.nanoTime() + SERVER_UNREACHABLE_BACKOFF_MS * 1_000_000L
        }

        private fun markServerReachable() {
            serverUnreachableUntilNanos = 0L
        }

        @VisibleForTesting
        internal fun resetServerReachability() = markServerReachable()

        /** Connect failures only: a read timeout means the server is up but slow. */
        private fun isConnectionFailure(e: Throwable): Boolean =
            e is ConnectException ||
                e is NoRouteToHostException ||
                e is UnknownHostException ||
                (e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true)

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

        val itemId = uri.host ?: uri.path?.removePrefix("/")
        if (itemId.isNullOrBlank()) {
            Timber.w("$TAG: Invalid URI format: $uri")
            return null
        }

        if (!repository.isLoggedIn) {
            Timber.v("$TAG: Not logged in, skipping fetch")
            return null
        }

        val sizeParam = uri.getQueryParameter("size")?.toIntOrNull()
            ?: RemoteArtworkCache.DEFAULT_COVER_SIZE_PX

        val cachedFile = File(
            cacheDir,
            RemoteArtworkCache.cacheFileName("jellyfin_cover", itemId, sizeParam)
        )
        RemoteArtworkCache.cachedResult(cachedFile)?.let { return it }

        if (isServerUnreachable()) {
            return cachedAlbumCover(itemId, sizeParam)
        }

        val imageUrl = repository.getImageUrl(itemId, sizeParam)
        if (imageUrl.isNullOrBlank()) {
            if (shouldLogFailure("no_url_$itemId")) {
                Timber.w("$TAG: No image URL for $itemId")
            }
            return null
        }

        val authHeader = repository.getAuthorizationHeader()

        return try {
            val request = Request.Builder()
                .url(imageUrl)
                .apply { if (authHeader != null) header("Authorization", authHeader) }
                .get()
                .build()
            RemoteArtworkCache.download(okHttpClient, request, cachedFile)
                .also { markServerReachable() }
        } catch (e: Exception) {
            // A cancelled fetch (its list item scrolled away) is not a failed download.
            if (e is CancellationException) throw e
            if (isConnectionFailure(e)) markServerUnreachable()
            if (shouldLogFailure("download_$itemId")) {
                Timber.w(e, "$TAG: Failed to download cover art for $itemId")
            }
            cachedAlbumCover(itemId, sizeParam)
        }
    }

    /**
     * Cached cover of [itemId]'s album, for when the song's own cover can't be fetched (e.g.
     * offline). Never cached as the song's cover.
     */
    private suspend fun cachedAlbumCover(itemId: String, sizePx: Int): FetchResult? {
        val albumCoverId = repository.getAlbumCoverItemId(itemId) ?: return null
        return RemoteArtworkCache.cachedResult(
            File(cacheDir, RemoteArtworkCache.cacheFileName("jellyfin_cover", albumCoverId, sizePx))
        )
    }

    class Factory @Inject constructor(
        private val repository: JellyfinRepository,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<Uri> {

        private var cacheDir: File? = null

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "jellyfin_cover") {
                val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
                JellyfinCoilFetcher(data, repository, okHttpClient, cache)
            } else {
                null
            }
        }
    }
}
