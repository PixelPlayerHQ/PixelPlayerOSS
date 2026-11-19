package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ExternalLocalArtworkCacheTest {
    @TempDir
    lateinit var root: File

    private val context = mockk<Context> {
        every { filesDir } answers { root }
    }

    @Test
    fun `a cold cover queues a warmup without querying MediaStore`() {
        val warmups = mutableListOf<Long>()

        assertThat(AlbumArtUtils.getOrWarmCachedAlbumArtFile(context, 42L) { _, songId ->
            warmups.add(songId)
        }).isNull()

        assertThat(warmups).containsExactly(42L)
        verify(exactly = 0) { context.contentResolver }
    }

    @Test
    fun `a cached cover is served without queuing a warmup`() {
        val file = AlbumArtUtils.getCachedAlbumArtFile(context, 42L).apply { writeText("cover") }

        assertThat(AlbumArtUtils.getOrWarmCachedAlbumArtFile(context, 42L) { _, _ ->
            error("Artwork was already cached")
        }).isEqualTo(file)
        verify(exactly = 0) { context.contentResolver }
    }

    @Test
    fun `a song known to have no artwork does not queue another warmup`() {
        val file = AlbumArtUtils.getCachedAlbumArtFile(context, 42L)
        File(file.parentFile, "${file.nameWithoutExtension}_no.jpg").createNewFile()

        assertThat(AlbumArtUtils.getOrWarmCachedAlbumArtFile(context, 42L) { _, _ ->
            error("Missing artwork was already cached")
        }).isNull()
        verify(exactly = 0) { context.contentResolver }
    }
}
