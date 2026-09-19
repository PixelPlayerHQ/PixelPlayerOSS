package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerApiService
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.utils.AlbumArtUtils
import com.lostf1sh.pixelplayeross.utils.FolderArtistArtUtils
import com.lostf1sh.pixelplayeross.utils.NetworkRetryUtils
import com.lostf1sh.pixelplayeross.utils.isRetryableNetworkError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import androidx.core.graphics.scale

/**
 * Resolves local artist portraits before fetching and caching Deezer images.
 * Uses both in-memory LRU cache and Room database for persistent storage.
 */
@Singleton
class ArtistImageRepository @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ArtistImageRepository"
        private const val CACHE_SIZE = 100
        // Path/miss records are tiny; keep large artist lists from repeating disk discovery
        // whenever a single Deezer URL update causes the library to emit again.
        private const val FOLDER_IMAGE_CACHE_SIZE = 4_096
        private const val MAX_ARTIST_DIRECTORIES = 64
        private const val PREFETCH_CONCURRENCY = 3
        private val deezerSizeRegex = Regex("/\\d{2,4}x\\d{2,4}([\\-.])")
        private const val NETWORK_RETRY_ATTEMPTS = 3
        private const val NETWORK_RETRY_INITIAL_DELAY_MS = 500L
        private const val MAX_CUSTOM_IMAGE_SOURCE_BYTES = 24L * 1024L * 1024L
        private const val MAX_CUSTOM_IMAGE_DIMENSION_PX = 16_384
        private const val MAX_CUSTOM_IMAGE_TOTAL_PIXELS = 80_000_000L
        private const val TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX = 2_048
        private const val TARGET_CUSTOM_IMAGE_MAX_PIXELS = 4_194_304L

        internal fun calculateCustomImageSampleSize(width: Int, height: Int): Int {
            var sampleSize = 1
            while (
                width / sampleSize > TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX ||
                height / sampleSize > TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX ||
                (width.toLong() / sampleSize) * (height.toLong() / sampleSize) > TARGET_CUSTOM_IMAGE_MAX_PIXELS
            ) {
                sampleSize = sampleSize shl 1
            }
            return sampleSize.coerceAtLeast(1)
        }
    }

    private val memoryCache = LruCache<String, String>(CACHE_SIZE)
    private data class FolderImageResult(val path: String?, val revision: Long)
    private val folderImageCache = LruCache<Long, FolderImageResult>(FOLDER_IMAGE_CACHE_SIZE)
    private val folderLookupMutex = Mutex()
    private val _folderArtworkRevision = MutableStateFlow(0L)
    val folderArtworkRevision: StateFlow<Long> = _folderArtworkRevision.asStateFlow()
    
    private val fetchMutex = Mutex()
    private val pendingFetches = mutableSetOf<String>()
    
    private val prefetchSemaphore = Semaphore(PREFETCH_CONCURRENCY)
    
    private val failedFetches = mutableSetOf<String>()

    /**
     * Get artist image URL, fetching from Deezer if not cached.
     * @param artistName Name of the artist
     * @param artistId Room database ID of the artist (for caching)
     * @return Image URL or null if not found
     */
    suspend fun getArtistImageUrl(artistName: String, artistId: Long): String? {
        if (artistName.isBlank()) return null
        getFolderArtistImageUrl(artistId, artistName)?.let { return it }
        if (!userPreferencesRepository.externalArtistImagesEnabledFlow.first()) return null

        val normalizedName = artistName.trim().lowercase()

        memoryCache.get(normalizedName)?.let { cachedUrl ->
            return cachedUrl
        }
        
        if (failedFetches.contains(normalizedName)) {
            return null
        }

        val (resolvedArtistId, dbCachedUrl) = withContext(Dispatchers.IO) {
            val canonicalArtistId = musicDao.getArtistIdByNormalizedName(artistName) ?: artistId
            val cachedUrl = musicDao.getArtistImageUrl(canonicalArtistId)
                ?: musicDao.getArtistImageUrlByNormalizedName(artistName)
            canonicalArtistId to cachedUrl
        }
        if (!dbCachedUrl.isNullOrEmpty()) {
            val upgradedDbUrl = upgradeToHighResDeezerUrl(dbCachedUrl)
            memoryCache.put(normalizedName, upgradedDbUrl)
            if (upgradedDbUrl != dbCachedUrl) {
                withContext(Dispatchers.IO) {
                    musicDao.updateArtistImageUrl(resolvedArtistId, upgradedDbUrl)
                }
            }
            return upgradedDbUrl
        }

        return fetchAndCacheArtistImage(artistName, resolvedArtistId, normalizedName)
    }

    /**
     * Prefetch artist images for a list of artists in background.
     * Useful for batch loading when displaying artist lists.
     */
    suspend fun prefetchArtistImages(artists: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        if (!userPreferencesRepository.externalArtistImagesEnabledFlow.first()) return@withContext

        artists.chunked(PREFETCH_CONCURRENCY * 4).forEach { chunk ->
            chunk.map { (artistId, artistName) ->
                async {
                    try {
                        val normalizedName = artistName.trim().lowercase()
                        if (memoryCache.get(normalizedName) == null && !failedFetches.contains(normalizedName)) {
                            prefetchSemaphore.withPermit {
                                getArtistImageUrl(artistName, artistId)
                            }
                        } else {
                            Timber.tag(TAG).d("Skipping prefetch for $artistName")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to prefetch image for $artistName: ${e.message}")
                    }
                }
            }.awaitAll()
        }
    }
    
    private suspend fun fetchAndCacheArtistImage(
        artistName: String,
        artistId: Long,
        normalizedName: String
    ): String? {
        fetchMutex.withLock {
            if (pendingFetches.contains(normalizedName)) {
                return null
            }
            pendingFetches.add(normalizedName)
        }

        return try {
            withContext(Dispatchers.IO) {
                val response = withNetworkRetry("deezer_search:$artistName") {
                    deezerApiService.searchArtist(artistName, limit = 1)
                }
                val deezerArtist = response.data.firstOrNull()

                if (deezerArtist != null) {
                    val imageUrl = (
                        deezerArtist.pictureXl
                            ?: deezerArtist.pictureBig
                            ?: deezerArtist.pictureMedium
                            ?: deezerArtist.picture
                        )?.let(::upgradeToHighResDeezerUrl)

                    if (!imageUrl.isNullOrEmpty()) {
                        memoryCache.put(normalizedName, imageUrl)
                        
                        musicDao.updateArtistImageUrl(artistId, imageUrl)

                        Timber.tag(TAG).d("Fetched and cached image for $artistName: $imageUrl")
                        imageUrl
                    } else {
                        null
                    }
                } else {
                    Timber.tag(TAG).d("No Deezer artist found for: $artistName")
                    failedFetches.add(normalizedName)
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error fetching artist image for $artistName: ${e.message}")
            if(e !is java.net.SocketTimeoutException) {
                failedFetches.add(normalizedName)
            }
            null
        } finally {
            fetchMutex.withLock {
                pendingFetches.remove(normalizedName)
            }
        }
    }

    private suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int = NETWORK_RETRY_ATTEMPTS,
        initialDelayMs: Long = NETWORK_RETRY_INITIAL_DELAY_MS,
        block: suspend () -> T
    ): T {
        return NetworkRetryUtils.withNetworkRetry(
            operationName = operationName,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            shouldRetry = { throwable -> throwable.isRetryableNetworkError() },
            onRetry = { attempt, attempts, throwable ->
                Timber.tag(TAG)
                    .d("Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}")
            },
            block = block
        )
    }
    
    /**
     * Clear all cached images. Useful for debugging or forced refresh.
     */
    fun clearCache() {
        memoryCache.evictAll()
        failedFetches.clear()
        folderImageCache.evictAll()
        _folderArtworkRevision.update { it + 1 }
    }

    /**
     * Folder images are kept separate from persisted third-party URLs and user overrides so
     * disabling the option (or losing image access) immediately restores the existing image.
     */
    suspend fun getFolderArtistImageUrl(artistId: Long, artistName: String): String? {
        if (artistName.isBlank() || !isFolderArtworkEnabled()) return null
        return withContext(Dispatchers.IO) {
            folderLookupMutex.withLock {
                if (!isFolderArtworkEnabled()) return@withLock null
                val revision = _folderArtworkRevision.value
                folderImageCache.get(artistId)?.takeIf { it.revision == revision }?.let { cached ->
                    if (cached.path == null || File(cached.path).let { it.isFile && it.canRead() }) {
                        return@withLock cached.path
                    }
                }
                val directories = musicDao.getLocalArtistDirectories(
                    artistId,
                    MAX_ARTIST_DIRECTORIES
                )
                val imagePath = FolderArtistArtUtils.findArtistImage(directories, artistName)?.absolutePath
                if (revision != _folderArtworkRevision.value || !isFolderArtworkEnabled()) {
                    return@withLock null
                }
                folderImageCache.put(artistId, FolderImageResult(imagePath, revision))
                imagePath
            }
        }
    }

    private suspend fun isFolderArtworkEnabled(): Boolean {
        return userPreferencesRepository.useFolderAlbumArtFlow.first() &&
            AlbumArtUtils.canReadImageFiles(context)
    }

    /**
     * Returns the effective image URL for an artist:
     * - If a custom (user-set) image exists in DB → returns that path
     * - Otherwise prefers an opted-in folder portrait when available
     * - Otherwise falls back to the Deezer URL (fetching from API if needed)
     */
    suspend fun getEffectiveArtistImageUrl(artistId: Long, artistName: String): String? {
        val customUri = withContext(Dispatchers.IO) { musicDao.getArtistCustomImage(artistId) }
        if (!customUri.isNullOrBlank()) return customUri
        return getArtistImageUrl(artistName, artistId)
    }

    /**
     * Saves a user-selected image as the artist's custom image.
     *
     * The content URI is resolved immediately and the bitmap is written to
     * internal storage (filesDir/artist_art_<id>.jpg). This avoids depending
     * on a content URI that may expire once the photo-picker dismisses.
     *
     * @param context Application context (used for contentResolver and filesDir)
     * @param artistId The artist's database row ID
     * @param sourceUri URI returned by the system photo-picker
     * @return The internal file path on success, null on failure
     */
    suspend fun setCustomArtistImage(context: Context, artistId: Long, sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeCustomArtistBitmap(context, sourceUri) ?: return@withContext null
                val scaledBitmap = scaleBitmapIfNeeded(bitmap)
                try {
                    val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                    FileOutputStream(destFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    val internalPath = destFile.absolutePath

                    musicDao.updateArtistCustomImage(artistId, internalPath)

                    Timber.tag(TAG).d("Custom artist image saved: $internalPath")
                    internalPath
                } finally {
                    if (scaledBitmap !== bitmap) {
                        bitmap.recycle()
                    }
                    scaledBitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e("Failed to save custom artist image for id=$artistId: ${e.message}")
                null
            }
        }
    }

    private fun decodeCustomArtistBitmap(context: Context, sourceUri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri)?.lowercase()
        if (mimeType != null && !mimeType.startsWith("image/")) {
            Timber.tag(TAG).w("Rejected custom artist image with unsupported MIME type: $mimeType")
            return null
        }

        runCatching { resolver.openAssetFileDescriptor(sourceUri, "r") }.getOrNull()?.use { descriptor ->
            val declaredLength = descriptor.length
            if (declaredLength > MAX_CUSTOM_IMAGE_SOURCE_BYTES) {
                Timber.tag(TAG)
                    .w("Rejected custom artist image larger than allowed source size: $declaredLength")
                return null
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with invalid bounds: ${width}x${height}")
            return null
        }
        if (width > MAX_CUSTOM_IMAGE_DIMENSION_PX || height > MAX_CUSTOM_IMAGE_DIMENSION_PX) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with oversized bounds: ${width}x${height}")
            return null
        }
        if (width.toLong() * height.toLong() > MAX_CUSTOM_IMAGE_TOTAL_PIXELS) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with excessive pixel count: ${width}x${height}")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateCustomImageSampleSize(width, height)
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return try {
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (oom: OutOfMemoryError) {
            Timber.tag(TAG).e(oom, "Failed to decode custom artist image due to OOM")
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX) {
            return bitmap
        }

        val scale = TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX.toFloat() / longestEdge.toFloat()
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(scaledWidth, scaledHeight)
    }

    /**
     * Removes the user's custom artist image, reverting to the Deezer URL.
     *
     * @param context Application context
     * @param artistId The artist's database row ID
     */
    suspend fun clearCustomArtistImage(context: Context, artistId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                if (destFile.exists()) {
                    destFile.delete()
                    Timber.tag(TAG).d("Deleted custom artist image file: ${destFile.absolutePath}")
                }
                musicDao.updateArtistCustomImage(artistId, null)
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e("Failed to clear custom artist image for id=$artistId: ${e.message}")
            }
        }
    }

    private fun upgradeToHighResDeezerUrl(url: String): String {
        if (!url.contains("dzcdn.net/images/artist")) return url
        return deezerSizeRegex.replace(url, "/1000x1000$1")
    }
}
