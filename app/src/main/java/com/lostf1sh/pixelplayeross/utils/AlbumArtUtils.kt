package com.lostf1sh.pixelplayeross.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lostf1sh.pixelplayeross.data.media.AudioMetadataReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object AlbumArtUtils {
    private const val CACHE_VERSION_SUFFIX = "_v4"

    // P2-1: Dedicated app-level scope to replace GlobalScope.
    // SupervisorJob ensures child failures don't cancel sibling coroutines.
    // Appropriate for fire-and-forget tasks like cache cleanup that outlive any single component.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
     * Local artwork is intentionally embedded-only. Falling back to folder images such as
     * cover.jpg/thumb.jpg can pick unrelated Gallery files when music is stored in mixed
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
        val cachedFile = getCachedAlbumArtFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (!forceRefresh) {
            if (cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile.setLastModified(System.currentTimeMillis())
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

        extractEmbeddedAlbumArtBytes(resolvedPath)?.let { bytes ->
            cacheAlbumArtBytes(appContext, bytes, songId)
            return cachedFile.takeIf { it.exists() && it.length() > 0 }
        }

        cachedFile.delete()
        noArtFile.createNewFile()
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

        val hasEmbeddedArt = extractEmbeddedAlbumArtBytes(filePath)?.isNotEmpty() == true
        if (hasEmbeddedArt) {
            noArtFile.delete()
            return true
        }

        cachedFile.delete()
        noArtFile.createNewFile()
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

    internal fun findExternalAlbumArtFile(filePath: String): File? {
        val audioFile = File(filePath)
        val directory = audioFile.parentFile ?: return null
        if (!directory.exists() || !directory.isDirectory) return null
        if (!shouldTrustDirectoryArtwork(directory.name)) return null

        return commonArtworkFileNames
            .asSequence()
            .map { name -> File(directory, name) }
            .firstOrNull { artFile ->
                artFile.exists() && artFile.isFile && artFile.length() > 1024
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
                potentialUri // only return if open succeeded
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

    // Album art lives in filesDir (persistent) instead of cacheDir, because Android can
    // wipe cacheDir at any time under storage pressure — taking every cached cover with
    // it and leaving the UI blank. The size is bounded by AlbumArtCacheManager's LRU.
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

        file.outputStream().use { outputStream ->
            outputStream.write(bytes)
        }
        noArtMarkerFile(appContext, songId).delete()

        // Trigger async cache cleanup if needed
        appScope.launch {
            AlbumArtCacheManager.cleanCacheIfNeeded(appContext, AlbumArtCacheManager.configuredCacheLimitMb)
        }

        return file
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
