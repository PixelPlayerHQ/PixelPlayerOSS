package com.lostf1sh.pixelplayeross.data.media

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @OptIn(ExperimentalCoilApi::class)
    fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        val imageLoader = context.imageLoader
        val memoryCache = imageLoader.memoryCache
        val diskCache = imageLoader.diskCache
        if (memoryCache == null && diskCache == null) return

        // Known Coil size request keys/transformations often append params.
        // This is a best-effort invalidation for common sizes.
        val knownSizeSuffixes = listOf(null, "128x128", "150x150", "168x168", "256x256", "300x300", "512x512", "600x600", "800x800")

        uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.forEach { baseUri ->
            if (com.lostf1sh.pixelplayeross.utils.LocalArtworkUri.isLocalArtworkUri(baseUri)) {
                com.lostf1sh.pixelplayeross.utils.LocalArtworkUri.parseSongId(baseUri)?.let { songId ->
                    com.lostf1sh.pixelplayeross.utils.AlbumArtUtils.clearCacheForSong(context, songId)
                }
            }

            knownSizeSuffixes.forEach { suffix ->
                val cacheKey = suffix?.let { "${baseUri}_${it}" } ?: baseUri
                memoryCache?.remove(MemoryCache.Key(cacheKey))
                diskCache?.remove(cacheKey)
            }
        }
    }
}
