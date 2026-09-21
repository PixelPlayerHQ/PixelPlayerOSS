package com.lostf1sh.pixelplayeross.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lostf1sh.pixelplayeross.data.media.AudioMetadataReader
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

object AlbumArtUtils {
    private const val CACHE_VERSION_SUFFIX = "_v4"

    // Cached covers are display-only; the source audio file keeps its original art untouched.
    // Embedded covers can be multi-megabyte full-resolution scans, and re-decoding such a blob
    // on every cold artwork load is the dominant avoidable artwork cost. Bounding the cached
    // copy to 1536 px JPEG is invisible on phone-class displays (the full player tops out at
    // 2048 px) while cutting heavy covers to a few hundred KB — far cheaper to decode and
    // lighter on RAM/IPC.
    private const val MAX_CACHED_ART_DIMENSION_PX = 1536
    private const val CACHED_ART_JPEG_QUALITY = 90
    // Cache entries larger than this are treated as legacy/oversized and shrunk in the
    // background on first access (one-time migration for art cached before bounding existed).
    private const val OVERSIZED_CACHED_ART_BYTES = 900L * 1024

    // Folder covers are arbitrary user files with no size ceiling, unlike embedded pictures which
    // a tagger already had to fit into the audio file. Refuse absurd ones before reading them
    // whole into memory to be bounded.
    private const val MAX_EXTERNAL_ART_BYTES = 20L * 1024 * 1024

    /**
     * Mirrors `UserPreferencesRepository.useFolderAlbumArtFlow`. AlbumArtUtils is a plain object
     * reached from a Coil fetcher and a ContentProvider, so it cannot take a DI dependency; the
     * value is pushed in from PixelPlayerApplication and from SettingsViewModel, the same way as
     * [AlbumArtCacheManager.configuredCacheLimitMb].
     *
     * Null means "not read yet" rather than "disabled" — treating the two alike would let an
     * artwork request that beats startup cache the wrong result permanently.
     */
    @Volatile
    private var folderAlbumArtPreference: Boolean? = null

    fun setFolderAlbumArtPreference(enabled: Boolean) {
        folderAlbumArtPreference = enabled
    }

    /** The mirrored preference, or null if it has not been read yet. */
    fun folderAlbumArtPreferenceOrNull(): Boolean? = folderAlbumArtPreference

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkCacheWriteLock = Any()
    private val artworkCacheGeneration = AtomicLong()
    private val _artworkCacheVersion = MutableStateFlow(0L)
    val artworkCacheVersion = _artworkCacheVersion.asStateFlow()

    /** Publish only after file and Coil caches are cleared, so mounted images can reload safely. */
    internal fun notifyArtworkCacheInvalidated() {
        _artworkCacheVersion.update { it + 1L }
    }

    /** Prevent in-flight reads from restoring covers or miss markers after settings invalidate them. */
    internal fun bumpArtworkCacheGeneration() {
        synchronized(artworkCacheWriteLock) {
            artworkCacheGeneration.incrementAndGet()
        }
    }
    // Tracks cache files currently being shrunk so rapid repeated loads of the same oversized
    // cover don't read the large blob into memory more than once concurrently.
    private val artworkShrinkInFlight = ConcurrentHashMap.newKeySet<String>()
    private val commonArtworkFileNames = listOf(
        "cover.jpg", "cover.png", "cover.jpeg",
        "folder.jpg", "folder.png", "folder.jpeg",
        "album.jpg", "album.png", "album.jpeg",
        "albumart.jpg", "albumart.png", "albumart.jpeg",
        "artwork.jpg", "artwork.png", "artwork.jpeg",
        "front.jpg", "front.png", "front.jpeg",
        ".folder.jpg", ".albumart.jpg",
        "thumb.jpg", "thumbnail.jpg",
        "scan.jpg", "scanned.jpg"
    )
    private val genericMixedDirectoryNames = setOf(
        "download",
        "downloads",
        "music",
        "songs",
        "audio",
        "studio",
        "gallery",
        "pictures",
        "photos",
        "images",
        "dcim",
        "camera",
        "screenshots"
    )

    /**
     * Main function to get album art for local songs.
     *
     * Local artwork is embedded-only unless the user opts in to folder covers, which
     * [isFolderAlbumArtEnabled] resolves. That opt-in exists because falling back to folder images such as
     * cover.jpg/thumb.jpg unasked can pick unrelated Gallery files when music is stored in mixed
     * directories, and can duplicate the same image across unrelated tracks.
     */
    fun getAlbumArtUri(
        appContext: Context,
        path: String,
        songId: Long,
        forceRefresh: Boolean
    ): String? {
        return if (hasLocalAlbumArt(appContext, path, songId, forceRefresh)) {
            LocalArtworkUri.buildSongUri(songId)
        } else {
            null
        }
    }

    /**
     * Lightweight scan-time artwork resolution.
     *
     * Full embedded-art extraction can allocate large ByteArrays per song. During a library scan
     * we only store the stable lazy URI; the normal image-loading path extracts/caches artwork
     * later for visible rows.
     */
    fun getAlbumArtUriForLibraryScan(
        appContext: Context,
        songId: Long,
        forceRefresh: Boolean
    ): String? {
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (forceRefresh) {
            cachedFile.delete()
            noArtFile.delete()
        }

        val hasCachedArtwork = cachedFile.exists() && cachedFile.length() > 0
        if (hasCachedArtwork) {
            cachedFile.setLastModified(System.currentTimeMillis())
        }

        return resolveAlbumArtUriForLibraryScan(
            songId = songId,
            hasCachedArtwork = hasCachedArtwork,
            hasNoArtworkMarker = noArtFile.exists(),
            forceRefresh = forceRefresh
        )
    }

    fun getCachedAlbumArtUri(
        appContext: Context,
        songId: Long
    ): Uri? {
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        if (!cachedFile.exists()) return null

        cachedFile.setLastModified(System.currentTimeMillis())
        return shareableCacheUri(appContext, cachedFile)
    }

    fun hasCachedAlbumArt(
        appContext: Context,
        songId: Long
    ): Boolean {
        return getCachedAlbumArtFile(appContext, songId).exists()
    }

    /**
     * Enhanced album art detection without eagerly persisting the whole library to cache.
     */
    fun getEmbeddedAlbumArtUri(
        appContext: Context,
        filePath: String,
        songId: Long,
        deepScan: Boolean
    ): Uri? {
        ensureAlbumArtCachedFile(appContext, songId, filePath, deepScan)?.let { cachedFile ->
            return shareableCacheUri(appContext, cachedFile)
        }
        return null
    }

    fun ensureAlbumArtCachedFile(
        appContext: Context,
        songId: Long,
        filePath: String? = null,
        forceRefresh: Boolean = false
    ): File? {
        val generation = artworkCacheGeneration.get()
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (!forceRefresh) {
            if (cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile.setLastModified(System.currentTimeMillis())
                scheduleOversizedArtworkShrink(cachedFile)
                return cachedFile
            }
            if (noArtFile.exists()) {
                return null
            }
        } else {
            cachedFile.delete()
            noArtFile.delete()
        }

        val resolvedPath = filePath ?: resolveSongMediaStoreInfo(appContext, songId)?.path ?: return null
        if (!File(resolvedPath).exists()) {
            return null
        }

        // Folder covers outrank embedded pictures when the user has opted in: a cover.jpg is
        // usually the full-resolution original, while embedded art is often a downscaled copy.
        readExternalAlbumArtBytes(resolvedPath, isFolderAlbumArtEnabled(appContext))?.let { bytes ->
            return cacheAlbumArtBytesIfCurrent(appContext, bytes, songId, generation)
        }

        extractEmbeddedAlbumArtBytes(resolvedPath)?.let { bytes ->
            return cacheAlbumArtBytesIfCurrent(appContext, bytes, songId, generation)
        }

        synchronized(artworkCacheWriteLock) {
            if (generation == artworkCacheGeneration.get()) {
                cachedFile.delete()
                noArtFile.createNewFile()
            }
        }
        return null
    }

    fun openArtworkInputStream(
        appContext: Context,
        uri: Uri
    ): InputStream? {
        val uriString = uri.toString()
        return when {
            LocalArtworkUri.isLocalArtworkUri(uriString) -> {
                val songId = LocalArtworkUri.parseSongId(uriString) ?: return null
                val resolvedPath = resolveSongMediaStoreInfo(appContext, songId)?.path
                ensureAlbumArtCachedFile(
                    appContext = appContext,
                    songId = songId,
                    filePath = resolvedPath
                )?.inputStream()
            }
            uri.scheme.isNullOrBlank() && uri.toString().startsWith("/") -> File(uri.toString()).inputStream()
            else -> appContext.contentResolver.openInputStream(uri)
        }
    }

    private fun hasLocalAlbumArt(
        appContext: Context,
        filePath: String,
        songId: Long,
        deepScan: Boolean
    ): Boolean {
        val generation = artworkCacheGeneration.get()
        val audioFile = File(filePath)
        if (!audioFile.exists() || !audioFile.canRead()) {
            return false
        }

        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (!deepScan) {
            if (noArtFile.exists()) {
                if (cachedFile.exists()) {
                    cachedFile.delete()
                }
                return false
            }

            if (cachedFile.exists() && cachedFile.length() > 0) {
                return true
            }
        } else {
            noArtFile.delete()
        }

        if (readExternalAlbumArtBytes(filePath, isFolderAlbumArtEnabled(appContext)) != null) {
            noArtFile.delete()
            return true
        }

        val hasEmbeddedArt = extractEmbeddedAlbumArtBytes(filePath)?.isNotEmpty() == true
        if (hasEmbeddedArt) {
            noArtFile.delete()
            return true
        }

        synchronized(artworkCacheWriteLock) {
            if (generation == artworkCacheGeneration.get()) {
                cachedFile.delete()
                noArtFile.createNewFile()
            }
        }
        return false
    }

    /**
     * Look for external album art files in the same directory.
     *
     * This is kept for explicit, controlled callers only. The default local-song artwork path
     * must remain embedded-only so the app does not pull unrelated personal Gallery files.
     */
    fun getExternalAlbumArtUri(filePath: String): Uri? {
        return runCatching {
            findExternalAlbumArtFile(filePath)?.let(Uri::fromFile)
        }.getOrNull()
    }

    /**
     * Whether folder covers should be used right now: the user opted in *and* the app can
     * actually read image files.
     *
     * The permission is checked rather than assumed because it can be taken away after the user
     * opts in — revoked in system settings, or auto-reset while the app goes unused. Without the
     * check the stored preference would keep claiming the feature is on while every folder read
     * silently failed.
     */
    internal fun isFolderAlbumArtEnabled(appContext: Context): Boolean {
        val preferred = folderAlbumArtPreference
            ?: UserPreferencesRepository.readUseFolderAlbumArtBlocking(appContext)
                .also { folderAlbumArtPreference = it }
        return preferred && canReadImageFiles(appContext)
    }

    /** Both image access and legacy shared-storage access can be revoked in system settings. */
    @android.annotation.SuppressLint("InlinedApi") // Guarded by the SDK argument, injectable for tests.
    internal fun canReadImageFiles(
        appContext: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        val permission = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Artwork bytes from a cover image sitting next to the audio file, or null when folder covers
     * are not in use, no trusted cover exists, or the candidate is too large to bound safely.
     */
    internal fun readExternalAlbumArtBytes(
        filePath: String,
        enabled: Boolean,
        maxBytes: Long = MAX_EXTERNAL_ART_BYTES
    ): ByteArray? {
        if (!enabled) return null
        return externalAlbumArtCandidates(filePath).firstNotNullOfOrNull { artFile ->
            readExternalArtworkFileBytes(artFile, maxBytes)
        }
    }

    internal fun findExternalAlbumArtFile(filePath: String): File? =
        externalAlbumArtCandidates(filePath).firstOrNull()

    private fun externalAlbumArtCandidates(filePath: String): List<File> {
        val directory = File(filePath).parentFile ?: return emptyList()
        if (!directory.isDirectory || !shouldTrustDirectoryArtwork(directory.name)) return emptyList()

        // Android file systems are case-sensitive, but cover names frequently use .JPG or Cover.jpg.
        val files = directory.listFiles()?.filter { it.isFile && it.length() > 0 } ?: return emptyList()
        return commonArtworkFileNames.flatMap { name ->
            files.filter { it.name.equals(name, ignoreCase = true) }.sortedBy { it.name }
        }
    }

    /** Shared validation for folder album covers and artist portraits. */
    internal fun isUsableExternalArtwork(file: File): Boolean =
        readExternalArtworkFileBytes(file) != null

    internal fun readExternalArtworkFileBytes(
        file: File,
        maxBytes: Long = MAX_EXTERNAL_ART_BYTES
    ): ByteArray? = runCatching {
        if (!file.isFile || !file.canRead() || file.length() > maxBytes) return null
        val bytes = file.inputStream().use { readArtworkBytesWithinLimit(it, maxBytes) } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Bounds alone also succeed for some truncated images. Check a small decoded thumbnail
        // before allowing an arbitrary sibling file to override embedded artwork.
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateArtworkInSampleSize(bounds.outWidth, bounds.outHeight, 64)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        decoded.recycle()
        bytes
    }.getOrNull()

    internal fun readArtworkBytesWithinLimit(input: InputStream, maxBytes: Long): ByteArray? {
        if (maxBytes <= 0) return null
        return ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                // Read at most one byte beyond the limit, even if the file grows after stat().
                val remaining = maxBytes - total
                val readLength = if (remaining >= buffer.size) buffer.size else remaining.toInt() + 1
                val count = input.read(buffer, 0, readLength)
                if (count == -1) break
                total += count
                if (total > maxBytes) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray().takeIf { it.isNotEmpty() }
        }
    }

    internal fun shouldTrustDirectoryArtwork(directoryName: String): Boolean {
        val normalized = directoryName.trim().lowercase()
        if (normalized.isBlank()) return false
        return normalized !in genericMixedDirectoryNames
    }

    /**
     * MediaStore's album-art cache can alias unrelated local songs when album metadata is weak or
     * collapsed into "Unknown Album". Keep this helper available for controlled callers, but do
     * not use it as an automatic per-song fallback.
     */
    fun getMediaStoreAlbumArtUri(appContext: Context, albumId: Long): Uri? {
        if (albumId <= 0) return null

        val potentialUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId
        )

        return try {
            appContext.contentResolver.openFileDescriptor(potentialUri, "r")?.use {
                potentialUri
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save embedded art to cache with unique naming
     */
    fun saveAlbumArtToCache(appContext: Context, bytes: ByteArray, songId: Long): Uri {
        val file = cacheAlbumArtBytes(appContext, bytes, songId)
        return shareableCacheUri(appContext, file)
    }

    /**
     * Delete both the cached artwork and the "no art" marker for a specific song.
     */
    fun clearCacheForSong(appContext: Context, songId: Long) {
        listOf(
            getCachedAlbumArtFile(appContext, songId),
            noArtMarkerFile(appContext, songId),
            legacyCachedAlbumArtFile(appContext, songId, "_v3"),
            legacyNoArtMarkerFile(appContext, songId, "_v3"),
            legacyCachedAlbumArtFile(appContext, songId, "_v2"),
            legacyNoArtMarkerFile(appContext, songId, "_v2"),
            legacyCachedAlbumArtFile(appContext, songId),
            legacyNoArtMarkerFile(appContext, songId)
        ).forEach { it.delete() }
    }

    private const val ALBUM_ART_DIR_NAME = "album_art"

    fun getAlbumArtDir(appContext: Context): File {
        val dir = File(appContext.filesDir, ALBUM_ART_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCachedAlbumArtFile(appContext: Context, songId: Long): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${CACHE_VERSION_SUFFIX}.jpg")
    }

    /**
     * Moves any legacy album-art files from cacheDir (old location, wipeable by the OS)
     * into filesDir/album_art/. Idempotent — safe to call on every startup. Runs quickly
     * because it only lists files matching the `song_art_` prefix.
     */
    fun migrateLegacyCacheLocation(appContext: Context) {
        val oldDir = appContext.cacheDir
        val newDir = getAlbumArtDir(appContext)
        val legacyFiles = oldDir.listFiles { f ->
            f.isFile && f.name.startsWith("song_art_")
        } ?: return

        for (file in legacyFiles) {
            val target = File(newDir, file.name)
            if (target.exists()) {
                file.delete()
                continue
            }
            if (!file.renameTo(target)) {
                runCatching {
                    file.copyTo(target, overwrite = false)
                    file.delete()
                }
            }
        }
    }

    private fun cacheAlbumArtBytes(appContext: Context, bytes: ByteArray, songId: Long): File {
        val file = getCachedAlbumArtFile(appContext, songId)

        val boundedBytes = boundArtworkForCache(bytes)
        return writeAlbumArtBytes(appContext, file, boundedBytes, songId)
    }

    private fun cacheAlbumArtBytesIfCurrent(
        appContext: Context,
        bytes: ByteArray,
        songId: Long,
        generation: Long
    ): File? {
        val boundedBytes = boundArtworkForCache(bytes)
        return synchronized(artworkCacheWriteLock) {
            if (generation != artworkCacheGeneration.get()) return@synchronized null
            writeAlbumArtBytes(appContext, getCachedAlbumArtFile(appContext, songId), boundedBytes, songId)
        }
    }

    private fun writeAlbumArtBytes(
        appContext: Context,
        file: File,
        boundedBytes: ByteArray,
        songId: Long
    ): File {
        file.outputStream().use { outputStream ->
            outputStream.write(boundedBytes)
        }
        noArtMarkerFile(appContext, songId).delete()

        appScope.launch {
            AlbumArtCacheManager.cleanCacheIfNeeded(appContext, AlbumArtCacheManager.configuredCacheLimitMb)
        }

        return file
    }

    /**
     * Schedules a one-time, background re-compression of an oversized cached cover. The current
     * load still returns the existing file; subsequent loads get the bounded one. De-duplicated
     * per file so rapid repeated loads don't read the same large blob into memory twice.
     */
    private fun scheduleOversizedArtworkShrink(file: File) {
        if (file.length() <= OVERSIZED_CACHED_ART_BYTES) return
        val key = file.absolutePath
        if (!artworkShrinkInFlight.add(key)) return
        val generation = artworkCacheGeneration.get()
        appScope.launch {
            try {
                if (file.length() <= OVERSIZED_CACHED_ART_BYTES) return@launch
                val raw = runCatching { file.readBytes() }.getOrNull() ?: return@launch
                val bounded = boundArtworkForCache(raw)
                if (bounded.size >= raw.size) return@launch
                val tmp = File(file.parentFile, "${file.name}.shrink.tmp")
                runCatching {
                    tmp.outputStream().use { it.write(bounded) }
                    synchronized(artworkCacheWriteLock) {
                        if (generation == artworkCacheGeneration.get() && file.exists()) {
                            if (!tmp.renameTo(file)) {
                                file.outputStream().use { it.write(bounded) }
                            }
                        }
                        tmp.delete()
                    }
                }
            } finally {
                artworkShrinkInFlight.remove(key)
            }
        }
    }

    /**
     * Returns artwork bytes bounded for the display cache: longest edge at most
     * [MAX_CACHED_ART_DIMENSION_PX], re-encoded as JPEG when the source is oversized by
     * dimension or by on-disk size. Returns the original bytes unchanged when they are already
     * small enough or when decoding fails, so artwork is never dropped or needlessly re-encoded.
     */
    private fun boundArtworkForCache(bytes: ByteArray): ByteArray {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val srcWidth = bounds.outWidth
            val srcHeight = bounds.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return bytes

            val oversizedByDimension = maxOf(srcWidth, srcHeight) > MAX_CACHED_ART_DIMENSION_PX
            val oversizedBySize = bytes.size > OVERSIZED_CACHED_ART_BYTES
            if (!oversizedByDimension && !oversizedBySize) return bytes

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateArtworkInSampleSize(srcWidth, srcHeight, MAX_CACHED_ART_DIMENSION_PX)
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return bytes
            val scaled = scaleArtworkDownTo(decoded, MAX_CACHED_ART_DIMENSION_PX)
            val encoded = ByteArrayOutputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, CACHED_ART_JPEG_QUALITY, stream)
                stream.toByteArray()
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            if (encoded.isNotEmpty() && encoded.size < bytes.size) encoded else bytes
        } catch (e: Throwable) {
            Timber.tag("AlbumArtUtils").w(e, "Failed to bound artwork for cache; keeping original bytes")
            bytes
        }
    }

    /** Largest power-of-two subsample that keeps the decoded longest edge >= [maxDimensionPx]. */
    private fun calculateArtworkInSampleSize(srcWidth: Int, srcHeight: Int, maxDimensionPx: Int): Int {
        var inSampleSize = 1
        val longestEdge = maxOf(srcWidth, srcHeight)
        while (longestEdge / (inSampleSize * 2) >= maxDimensionPx) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /** Scales [src] so its longest edge is [maxDimensionPx]; returns [src] unchanged if already smaller. */
    private fun scaleArtworkDownTo(src: Bitmap, maxDimensionPx: Int): Bitmap {
        val longestEdge = maxOf(src.width, src.height)
        if (longestEdge <= maxDimensionPx) return src
        val scale = maxDimensionPx.toFloat() / longestEdge
        val targetWidth = (src.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }

    private fun noArtMarkerFile(appContext: Context, songId: Long): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${CACHE_VERSION_SUFFIX}_no.jpg")
    }

    private fun legacyCachedAlbumArtFile(
        appContext: Context,
        songId: Long,
        versionSuffix: String = ""
    ): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${versionSuffix}.jpg")
    }

    private fun legacyNoArtMarkerFile(
        appContext: Context,
        songId: Long,
        versionSuffix: String = ""
    ): File {
        return File(getAlbumArtDir(appContext), "song_art_${songId}${versionSuffix}_no.jpg")
    }

    private data class MediaStoreSongInfo(
        val path: String,
        val albumId: Long?
    )

    private fun resolveSongMediaStoreInfo(
        appContext: Context,
        songId: Long
    ): MediaStoreSongInfo? {
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(songId.toString())
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        return runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                MediaStoreSongInfo(
                    path = path,
                    albumId = albumId.takeIf { it > 0L }
                )
            }
        }.getOrNull()
    }

    private fun extractEmbeddedAlbumArtBytes(filePath: String): ByteArray? {
        val retrieverArtwork = MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
            } catch (e: IllegalArgumentException) {
                try {
                    FileInputStream(filePath).use { fis ->
                        retriever.setDataSource(fis.fd)
                    }
                } catch (e2: Exception) {
                    return@withRetriever null
                }
            }

            retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
        }

        if (retrieverArtwork != null) {
            return retrieverArtwork
        }

        return runCatching {
            AudioMetadataReader.read(File(filePath))?.artwork?.bytes?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun shareableCacheUri(appContext: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }
}

internal fun resolveAlbumArtUriForLibraryScan(
    songId: Long,
    hasCachedArtwork: Boolean,
    hasNoArtworkMarker: Boolean,
    forceRefresh: Boolean
): String? {
    if (hasCachedArtwork) {
        return LocalArtworkUri.buildSongUri(songId)
    }
    if (hasNoArtworkMarker && !forceRefresh) {
        return null
    }
    return LocalArtworkUri.buildSongUri(songId)
}
