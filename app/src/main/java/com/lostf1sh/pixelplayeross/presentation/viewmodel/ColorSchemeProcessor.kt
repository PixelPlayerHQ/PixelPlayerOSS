package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtColorAccuracy
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtPaletteStyle
import com.lostf1sh.pixelplayeross.data.database.AlbumArtThemeDao
import com.lostf1sh.pixelplayeross.data.database.AlbumArtThemeEntity
import com.lostf1sh.pixelplayeross.data.database.StoredColorSchemeValues
import com.lostf1sh.pixelplayeross.data.database.toComposeColor
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import com.lostf1sh.pixelplayeross.utils.traceAsyncSection
import com.lostf1sh.pixelplayeross.ui.theme.clearExtractedColorCache
import com.lostf1sh.pixelplayeross.ui.theme.extractSeedColor
import com.lostf1sh.pixelplayeross.ui.theme.generateColorSchemeFromSeed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Efficient color scheme processor for album art.
 * 
 * Optimizations:
 * - In-memory LRU cache to avoid disk reads for recently accessed schemes
 * - Mutex-protected processing to avoid duplicate work
 * - Batched bitmap operations on IO dispatcher
 * - Reduced bitmap size (128x128) for faster processing
 * 
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class ColorSchemeProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumArtThemeDao: AlbumArtThemeDao
) {
    private val memoryCache = LruCache<String, ColorSchemePair>(20)
    private val processingMutex = Mutex()
    private val inProgressUris = mutableSetOf<String>()
    private val localArtworkCacheMutex = Mutex()
    @Volatile
    private var localArtworkGeneration = 0L

    /**
     * Channel for queuing color scheme requests.
     * Used by PlayerViewModel for background processing.
     * Capacity is bounded with DROP_OLDEST to prevent unbounded growth during rapid
     * track changes (e.g. fast seek through a large playlist).
     */
    val requestChannel = Channel<String>(capacity = 32, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    /**
     * Gets or generates a color scheme for the given album art URI.
     * Checks memory cache first, then database, then generates new.
     * All heavy operations are performed on appropriate dispatchers.
     */
    /**
     * Gets or generates a color scheme for the given album art URI.
     * Checks memory cache first, then database, then generates new.
     * @param forceRefresh If true, bypasses caches and forces regeneration from source image.
     */
    suspend fun getOrGenerateColorScheme(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int = AlbumArtColorAccuracy.DEFAULT,
        forceRefresh: Boolean = false
    ): ColorSchemePair? = traceAsyncSection("ColorSchemeProcessor.getOrGenerate") {
        val resolvedAccuracyLevel = AlbumArtColorAccuracy.clamp(colorAccuracyLevel)
        val generation = localArtworkGeneration
        if (!forceRefresh) {
            loadCachedColorScheme(
                albumArtUri = albumArtUri,
                paletteStyle = paletteStyle,
                colorAccuracyLevel = resolvedAccuracyLevel,
                expectedLocalGeneration = generation
            )?.let { schemePair ->
                return@traceAsyncSection schemePair
            }
        }

        generateAndCacheColorScheme(
            albumArtUri = albumArtUri,
            paletteStyle = paletteStyle,
            colorAccuracyLevel = resolvedAccuracyLevel,
            forceRefresh = forceRefresh,
            expectedLocalGeneration = generation
        )
    }

    suspend fun getPreviewColorScheme(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int = AlbumArtColorAccuracy.DEFAULT
    ): ColorSchemePair? {
        val resolvedAccuracyLevel = AlbumArtColorAccuracy.clamp(colorAccuracyLevel)
        val generation = localArtworkGeneration
        return loadCachedColorScheme(
            albumArtUri = albumArtUri,
            paletteStyle = paletteStyle,
            colorAccuracyLevel = resolvedAccuracyLevel,
            expectedLocalGeneration = generation
        ) ?: generateAndCacheColorScheme(
            albumArtUri = albumArtUri,
            paletteStyle = paletteStyle,
            colorAccuracyLevel = resolvedAccuracyLevel,
            persistToDatabase = false,
            expectedLocalGeneration = generation
        )
    }

    /**
     * Generates a color scheme from the album art bitmap.
     * All processing done on Default dispatcher for CPU-bound work.
     */
    private suspend fun generateAndCacheColorScheme(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int,
        persistToDatabase: Boolean = true,
        forceRefresh: Boolean = false,
        expectedLocalGeneration: Long
    ): ColorSchemePair? = traceAsyncSection("ColorSchemeProcessor.generate") {
        try {
            val cacheKey = buildCacheKey(albumArtUri, paletteStyle, colorAccuracyLevel)
            val bitmap = withContext(Dispatchers.IO) {
                loadBitmapForColorExtraction(albumArtUri, forceRefresh)
            } ?: return@traceAsyncSection null

            val schemePair = try {
                withContext(Dispatchers.Default) {
                    val seed = extractSeedColor(
                        bitmap = bitmap,
                        config = com.lostf1sh.pixelplayeross.ui.theme.ColorExtractionConfig(
                            accuracyLevel = colorAccuracyLevel
                        )
                    )
                    generateColorSchemeFromSeed(seedColor = seed, paletteStyle = paletteStyle)
                }
            } finally {
                bitmap.recycle()
            }

            suspend fun cacheScheme(): ColorSchemePair {
                if (persistToDatabase) {
                    withContext(Dispatchers.IO) {
                        albumArtThemeDao.insertTheme(
                            mapColorSchemePairToEntity(
                                uri = albumArtUri,
                                paletteStyle = paletteStyle,
                                colorAccuracyLevel = colorAccuracyLevel,
                                pair = schemePair
                            )
                        )
                    }
                }
                memoryCache.put(cacheKey, schemePair)
                return schemePair
            }

            if (LocalArtworkUri.isLocalArtworkUri(albumArtUri)) {
                localArtworkCacheMutex.withLock {
                    if (expectedLocalGeneration != localArtworkGeneration) null else cacheScheme()
                }
            } else {
                cacheScheme()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads a small bitmap optimized for color extraction.
     */
    private suspend fun loadBitmapForColorExtraction(uri: String, skipCache: Boolean): Bitmap? {
        return try {
            val cachePolicy = if (skipCache) CachePolicy.DISABLED else CachePolicy.ENABLED
            val diskCachePolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) CachePolicy.DISABLED else cachePolicy
            
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(Size(128, 128))
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .memoryCachePolicy(cachePolicy)
                .diskCachePolicy(diskCachePolicy)
                .build()
            
            val drawable = context.imageLoader.execute(request).drawable ?: return null
            
            createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1)
            ).also { bmp ->
                Canvas(bmp).let { canvas ->
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a URI is currently being processed.
     * Used to avoid duplicate work.
     */
    suspend fun markProcessing(uri: String): Boolean {
        return processingMutex.withLock {
            if (inProgressUris.contains(uri)) {
                false
            } else {
                inProgressUris.add(uri)
                true
            }
        }
    }

    /**
     * Marks a URI as finished processing.
     */
    suspend fun markComplete(uri: String) {
        processingMutex.withLock {
            inProgressUris.remove(uri)
        }
    }

    /**
     * Clears the in-memory cache.
     * Call when memory is low or on configuration changes.
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    /**
     * Removes a specific URI from the cache.
     */
    fun evictFromCache(uri: String) {
        removeUriFromMemoryCache(uri)
    }

    /**
     * Invalidates the color scheme for a URI in both memory and database.
     */
    suspend fun invalidateScheme(uri: String) {
        clearExtractedColorCache()
        removeUriFromMemoryCache(uri)
        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(listOf(uri))
        }
    }

    /** Retire only local palettes; extraction resumes lazily for visible/current artwork. */
    suspend fun invalidateLocalArtworkSchemes() {
        localArtworkCacheMutex.withLock {
            localArtworkGeneration += 1
            memoryCache.snapshot().keys
                .filter(LocalArtworkUri::isLocalArtworkUri)
                .forEach { memoryCache.remove(it) }
            clearExtractedColorCache()
            withContext(Dispatchers.IO) {
                albumArtThemeDao.deleteLocalArtworkThemes()
            }
        }
    }

    private fun removeUriFromMemoryCache(uri: String) {
        val prefix = "$uri$CACHE_KEY_SEPARATOR"
        memoryCache.snapshot().keys
            .filter { key -> key == uri || key.startsWith(prefix) }
            .forEach { key -> memoryCache.remove(key) }
    }

    private fun mapColorSchemePairToEntity(
        uri: String,
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int,
        pair: ColorSchemePair
    ): AlbumArtThemeEntity {
        fun mapScheme(cs: ColorScheme) = StoredColorSchemeValues(
            primary = cs.primary.toHexString(),
            onPrimary = cs.onPrimary.toHexString(),
            primaryContainer = cs.primaryContainer.toHexString(),
            onPrimaryContainer = cs.onPrimaryContainer.toHexString(),
            secondary = cs.secondary.toHexString(),
            onSecondary = cs.onSecondary.toHexString(),
            secondaryContainer = cs.secondaryContainer.toHexString(),
            onSecondaryContainer = cs.onSecondaryContainer.toHexString(),
            tertiary = cs.tertiary.toHexString(),
            onTertiary = cs.onTertiary.toHexString(),
            tertiaryContainer = cs.tertiaryContainer.toHexString(),
            onTertiaryContainer = cs.onTertiaryContainer.toHexString(),
            background = cs.background.toHexString(),
            onBackground = cs.onBackground.toHexString(),
            surface = cs.surface.toHexString(),
            onSurface = cs.onSurface.toHexString(),
            surfaceVariant = cs.surfaceVariant.toHexString(),
            onSurfaceVariant = cs.onSurfaceVariant.toHexString(),
            error = cs.error.toHexString(),
            onError = cs.onError.toHexString(),
            outline = cs.outline.toHexString(),
            errorContainer = cs.errorContainer.toHexString(),
            onErrorContainer = cs.onErrorContainer.toHexString(),
            inversePrimary = cs.inversePrimary.toHexString(),
            inverseSurface = cs.inverseSurface.toHexString(),
            inverseOnSurface = cs.inverseOnSurface.toHexString(),
            surfaceTint = cs.surfaceTint.toHexString(),
            outlineVariant = cs.outlineVariant.toHexString(),
            scrim = cs.scrim.toHexString(),
            surfaceBright = cs.surfaceBright.toHexString(),
            surfaceDim = cs.surfaceDim.toHexString(),
            surfaceContainer = cs.surfaceContainer.toHexString(),
            surfaceContainerHigh = cs.surfaceContainerHigh.toHexString(),
            surfaceContainerHighest = cs.surfaceContainerHighest.toHexString(),
            surfaceContainerLow = cs.surfaceContainerLow.toHexString(),
            surfaceContainerLowest = cs.surfaceContainerLowest.toHexString(),
            primaryFixed = cs.primaryFixed.toHexString(),
            primaryFixedDim = cs.primaryFixedDim.toHexString(),
            onPrimaryFixed = cs.onPrimaryFixed.toHexString(),
            onPrimaryFixedVariant = cs.onPrimaryFixedVariant.toHexString(),
            secondaryFixed = cs.secondaryFixed.toHexString(),
            secondaryFixedDim = cs.secondaryFixedDim.toHexString(),
            onSecondaryFixed = cs.onSecondaryFixed.toHexString(),
            onSecondaryFixedVariant = cs.onSecondaryFixedVariant.toHexString(),
            tertiaryFixed = cs.tertiaryFixed.toHexString(),
            tertiaryFixedDim = cs.tertiaryFixedDim.toHexString(),
            onTertiaryFixed = cs.onTertiaryFixed.toHexString(),
            onTertiaryFixedVariant = cs.onTertiaryFixedVariant.toHexString()
        )
        return AlbumArtThemeEntity(
            albumArtUriString = uri,
            paletteStyle = paletteStyleCacheKey(paletteStyle, colorAccuracyLevel),
            lightThemeValues = mapScheme(pair.light),
            darkThemeValues = mapScheme(pair.dark)
        )
    }

    private fun mapEntityToColorSchemePair(entity: AlbumArtThemeEntity): ColorSchemePair {
        fun mapStored(sv: StoredColorSchemeValues) = ColorScheme(
            primary = sv.primary.toComposeColor(),
            onPrimary = sv.onPrimary.toComposeColor(),
            primaryContainer = sv.primaryContainer.toComposeColor(),
            onPrimaryContainer = sv.onPrimaryContainer.toComposeColor(),
            inversePrimary = sv.inversePrimary.toComposeColor(),
            secondary = sv.secondary.toComposeColor(),
            onSecondary = sv.onSecondary.toComposeColor(),
            secondaryContainer = sv.secondaryContainer.toComposeColor(),
            onSecondaryContainer = sv.onSecondaryContainer.toComposeColor(),
            tertiary = sv.tertiary.toComposeColor(),
            onTertiary = sv.onTertiary.toComposeColor(),
            tertiaryContainer = sv.tertiaryContainer.toComposeColor(),
            onTertiaryContainer = sv.onTertiaryContainer.toComposeColor(),
            background = sv.background.toComposeColor(),
            onBackground = sv.onBackground.toComposeColor(),
            surface = sv.surface.toComposeColor(),
            onSurface = sv.onSurface.toComposeColor(),
            surfaceVariant = sv.surfaceVariant.toComposeColor(),
            onSurfaceVariant = sv.onSurfaceVariant.toComposeColor(),
            error = sv.error.toComposeColor(),
            onError = sv.onError.toComposeColor(),
            outline = sv.outline.toComposeColor(),
            errorContainer = sv.errorContainer.toComposeColor(),
            onErrorContainer = sv.onErrorContainer.toComposeColor(),
            inverseSurface = sv.inverseSurface.toComposeColor(),
            inverseOnSurface = sv.inverseOnSurface.toComposeColor(),
            surfaceTint = sv.surfaceTint.toComposeColor(),
            outlineVariant = sv.outlineVariant.toComposeColor(),
            scrim = sv.scrim.toComposeColor(),
            surfaceBright = sv.surfaceBright.toComposeColor(),
            surfaceDim = sv.surfaceDim.toComposeColor(),
            surfaceContainer = sv.surfaceContainer.toComposeColor(),
            surfaceContainerHigh = sv.surfaceContainerHigh.toComposeColor(),
            surfaceContainerHighest = sv.surfaceContainerHighest.toComposeColor(),
            surfaceContainerLow = sv.surfaceContainerLow.toComposeColor(),
            surfaceContainerLowest = sv.surfaceContainerLowest.toComposeColor(),
            primaryFixed = sv.primaryFixed.toComposeColor(),
            primaryFixedDim = sv.primaryFixedDim.toComposeColor(),
            onPrimaryFixed = sv.onPrimaryFixed.toComposeColor(),
            onPrimaryFixedVariant = sv.onPrimaryFixedVariant.toComposeColor(),
            secondaryFixed = sv.secondaryFixed.toComposeColor(),
            secondaryFixedDim = sv.secondaryFixedDim.toComposeColor(),
            onSecondaryFixed = sv.onSecondaryFixed.toComposeColor(),
            onSecondaryFixedVariant = sv.onSecondaryFixedVariant.toComposeColor(),
            tertiaryFixed = sv.tertiaryFixed.toComposeColor(),
            tertiaryFixedDim = sv.tertiaryFixedDim.toComposeColor(),
            onTertiaryFixed = sv.onTertiaryFixed.toComposeColor(),
            onTertiaryFixedVariant = sv.onTertiaryFixedVariant.toComposeColor()
        )
        return ColorSchemePair(
            light = mapStored(entity.lightThemeValues),
            dark = mapStored(entity.darkThemeValues)
        )
    }

    private fun Color.toHexString(): String {
        return String.format("#%08X", toArgb())
    }

    private fun buildCacheKey(
        uri: String,
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int
    ): String {
        return "$uri$CACHE_KEY_SEPARATOR${paletteStyleCacheKey(paletteStyle, colorAccuracyLevel)}"
    }

    private fun paletteStyleCacheKey(
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int
    ): String {
        return buildString {
            append(paletteStyle.storageKey)
            append(CACHE_KEY_SEPARATOR)
            append("accuracy_")
            append(AlbumArtColorAccuracy.clamp(colorAccuracyLevel))
            append(CACHE_KEY_SEPARATOR)
            append(CACHE_ALGORITHM_VERSION)
        }
    }

    private suspend fun loadCachedColorScheme(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        colorAccuracyLevel: Int,
        expectedLocalGeneration: Long
    ): ColorSchemePair? {
        val cacheKey = buildCacheKey(albumArtUri, paletteStyle, colorAccuracyLevel)

        suspend fun loadScheme(): ColorSchemePair? {
            memoryCache.get(cacheKey)?.let { return it }

            val cachedEntity = withContext(Dispatchers.IO) {
                albumArtThemeDao.getThemeByUriAndStyle(
                    albumArtUri,
                    paletteStyleCacheKey(paletteStyle, colorAccuracyLevel)
                )
            } ?: return null

            return mapEntityToColorSchemePair(cachedEntity).also { schemePair ->
                memoryCache.put(cacheKey, schemePair)
            }
        }

        return if (LocalArtworkUri.isLocalArtworkUri(albumArtUri)) {
            localArtworkCacheMutex.withLock {
                if (expectedLocalGeneration != localArtworkGeneration) null else loadScheme()
            }
        } else {
            loadScheme()
        }
    }

    companion object {
        private const val TAG = "ColorSchemeProcessor"
        private const val CACHE_KEY_SEPARATOR = "|"
        private const val CACHE_ALGORITHM_VERSION = "algo_v7"
    }
}
