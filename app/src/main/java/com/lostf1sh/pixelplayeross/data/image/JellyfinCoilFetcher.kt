package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
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

        val sizeParam = uri.getQueryParameter("size")?.toIntOrNull() ?: 500

        val cachedFile = File(cacheDir, "jellyfin_cover_${itemId}_$sizeParam.jpg")
        RemoteArtworkCache.cachedResult(cachedFile)?.let { return it }

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
        } catch (e: Exception) {
            if (shouldLogFailure("download_$itemId")) {
                Timber.w(e, "$TAG: Failed to download cover art for $itemId")
            }
            null
        }
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
