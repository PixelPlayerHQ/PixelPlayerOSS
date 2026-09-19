package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.media.ImageCacheManager
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemeProcessor
import dagger.Lazy
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class FolderArtworkSettingsCoordinatorTest {
    private val context = mockk<Context>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val artistImageRepository = mockk<ArtistImageRepository>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val colorSchemeProcessor = mockk<ColorSchemeProcessor>(relaxed = true)
    private val enabled = MutableStateFlow(false)
    private val recorded = MutableStateFlow<Boolean?>(false)
    private val setupDone = MutableStateFlow(true)
    private var mirrored = false
    private var hasImagePermission = true
    private lateinit var coordinator: FolderArtworkSettingsCoordinator

    @BeforeEach
    fun setUp() {
        mockkObject(AlbumArtUtils, AlbumArtCacheManager)
        every { preferences.useFolderAlbumArtFlow } returns enabled
        every { preferences.folderAlbumArtCacheStateFlow } returns recorded
        every { preferences.initialSetupDoneFlow } returns setupDone
        coEvery { preferences.setUseFolderAlbumArt(any()) } answers {
            enabled.value = firstArg()
        }
        coEvery { preferences.setFolderAlbumArtCacheState(any()) } answers {
            recorded.value = firstArg()
        }
        every { AlbumArtUtils.migrateLegacyCacheLocation(context) } just Runs
        every { AlbumArtUtils.setFolderAlbumArtPreference(any()) } answers {
            mirrored = firstArg()
        }
        every { AlbumArtUtils.isFolderAlbumArtEnabled(context) } answers {
            mirrored && hasImagePermission
        }
        every { AlbumArtUtils.bumpArtworkCacheGeneration() } just Runs
        every { AlbumArtUtils.notifyArtworkCacheInvalidated() } just Runs
        coEvery { AlbumArtCacheManager.clearAllCache(context) } returns 0
        coordinator = FolderArtworkSettingsCoordinator(
            context, preferences, imageCacheManager, artistImageRepository, syncManager,
            Lazy { colorSchemeProcessor }
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(AlbumArtUtils, AlbumArtCacheManager)
    }

    @Test
    fun `setting and observer reconcile share one invalidation`() = runTest {
        val clearStarted = CompletableDeferred<Unit>()
        val finishClear = CompletableDeferred<Unit>()
        coEvery { AlbumArtCacheManager.clearAllCache(context) } coAnswers {
            clearStarted.complete(Unit)
            finishClear.await()
            0
        }

        val setting = async { coordinator.setEnabled(true) }
        clearStarted.await()
        val observer = async { coordinator.reconcile() }
        finishClear.complete(Unit)
        setting.await()
        observer.await()

        assertThat(recorded.value).isTrue()
        assertThat(mirrored).isTrue()
        coVerify(exactly = 1) { AlbumArtCacheManager.clearAllCache(context) }
        verify(exactly = 1) { imageCacheManager.clearAllCoverArtCaches() }
        verify(exactly = 1) { artistImageRepository.clearCache() }
        verify(exactly = 1) { syncManager.fullSync() }
    }

    @Test
    fun `mounted artwork reloads only after file image and palette caches are cleared`() = runTest {
        enabled.value = true

        coordinator.reconcile()

        coVerifyOrder {
            AlbumArtCacheManager.clearAllCache(context)
            imageCacheManager.clearAllCoverArtCaches()
            colorSchemeProcessor.invalidateLocalArtworkSchemes()
            artistImageRepository.clearCache()
            AlbumArtUtils.notifyArtworkCacheInvalidated()
        }
    }

    @Test
    fun `second toggle waits for the first cache update before recording its own state`() = runTest {
        val clearStarted = CompletableDeferred<Unit>()
        val finishClear = CompletableDeferred<Unit>()
        coEvery { AlbumArtCacheManager.clearAllCache(context) } coAnswers {
            clearStarted.complete(Unit)
            finishClear.await()
            0
        }

        val firstToggle = async { coordinator.setEnabled(true) }
        clearStarted.await()
        val secondToggle = async { coordinator.setEnabled(false) }
        val foreground = async { coordinator.reconcile() }
        finishClear.complete(Unit)
        firstToggle.await()
        secondToggle.await()
        foreground.await()

        assertThat(enabled.value).isFalse()
        assertThat(mirrored).isFalse()
        assertThat(recorded.value).isFalse()
        coVerify(exactly = 2) { AlbumArtCacheManager.clearAllCache(context) }
        coVerify(exactly = 1) { preferences.setFolderAlbumArtCacheState(true) }
        coVerify(exactly = 1) { preferences.setFolderAlbumArtCacheState(false) }
    }

    @Test
    fun `permission regrant rescans songs with missing stored artwork without changing opt in`() = runTest {
        enabled.value = true
        hasImagePermission = false
        coordinator.reconcile()
        coVerify(exactly = 0) { AlbumArtCacheManager.clearAllCache(context) }

        hasImagePermission = true
        coordinator.reconcile()

        assertThat(recorded.value).isTrue()
        coVerify(exactly = 1) { AlbumArtCacheManager.clearAllCache(context) }
        coVerify(exactly = 0) { preferences.setUseFolderAlbumArt(any()) }
        verify(exactly = 1) { syncManager.fullSync() }
    }

    @Test
    fun `first launch clears unknown old cache without starting a scan before setup`() = runTest {
        recorded.value = null
        setupDone.value = false

        coordinator.reconcile()

        assertThat(recorded.value).isFalse()
        coVerify(exactly = 1) { AlbumArtCacheManager.clearAllCache(context) }
        verify(exactly = 0) { syncManager.fullSync(any()) }
    }

    @Test
    fun `failed invalidation leaves marker unchanged and next reconciliation retries`() = runTest {
        enabled.value = true
        coEvery { AlbumArtCacheManager.clearAllCache(context) } throws IOException("Cache unavailable")

        coordinator.reconcile()

        assertThat(recorded.value).isFalse()
        coVerify(exactly = 0) { preferences.setFolderAlbumArtCacheState(any()) }
        verify(exactly = 0) { AlbumArtUtils.notifyArtworkCacheInvalidated() }

        coEvery { AlbumArtCacheManager.clearAllCache(context) } returns 0
        coordinator.reconcile()

        assertThat(recorded.value).isTrue()
        verify(exactly = 1) { syncManager.fullSync() }
    }

    @Test
    fun `failed marker read is not treated as an unknown marker and overwritten`() = runTest {
        enabled.value = true
        every { preferences.folderAlbumArtCacheStateFlow } returns flow {
            throw IOException("Preferences unavailable")
        }

        coordinator.reconcile()

        coVerify(exactly = 0) { preferences.setFolderAlbumArtCacheState(any()) }
        coVerify(exactly = 0) { AlbumArtCacheManager.clearAllCache(context) }
    }
}
