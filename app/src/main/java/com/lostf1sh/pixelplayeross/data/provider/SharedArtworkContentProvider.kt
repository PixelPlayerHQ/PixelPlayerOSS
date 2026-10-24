package com.lostf1sh.pixelplayeross.data.provider

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.lostf1sh.pixelplayeross.data.image.RemoteArtworkCache
import com.lostf1sh.pixelplayeross.utils.AlbumArtUtils
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SharedArtworkContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        val appContext = context?.applicationContext ?: return null
        val cloudArtworkUri = parseCloudArtworkUri(uri.toString(), appContext.packageName)
        if (cloudArtworkUri != null) {
            // Cloud covers are served as cached, in whatever format the server sent.
            return RemoteArtworkCache.cacheFileFor(appContext.cacheDir, cloudArtworkUri)
                ?.let(RemoteArtworkCache::imageMimeType)
                ?: DEFAULT_CONTENT_TYPE
        }
        return if (parseSongId(uri, appContext.packageName) != null) DEFAULT_CONTENT_TYPE else null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw FileNotFoundException("Shared artwork provider is read-only")
        }

        val appContext = context?.applicationContext
            ?: throw FileNotFoundException("Artwork provider is unavailable")
        enforceArtworkReadAccess(uri, appContext)
        val cloudArtworkUri = parseCloudArtworkUri(uri.toString(), appContext.packageName)
        if (cloudArtworkUri != null) {
            return openCachedCloudArtwork(appContext, cloudArtworkUri)
        }

        val file = resolveArtworkFile(uri)
            ?: throw FileNotFoundException("No artwork found for uri=$uri")

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val fileDescriptor = openFile(uri, mode)
        return AssetFileDescriptor(fileDescriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    private fun resolveArtworkFile(uri: Uri): File? {
        val appContext = context?.applicationContext ?: return null
        val songId = parseSongId(uri, appContext.packageName) ?: return null
        return AlbumArtUtils.ensureAlbumArtCachedFile(
            appContext = appContext,
            songId = songId
        )?.takeIf { it.exists() && it.isFile && it.canRead() }
    }

    /**
     * Some vendor System UI implementations reject URI grants before attempting to open a
     * non-exported provider. The provider is therefore exported for compatibility, while every
     * file open still requires our own UID, the explicit per-item grant issued by the media
     * session, or a privileged media surface reading what the session currently publishes.
     *
     * System UI's media controls read the platform session's artwork URI without ever
     * connecting as a Media3 controller, so they never receive a per-item grant. They hold
     * MEDIA_CONTENT_CONTROL (signature|privileged) and already see the session's metadata;
     * they may open only the artwork that metadata currently points at, not arbitrary songs.
     */
    private fun enforceArtworkReadAccess(uri: Uri, appContext: Context) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val permissionResult = appContext.checkUriPermission(
            uri,
            callingPid,
            callingUid,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val mediaControlResult = appContext.checkPermission(
            Manifest.permission.MEDIA_CONTENT_CONTROL,
            callingPid,
            callingUid,
        )
        if (
            !hasArtworkReadAccess(
                callingUid = callingUid,
                providerUid = appContext.applicationInfo.uid,
                uriPermissionResult = permissionResult,
                mediaContentControlResult = mediaControlResult,
                isPublishedSessionArtwork = isPublishedSessionArtwork(uri.toString(), appContext.packageName),
            )
        ) {
            // FileNotFoundException is intentionally used here: media clients commonly treat a
            // missing cover as optional, whereas vendor System UI code may crash on SecurityException.
            throw FileNotFoundException("Artwork URI has not been granted to the caller")
        }
    }

    /**
     * Serves cloud artwork only from the Navidrome/Jellyfin disk cache, never from the network.
     *
     * Callers read this synchronously: Bluetooth's AVRCP target (it holds MEDIA_CONTENT_CONTROL,
     * so it may open the published cover) decodes the session's artwork URI on the Bluetooth
     * process's main thread, several times per metadata or playback-state change. Downloading
     * and re-encoding the cover inside that read stalled com.android.bluetooth until it crashed
     * or dropped the headset while streaming. The cached file is the server-resized cover the
     * fetchers already store for the app's own UI and notification, so it is small and opens
     * instantly; a cover that is not cached yet fails fast and is fetched in the background
     * for the next read. Media3 also publishes the decoded bitmap, which these surfaces prefer.
     */
    private fun openCachedCloudArtwork(
        appContext: Context,
        rawArtworkUri: String,
    ): ParcelFileDescriptor {
        val cachedFile = resolveCachedCloudArtwork(appContext.cacheDir, rawArtworkUri)
        if (cachedFile == null) {
            warmCloudArtworkCache(appContext, rawArtworkUri)
            throw FileNotFoundException("Cloud artwork is not cached yet")
        }
        return ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun warmCloudArtworkCache(appContext: Context, rawArtworkUri: String) {
        if (!cloudArtworkWarmups.add(rawArtworkUri)) return
        cloudArtworkScope.launch {
            try {
                val request = ImageRequest.Builder(appContext)
                    .data(Uri.parse(rawArtworkUri))
                    .size(RemoteArtworkCache.DEFAULT_COVER_SIZE_PX)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
                appContext.imageLoader.execute(request)
            } finally {
                cloudArtworkWarmups.remove(rawArtworkUri)
            }
        }
    }

    companion object {
        private const val AUTHORITY_SUFFIX = ".artwork"
        private const val PATH_SONG = "song"
        private const val PATH_CLOUD = "cloud"
        private const val DEFAULT_CONTENT_TYPE = "image/jpeg"
        private val CLOUD_ARTWORK_SCHEMES = setOf("navidrome_cover", "jellyfin_cover")
        private val cloudArtworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cloudArtworkWarmups: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /*
         * Covers are cached server-resized to about 500 px, i.e. tens to a few hundred KB. A
         * larger file means the server ignored the size and sent the original, which readers
         * such as Bluetooth would decode at full resolution in their own process.
         */
        internal const val MAX_SHARED_CLOUD_ARTWORK_BYTES = 2L * 1024 * 1024

        /*
         * The byte limit doesn't bound the decoded size: a highly compressed original can be
         * small on disk and still decode to a huge bitmap, so the dimensions are checked too.
         */
        internal const val MAX_SHARED_CLOUD_ARTWORK_DIMENSION_PX = 2048

        /** The cached cover file for [rawArtworkUri] if it can be served as-is, else null. */
        internal fun resolveCachedCloudArtwork(
            cacheDir: File,
            rawArtworkUri: String,
            readArtworkImageBounds: (File) -> ArtworkImageBounds? = ::decodeArtworkImageBounds,
        ): File? {
            if (!isSupportedCloudArtworkUri(rawArtworkUri)) return null
            return RemoteArtworkCache.cacheFileFor(cacheDir, rawArtworkUri)
                ?.takeIf { it.length() <= MAX_SHARED_CLOUD_ARTWORK_BYTES }
                ?.takeIf(RemoteArtworkCache::isCachedImage)
                ?.takeIf { file ->
                    val bounds = readArtworkImageBounds(file) ?: return@takeIf false
                    bounds.width <= MAX_SHARED_CLOUD_ARTWORK_DIMENSION_PX &&
                        bounds.height <= MAX_SHARED_CLOUD_ARTWORK_DIMENSION_PX
                }
        }

        /** Reads only the image header; null when the platform can't decode it. */
        private fun decodeArtworkImageBounds(file: File): ArtworkImageBounds? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            return ArtworkImageBounds(options.outWidth, options.outHeight)
                .takeIf { it.width > 0 && it.height > 0 }
        }

        fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

        fun buildSongUri(
            context: Context,
            songId: Long,
            cacheBustToken: String? = null
        ): Uri = buildSongUri(context.packageName, songId, cacheBustToken)

        internal fun buildSongUri(
            packageName: String,
            songId: Long,
            cacheBustToken: String? = null
        ): Uri {
            return Uri.parse(buildSongUriString(packageName, songId, cacheBustToken))
        }

        fun buildCloudUri(context: Context, rawArtworkUri: String): Uri? {
            return buildCloudUriString(context.packageName, rawArtworkUri)?.let(Uri::parse)
        }

        internal fun buildCloudUriString(
            packageName: String,
            rawArtworkUri: String,
        ): String? {
            if (!isSupportedCloudArtworkUri(rawArtworkUri)) return null
            val encodedUri = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawArtworkUri.toByteArray(Charsets.UTF_8))
            return "content://${authority(packageName)}/$PATH_CLOUD/$encodedUri"
        }

        internal fun parseCloudArtworkUri(
            uriString: String,
            packageName: String? = null,
        ): String? {
            val expectedPrefix = packageName
                ?.let(::authority)
                ?.let { "content://$it/$PATH_CLOUD/" }
                ?: return null
            if (!uriString.startsWith(expectedPrefix)) return null

            val encodedUri = uriString
                .removePrefix(expectedPrefix)
                .substringBefore('?')
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
                ?: return null
            val rawArtworkUri = runCatching {
                String(Base64.getUrlDecoder().decode(encodedUri), Charsets.UTF_8)
            }.getOrNull() ?: return null
            return rawArtworkUri.takeIf(::isSupportedCloudArtworkUri)
        }

        private fun isSupportedCloudArtworkUri(rawArtworkUri: String): Boolean {
            val scheme = rawArtworkUri.substringBefore(':', missingDelimiterValue = "")
                .lowercase()
            return scheme in CLOUD_ARTWORK_SCHEMES && rawArtworkUri.startsWith("$scheme://")
        }

        internal fun buildSongUriString(
            packageName: String,
            songId: Long,
            cacheBustToken: String? = null
        ): String {
            val baseUri = "content://${authority(packageName)}/$PATH_SONG/$songId"
            return cacheBustToken
                ?.takeIf { it.isNotBlank() }
                ?.let { "$baseUri?t=$it" }
                ?: baseUri
        }

        internal fun parseSongId(uri: Uri, packageName: String? = null): Long? {
            return parseSongId(uri.toString(), packageName)
        }

        internal fun parseSongId(uriString: String, packageName: String? = null): Long? {
            val expectedPrefix = packageName
                ?.let(::authority)
                ?.let { "content://$it/$PATH_SONG/" }

            if (expectedPrefix != null && !uriString.startsWith(expectedPrefix)) {
                return null
            }

            val basePrefix = expectedPrefix ?: run {
                val authoritySeparator = "://"
                val schemeSplit = uriString.indexOf(authoritySeparator)
                if (schemeSplit < 0) return null
                val pathStart = uriString.indexOf('/', schemeSplit + authoritySeparator.length)
                if (pathStart < 0) return null
                val pathPrefix = uriString.substring(pathStart)
                if (!pathPrefix.startsWith("/$PATH_SONG/")) return null
                uriString.substring(0, pathStart) + "/$PATH_SONG/"
            }

            val songIdSegment = uriString
                .removePrefix(basePrefix)
                .substringBefore('?')
                .substringBefore('/')

            if (songIdSegment.isBlank()) {
                return null
            }
            return songIdSegment.toLongOrNull()
        }

        internal fun hasArtworkReadAccess(
            callingUid: Int,
            providerUid: Int,
            uriPermissionResult: Int,
            mediaContentControlResult: Int,
            isPublishedSessionArtwork: Boolean,
        ): Boolean {
            return callingUid == providerUid ||
                uriPermissionResult == PackageManager.PERMISSION_GRANTED ||
                (mediaContentControlResult == PackageManager.PERMISSION_GRANTED && isPublishedSessionArtwork)
        }

        /*
         * Artwork the media session currently publishes to platform surfaces, keyed by what the
         * URI points at (song id or raw cloud URI) so cache-bust tokens don't matter. A few recent
         * entries are kept because System UI may load a cover just after the track changes.
         */
        private const val MAX_PUBLISHED_SESSION_ARTWORK = 3
        private val publishedSessionArtwork = ArrayDeque<String>(MAX_PUBLISHED_SESSION_ARTWORK)

        /** Records artwork exposed through the session's current metadata. */
        fun publishSessionArtwork(packageName: String, artworkUri: String) {
            val key = artworkKey(artworkUri, packageName) ?: return
            synchronized(publishedSessionArtwork) {
                if (publishedSessionArtwork.lastOrNull() == key) return
                publishedSessionArtwork.remove(key)
                publishedSessionArtwork.addLast(key)
                while (publishedSessionArtwork.size > MAX_PUBLISHED_SESSION_ARTWORK) {
                    publishedSessionArtwork.removeFirst()
                }
            }
        }

        internal fun isPublishedSessionArtwork(uriString: String, packageName: String): Boolean {
            val key = artworkKey(uriString, packageName) ?: return false
            return synchronized(publishedSessionArtwork) { key in publishedSessionArtwork }
        }

        internal fun clearPublishedSessionArtwork() {
            synchronized(publishedSessionArtwork) { publishedSessionArtwork.clear() }
        }

        private fun artworkKey(uriString: String, packageName: String): String? =
            parseSongId(uriString, packageName)?.let { "song:$it" }
                ?: parseCloudArtworkUri(uriString, packageName)?.let { "cloud:$it" }
    }
}

internal data class ArtworkImageBounds(val width: Int, val height: Int)
