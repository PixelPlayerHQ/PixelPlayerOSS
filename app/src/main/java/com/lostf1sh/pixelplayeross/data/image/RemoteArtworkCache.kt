package com.lostf1sh.pixelplayeross.data.image

import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.SourceResult
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.io.IOException

/**
 * Disk cache shared by the Navidrome and Jellyfin Coil fetchers.
 *
 * Only bodies that carry an image signature are cached: Subsonic servers answer failed
 * `getCoverArt` calls with HTTP 200 and a JSON/XML error, and caching that body would pin a
 * broken cover until the cache is cleared. Downloads land in a temp file that is renamed into
 * place, so a concurrent request never decodes a file another request is still writing.
 */
internal object RemoteArtworkCache {

    private const val SIGNATURE_BYTES = 12

    fun cachedResult(file: File): SourceResult? {
        if (!file.isFile || file.length() == 0L) return null
        if (!hasImageSignature(readSignature(file))) {
            file.delete()
            return null
        }
        return SourceResult(
            source = ImageSource(file = file.absolutePath.toPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    @Throws(IOException::class)
    fun download(client: OkHttpClient, request: Request, file: File): SourceResult {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val bytes = response.body.bytes()
            if (!hasImageSignature(bytes)) {
                throw IOException("Response is not an image (Content-Type: ${response.header("Content-Type")})")
            }

            val tempFile = File.createTempFile(file.name, ".tmp", file.parentFile)
            try {
                tempFile.writeBytes(bytes)
                if (!tempFile.renameTo(file)) throw IOException("Could not move artwork into $file")
            } finally {
                tempFile.delete()
            }

            return SourceResult(
                source = ImageSource(file = file.absolutePath.toPath(), fileSystem = FileSystem.SYSTEM),
                mimeType = response.header("Content-Type") ?: "image/jpeg",
                dataSource = DataSource.NETWORK
            )
        }
    }

    internal fun hasImageSignature(header: ByteArray): Boolean {
        fun matches(offset: Int, vararg expected: Int): Boolean =
            header.size >= offset + expected.size &&
                expected.indices.all { header[offset + it].toInt() and 0xFF == expected[it] }

        return matches(0, 0xFF, 0xD8, 0xFF) || // JPEG
            matches(0, 0x89, 0x50, 0x4E, 0x47) || // PNG
            matches(0, 0x47, 0x49, 0x46, 0x38) || // GIF8
            (matches(0, 0x52, 0x49, 0x46, 0x46) && matches(8, 0x57, 0x45, 0x42, 0x50)) || // RIFF....WEBP
            matches(4, 0x66, 0x74, 0x79, 0x70) || // ISO-BMFF ftyp (HEIF/AVIF)
            matches(0, 0x42, 0x4D) // BMP
    }

    private fun readSignature(file: File): ByteArray = runCatching {
        file.inputStream().use { input ->
            val buffer = ByteArray(SIGNATURE_BYTES)
            var read = 0
            while (read < SIGNATURE_BYTES) {
                val count = input.read(buffer, read, SIGNATURE_BYTES - read)
                if (count < 0) break
                read += count
            }
            buffer.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))
}
