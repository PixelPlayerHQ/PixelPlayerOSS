package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerApiService
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerArtist
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerSearchResponse
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.utils.AlbumArtUtils
import com.lostf1sh.pixelplayeross.utils.FolderArtistArtUtils
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtistImageRepositoryTest {

    @Test
    fun `calculateCustomImageSampleSize keeps small bitmaps at full resolution`() {
        assertEquals(1, ArtistImageRepository.calculateCustomImageSampleSize(1024, 1024))
    }

    @Test
    fun `calculateCustomImageSampleSize aggressively downsamples oversized inputs`() {
        val sampleSize = ArtistImageRepository.calculateCustomImageSampleSize(12000, 8000)

        assertTrue(sampleSize >= 4)
        assertEquals(8, sampleSize)
    }

    @Test
    fun `cancelled prefetch does not mark artist as failed for the session`() = runTest {
        val deezerApiService = mockk<DeezerApiService>()
        val musicDao = mockk<MusicDao>()
        val repository = ArtistImageRepository(deezerApiService, musicDao, userPreferencesRepository(), mockk())
        val firstAttemptStarted = CompletableDeferred<Unit>()
        val searchAttempts = AtomicInteger(0)
        val rawUrl = "https://cdn-images.dzcdn.net/images/artist/250x250-000000-80-0-0.jpg"
        val upgradedUrl = "https://cdn-images.dzcdn.net/images/artist/1000x1000-000000-80-0-0.jpg"

        coEvery { musicDao.getArtistIdByNormalizedName("Artist Name") } returns 42L
        coEvery { musicDao.getArtistImageUrl(42L) } returns null
        coEvery { musicDao.getArtistImageUrlByNormalizedName("Artist Name") } returns null
        coJustRun { musicDao.updateArtistImageUrl(42L, any()) }
        coEvery { deezerApiService.searchArtist("Artist Name", 1) } coAnswers {
            when (searchAttempts.incrementAndGet()) {
                1 -> {
                    firstAttemptStarted.complete(Unit)
                    awaitCancellation()
                }

                else -> DeezerSearchResponse(
                    data = listOf(
                        DeezerArtist(
                            id = 7L,
                            name = "Artist Name",
                            pictureBig = rawUrl
                        )
                    )
                )
            }
        }

        val prefetchJob = launch {
            repository.prefetchArtistImages(listOf(42L to "Artist Name"))
        }
        firstAttemptStarted.await()
        prefetchJob.cancel()
        prefetchJob.join()

        val imageUrl = repository.getArtistImageUrl("Artist Name", 42L)

        assertEquals(upgradedUrl, imageUrl)
        assertEquals(2, searchAttempts.get())
        coVerify(exactly = 1) { musicDao.updateArtistImageUrl(42L, upgradedUrl) }
    }

    @Test
    fun `disabled artist image lookup does not call deezer`() = runTest {
        val deezerApiService = mockk<DeezerApiService>(relaxed = true)
        val musicDao = mockk<MusicDao>(relaxed = true)
        val repository = ArtistImageRepository(
            deezerApiService,
            musicDao,
            userPreferencesRepository(externalArtistImagesEnabled = false),
            mockk()
        )

        val imageUrl = repository.getArtistImageUrl("Artist Name", 42L)

        assertEquals(null, imageUrl)
        coVerify(exactly = 0) { deezerApiService.searchArtist(any(), any()) }
    }

    @Test
    fun `folder portrait is preferred even with third party artist images disabled`() = runTest {
        val deezerApiService = mockk<DeezerApiService>()
        val musicDao = mockk<MusicDao>()
        val context = mockk<Context>()
        val image = File("/Music/Artist Name/artist.jpg")
        val directories = listOf("/Music/Artist Name/Album")
        val repository = ArtistImageRepository(
            deezerApiService,
            musicDao,
            userPreferencesRepository(externalArtistImagesEnabled = false, folderArtworkEnabled = true),
            context
        )
        coEvery { musicDao.getArtistIdByNormalizedName("Artist Name") } returns 42L
        coEvery { musicDao.getLocalArtistDirectories(42L, 64) } returns directories
        mockkObject(AlbumArtUtils, FolderArtistArtUtils)
        try {
            every { AlbumArtUtils.canReadImageFiles(context) } returns true
            every { FolderArtistArtUtils.findArtistImage(directories, "Artist Name", any()) } returns image

            assertEquals(image.absolutePath, repository.getArtistImageUrl("Artist Name", 42L))

            coVerify(exactly = 0) { deezerApiService.searchArtist(any(), any()) }
            coVerify(exactly = 0) { musicDao.updateArtistImageUrl(any(), any()) }
        } finally {
            unmockkObject(AlbumArtUtils, FolderArtistArtUtils)
        }
    }

    @Test
    fun `manual artist image retains priority over opted in folder image`() = runTest {
        val deezerApiService = mockk<DeezerApiService>()
        val musicDao = mockk<MusicDao>()
        val preferences = userPreferencesRepository(folderArtworkEnabled = true)
        val repository = ArtistImageRepository(deezerApiService, musicDao, preferences, mockk())
        coEvery { musicDao.getArtistCustomImage(42L) } returns "/internal/custom_artist.jpg"

        assertEquals(
            "/internal/custom_artist.jpg",
            repository.getEffectiveArtistImageUrl(42L, "Artist Name")
        )
        coVerify(exactly = 0) { musicDao.getLocalArtistDirectories(any(), any()) }
        verify(exactly = 0) { preferences.useFolderAlbumArtFlow }
        coVerify(exactly = 0) { deezerApiService.searchArtist(any(), any()) }
    }

    @Test
    fun `disabling folder artwork restores persisted third party image without replacing it`() = runTest {
        val deezerApiService = mockk<DeezerApiService>()
        val musicDao = mockk<MusicDao>()
        val context = mockk<Context>()
        val enabled = MutableStateFlow(true)
        val preferences = userPreferencesRepository()
        every { preferences.useFolderAlbumArtFlow } returns enabled
        val repository = ArtistImageRepository(deezerApiService, musicDao, preferences, context)
        val directories = listOf("/Music/Artist Name/Album")
        val image = File("/Music/Artist Name/artist.jpg")
        val remoteImage = "https://example.com/artist.jpg"
        coEvery { musicDao.getLocalArtistDirectories(42L, 64) } returns directories
        coEvery { musicDao.getArtistIdByNormalizedName("Artist Name") } returns 42L
        coEvery { musicDao.getArtistImageUrl(42L) } returns remoteImage
        mockkObject(AlbumArtUtils, FolderArtistArtUtils)
        try {
            every { AlbumArtUtils.canReadImageFiles(context) } returns true
            every { FolderArtistArtUtils.findArtistImage(directories, "Artist Name", any()) } returns image

            assertEquals(image.absolutePath, repository.getArtistImageUrl("Artist Name", 42L))
            enabled.value = false
            assertEquals(remoteImage, repository.getArtistImageUrl("Artist Name", 42L))

            coVerify(exactly = 0) { deezerApiService.searchArtist(any(), any()) }
            coVerify(exactly = 0) { musicDao.updateArtistImageUrl(any(), any()) }
        } finally {
            unmockkObject(AlbumArtUtils, FolderArtistArtUtils)
        }
    }

    @Test
    fun `folder portrait lookup skips disk discovery without image permission`() = runTest {
        val musicDao = mockk<MusicDao>()
        val context = mockk<Context>()
        val repository = ArtistImageRepository(
            mockk(),
            musicDao,
            userPreferencesRepository(folderArtworkEnabled = true),
            context
        )
        mockkObject(AlbumArtUtils)
        try {
            every { AlbumArtUtils.canReadImageFiles(context) } returns false

            assertEquals(null, repository.getFolderArtistImageUrl(42L, "Artist Name"))

            coVerify(exactly = 0) { musicDao.getLocalArtistDirectories(any(), any()) }
        } finally {
            unmockkObject(AlbumArtUtils)
        }
    }

    @Test
    fun `clearing images signals folder image consumers to reload`() {
        val repository = ArtistImageRepository(mockk(), mockk(), userPreferencesRepository(), mockk())

        repository.clearCache()

        assertEquals(1L, repository.folderArtworkRevision.value)
    }

    private fun userPreferencesRepository(
        externalArtistImagesEnabled: Boolean = true,
        folderArtworkEnabled: Boolean = false
    ): UserPreferencesRepository {
        return mockk {
            every { this@mockk.externalArtistImagesEnabledFlow } returns flowOf(externalArtistImagesEnabled)
            every { this@mockk.useFolderAlbumArtFlow } returns flowOf(folderArtworkEnabled)
        }
    }
}
