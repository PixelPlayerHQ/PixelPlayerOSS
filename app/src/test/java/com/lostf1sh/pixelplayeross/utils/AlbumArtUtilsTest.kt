package com.lostf1sh.pixelplayeross.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.content.ContextCompat
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.media.AudioMetadataReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayInputStream
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlbumArtUtilsTest {

    @BeforeEach
    fun setUp() {
        // Android's decoder is stubbed on the JVM. A leading 7 represents a decoded image;
        // a leading 8 represents a truncated image with readable bounds but no bitmap.
        mockkStatic(BitmapFactory::class, ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val options = arg<BitmapFactory.Options>(3)
            val header = bytes.firstOrNull()?.toInt()
            options.outWidth = if (header == 7 || header == 8) 128 else -1
            options.outHeight = options.outWidth
            if (!options.inJustDecodeBounds && header == 7) mockk<Bitmap>(relaxed = true) else null
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(BitmapFactory::class, ContextCompat::class)
        unmockkObject(MediaMetadataRetrieverPool, AudioMetadataReader)
    }

    @Test
    fun canReadImageFiles_checksLegacyStoragePermissionBeforeAndroid13() {
        val context = mockk<Context>()
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        } returns PackageManager.PERMISSION_DENIED

        assertThat(AlbumArtUtils.canReadImageFiles(context, sdkInt = 32)).isFalse()
        verify { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) }
    }

    @Test
    fun canReadImageFiles_checksImagePermissionFromAndroid13() {
        val context = mockk<Context>()
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        } returns PackageManager.PERMISSION_DENIED

        assertThat(AlbumArtUtils.canReadImageFiles(context, sdkInt = 33)).isFalse()
        verify { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) }
    }

    @Test
    fun findExternalAlbumArtFile_returnsExplicitCoverFromDedicatedAlbumFolder() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Calvin Harris - Funk Wav Bounces").apply { mkdirs() }
        val songFile = albumDir.resolve("Feels.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val coverFile = albumDir.resolve("cover.jpg").apply { writeBytes(ByteArray(2048) { 7 }) }

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isEqualTo(coverFile)
        root.deleteRecursively()
    }

    @Test
    fun findExternalAlbumArtFile_ignoresLooseArtworkNames() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Singles").apply { mkdirs() }
        val songFile = albumDir.resolve("Random Song.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        albumDir.resolve("linkin_park_artwork_random.jpg").writeBytes(ByteArray(2048) { 9 })

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun findExternalAlbumArtFile_ignoresGenericDownloadsFolder() {
        val root = createTempDirectory("album-art-test").toFile()
        val downloadsDir = root.resolve("Downloads").apply { mkdirs() }
        val songFile = downloadsDir.resolve("Fresh Track.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        downloadsDir.resolve("cover.jpg").writeBytes(ByteArray(2048) { 5 })

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun findExternalAlbumArtFile_ignoresStudioGalleryFolder() {
        val root = createTempDirectory("album-art-test").toFile()
        val studioDir = root.resolve("Studio").apply { mkdirs() }
        val songFile = studioDir.resolve("Voice Note.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        studioDir.resolve("cover.jpg").writeBytes(ByteArray(2048) { 5 })

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun readExternalAlbumArtBytes_returnsCoverBytesWhenEnabled() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Daft Punk - Discovery").apply { mkdirs() }
        val songFile = albumDir.resolve("One More Time.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val coverBytes = ByteArray(2048) { 7 }
        albumDir.resolve("cover.jpg").writeBytes(coverBytes)

        val resolved = AlbumArtUtils.readExternalAlbumArtBytes(songFile.absolutePath, enabled = true)

        assertThat(resolved).isEqualTo(coverBytes)
        root.deleteRecursively()
    }

    @Test
    fun readExternalAlbumArtBytes_returnsNullWhenDisabled() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Daft Punk - Discovery").apply { mkdirs() }
        val songFile = albumDir.resolve("Aerodynamic.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        albumDir.resolve("cover.jpg").writeBytes(ByteArray(2048) { 7 })

        val resolved = AlbumArtUtils.readExternalAlbumArtBytes(songFile.absolutePath, enabled = false)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun readExternalAlbumArtBytes_returnsNullWhenCoverExceedsSizeLimit() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Daft Punk - Discovery").apply { mkdirs() }
        val songFile = albumDir.resolve("Digital Love.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        albumDir.resolve("cover.jpg").writeBytes(ByteArray(4096) { 7 })

        val resolved = AlbumArtUtils.readExternalAlbumArtBytes(
            filePath = songFile.absolutePath,
            enabled = true,
            maxBytes = 2048L
        )

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun readExternalAlbumArtBytes_stillHonoursExcludedDirectoriesWhenEnabled() {
        val root = createTempDirectory("album-art-test").toFile()
        val downloadsDir = root.resolve("Downloads").apply { mkdirs() }
        val songFile = downloadsDir.resolve("Fresh Track.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        downloadsDir.resolve("cover.jpg").writeBytes(ByteArray(2048) { 5 })

        val resolved = AlbumArtUtils.readExternalAlbumArtBytes(songFile.absolutePath, enabled = true)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun readExternalAlbumArtBytes_skipsCorruptCoverAndUsesNextImage() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            root.resolve("cover.jpg").writeBytes(ByteArray(2048) { 0 })
            val validBytes = ByteArray(2048) { 7 }
            root.resolve("folder.png").writeBytes(validBytes)

            assertThat(AlbumArtUtils.readExternalAlbumArtBytes(songFile.path, true)).isEqualTo(validBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readExternalAlbumArtBytes_rejectsTruncatedImagesEvenWhenBoundsAreReadable() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            root.resolve("cover.jpg").writeBytes(ByteArray(2048) { 8 })

            assertThat(AlbumArtUtils.readExternalAlbumArtBytes(songFile.path, true)).isNull()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readExternalAlbumArtBytes_skipsOversizedCoverAndUsesNextImage() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            root.resolve("cover.jpg").writeBytes(ByteArray(4096) { 7 })
            val validBytes = ByteArray(2048) { 7 }
            root.resolve("cover.png").writeBytes(validBytes)

            assertThat(AlbumArtUtils.readExternalAlbumArtBytes(songFile.path, true, 2048L))
                .isEqualTo(validBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readExternalAlbumArtBytes_acceptsSmallImagesAndCaseInsensitiveNames() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            val validBytes = ByteArray(128) { 7 }
            root.resolve("Cover.JPG").writeBytes(validBytes)

            assertThat(AlbumArtUtils.readExternalAlbumArtBytes(songFile.path, true)).isEqualTo(validBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readArtworkBytesWithinLimit_stopsWhenStreamExceedsLimit() {
        val input = ByteArrayInputStream(ByteArray(4096))

        assertThat(AlbumArtUtils.readArtworkBytesWithinLimit(input, 2048L)).isNull()
        assertThat(input.available()).isEqualTo(2047)
    }

    @Test
    fun readArtworkBytesWithinLimit_acceptsExactLimit() {
        val bytes = ByteArray(2048) { 7 }

        assertThat(AlbumArtUtils.readArtworkBytesWithinLimit(bytes.inputStream(), 2048L))
            .isEqualTo(bytes)
    }

    @Test
    fun getAlbumArtUri_fallsBackToEmbeddedArtWhenFolderCoverIsCorrupt() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            root.resolve("cover.jpg").writeBytes(ByteArray(2048) { 0 })
            val retriever = mockk<MediaMetadataRetriever>(relaxed = true)
            mockkObject(MediaMetadataRetrieverPool)
            every { MediaMetadataRetrieverPool.acquire() } returns retriever
            every { MediaMetadataRetrieverPool.release(any()) } returns Unit
            every { retriever.embeddedPicture } returns ByteArray(2048) { 7 }
            AlbumArtUtils.setFolderAlbumArtPreference(true)

            assertThat(AlbumArtUtils.getAlbumArtUri(context, songFile.path, 123L, false)).isNotNull()
            verify(exactly = 1) { retriever.embeddedPicture }
        } finally {
            AlbumArtUtils.setFolderAlbumArtPreference(false)
            root.deleteRecursively()
        }
    }

    @Test
    fun ensureAlbumArtCachedFile_doesNotRestoreArtAfterSettingsInvalidateAnInFlightRead() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            val retriever = mockk<MediaMetadataRetriever>(relaxed = true)
            mockkObject(MediaMetadataRetrieverPool)
            every { MediaMetadataRetrieverPool.acquire() } returns retriever
            every { MediaMetadataRetrieverPool.release(any()) } returns Unit
            every { retriever.embeddedPicture } answers {
                AlbumArtUtils.bumpArtworkCacheGeneration()
                ByteArray(2048) { 7 }
            }
            AlbumArtUtils.setFolderAlbumArtPreference(false)

            assertThat(AlbumArtUtils.ensureAlbumArtCachedFile(context, 123L, songFile.path)).isNull()
            assertThat(AlbumArtUtils.getAlbumArtDir(context).listFiles()).isEmpty()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ensureAlbumArtCachedFile_doesNotRestoreMissAfterSettingsInvalidateAnInFlightRead() {
        val root = createTempDirectory("album-art-test").toFile()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val songFile = root.resolve("Song.mp3").apply { writeBytes(byteArrayOf(1)) }
            val retriever = mockk<MediaMetadataRetriever>(relaxed = true)
            mockkObject(MediaMetadataRetrieverPool, AudioMetadataReader)
            every { MediaMetadataRetrieverPool.acquire() } returns retriever
            every { MediaMetadataRetrieverPool.release(any()) } returns Unit
            every { retriever.embeddedPicture } returns null
            every { AudioMetadataReader.read(songFile) } answers {
                AlbumArtUtils.bumpArtworkCacheGeneration()
                null
            }
            AlbumArtUtils.setFolderAlbumArtPreference(false)

            assertThat(AlbumArtUtils.ensureAlbumArtCachedFile(context, 123L, songFile.path)).isNull()
            assertThat(AlbumArtUtils.getAlbumArtDir(context).listFiles()).isEmpty()
        } finally {
            root.deleteRecursively()
        }
    }
}
