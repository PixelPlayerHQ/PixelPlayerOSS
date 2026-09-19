package com.lostf1sh.pixelplayeross.presentation.components

import android.content.Context
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class LocalArtworkImageModelTest {
    @Test
    fun `cache revisions change local request data while preserving song and edit token`() {
        val uri = "${LocalArtworkUri.buildSongUri(42L)}?t=123"

        val before = withLocalArtworkCacheVersion(uri, 1L) as String
        val after = withLocalArtworkCacheVersion(uri, 2L) as String

        assertThat(after).isNotEqualTo(before)
        assertThat(LocalArtworkUri.parseSongId(after)).isEqualTo(42L)
        assertThat(LocalArtworkUri.extractCacheBustToken(after)).isEqualTo("123")
        assertThat(after).contains("artwork_version=2")
    }

    @Test
    fun `cloud custom and direct image models retain their identities`() {
        listOf(
            "https://music.example/cover.jpg",
            "content://custom.provider/image/42",
            "/storage/emulated/0/Pictures/custom.jpg",
            Any(),
            null
        ).forEach { model ->
            assertThat(withLocalArtworkCacheVersion(model, 2L)).isSameInstanceAs(model)
        }
        val request = mockk<ImageRequest>()
        every { request.data } returns "https://music.example/cover.jpg"
        assertThat(withLocalArtworkCacheVersion(request, 2L)).isSameInstanceAs(request)
    }

    @Test
    fun `local requests change custom cache keys and discard stale placeholders`() {
        val uri = LocalArtworkUri.buildSongUri(42L)
        val context = mockk<Context>()
        val request = mockk<ImageRequest>()
        val builder = mockk<ImageRequest.Builder>()
        val updated = mockk<ImageRequest>()
        val originalKey = MemoryCache.Key("custom-album-art", mapOf("size" to "128"))
        every { request.data } returns uri
        every { request.context } returns context
        every { request.memoryCacheKey } returns originalKey
        every { request.newBuilder(context) } returns builder
        every { builder.data(any()) } returns builder
        every { builder.memoryCacheKey(any<MemoryCache.Key>()) } returns builder
        every { builder.placeholderMemoryCacheKey(null as MemoryCache.Key?) } returns builder
        every { builder.diskCachePolicy(CachePolicy.DISABLED) } returns builder
        every { builder.build() } returns updated

        assertThat(withLocalArtworkCacheVersion(request, 3L)).isSameInstanceAs(updated)
        verify { builder.data("$uri?artwork_version=3") }
        verify {
            builder.memoryCacheKey(MemoryCache.Key("custom-album-art_artwork_version_3", originalKey.extras))
        }
        verify { builder.placeholderMemoryCacheKey(null as MemoryCache.Key?) }
    }
}
