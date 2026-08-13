package com.lostf1sh.pixelplayeross.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.lostf1sh.pixelplayeross.utils.AlbumArtUtils
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64
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
        return if (
            parseSongId(uri, appContext.packageName) != null ||
            parseCloudArtworkUri(uri.toString(), appContext.packageName) != null
        ) {
            DEFAULT_CONTENT_TYPE
        } else {
            null
        }
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
        val cloudArtworkUri = parseCloudArtworkUri(uri.toString(), appContext.packageName)
        if (cloudArtworkUri != null) {
            return openCloudArtworkPipe(appContext, cloudArtworkUri)
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

    private fun openCloudArtworkPipe(
        appContext: Context,
        rawArtworkUri: String,
    ): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        cloudArtworkScope.launch {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                val request = ImageRequest.Builder(appContext)
                    .data(Uri.parse(rawArtworkUri))
                    .size(CLOUD_ARTWORK_SIZE_PX, CLOUD_ARTWORK_SIZE_PX)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
                val drawable = appContext.imageLoader.execute(request).drawable ?: return@use
                val fallbackSizePx = CLOUD_ARTWORK_SIZE_PX
                val bitmap = drawable.toBitmap(
                    width = drawable.intrinsicWidth.takeIf { it > 0 } ?: fallbackSizePx,
                    height = drawable.intrinsicHeight.takeIf { it > 0 } ?: fallbackSizePx,
                    config = Bitmap.Config.ARGB_8888,
                )
                bitmap.compress(Bitmap.CompressFormat.JPEG, CLOUD_ARTWORK_JPEG_QUALITY, output)
            }
        }
        return pipe[0]
    }

    companion object {
        private const val AUTHORITY_SUFFIX = ".artwork"
        private const val PATH_SONG = "song"
        private const val PATH_CLOUD = "cloud"
        private const val DEFAULT_CONTENT_TYPE = "image/jpeg"
        private const val CLOUD_ARTWORK_SIZE_PX = 1024
        private const val CLOUD_ARTWORK_JPEG_QUALITY = 90
        private val CLOUD_ARTWORK_SCHEMES = setOf("navidrome_cover", "jellyfin_cover")
        private val cloudArtworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    }
}
