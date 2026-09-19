package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import com.lostf1sh.pixelplayeross.data.media.ImageCacheManager
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemeProcessor
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes settings, foreground permission checks, and backup restores against the same cache. */
@Singleton
class FolderArtworkSettingsCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val imageCacheManager: ImageCacheManager,
    private val artistImageRepository: ArtistImageRepository,
    private val syncManager: SyncManager,
    private val colorSchemeProcessor: Lazy<ColorSchemeProcessor>
) {
    private val mutex = Mutex()
    private var legacyCacheMigrated = false

    suspend fun setEnabled(enabled: Boolean) {
        update { preferences.setUseFolderAlbumArt(enabled) }
    }

    suspend fun reconcile() {
        update {}
    }

    private suspend fun update(writePreference: suspend () -> Unit) = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                writePreference()
                reconcileLocked()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Leave the marker unchanged so the next foreground/settings event retries.
            Timber.e(error, "Unable to reconcile folder artwork settings")
        }
    }

    private suspend fun reconcileLocked() {
        if (!legacyCacheMigrated) {
            AlbumArtUtils.migrateLegacyCacheLocation(context)
            legacyCacheMigrated = true
        }

        // Read inside the lock: an observer can have queued an old emission while a setting
        // changed again. Applying that emission would put the mirror and marker out of sync.
        val enabled = preferences.useFolderAlbumArtFlow.first()
        AlbumArtUtils.setFolderAlbumArtPreference(enabled)
        val effective = AlbumArtUtils.isFolderAlbumArtEnabled(context)
        val recorded = preferences.folderAlbumArtCacheStateFlow.first()
        if (resolveFolderAlbumArtUpdate(recorded, effective) == FolderAlbumArtUpdate.IGNORE) return

        AlbumArtUtils.bumpArtworkCacheGeneration()
        AlbumArtCacheManager.clearAllCache(context)
        // Also retire readers/shrink jobs that observed an old file while deletion was running.
        AlbumArtUtils.bumpArtworkCacheGeneration()
        imageCacheManager.clearAllCoverArtCaches()
        colorSchemeProcessor.get().invalidateLocalArtworkSchemes()
        artistImageRepository.clearCache()
        AlbumArtUtils.notifyArtworkCacheInvalidated()

        // A song previously resolved without artwork can have a null database URI. Clearing
        // files alone never requests its image again, including after a permission regrant.
        if (preferences.initialSetupDoneFlow.first()) {
            syncManager.fullSync()
        }
        preferences.setFolderAlbumArtCacheState(effective)
    }
}

internal enum class FolderAlbumArtUpdate {
    IGNORE,
    MIRROR_AND_INVALIDATE
}

internal fun resolveFolderAlbumArtUpdate(
    previous: Boolean?,
    observed: Boolean
): FolderAlbumArtUpdate = when (previous) {
    observed -> FolderAlbumArtUpdate.IGNORE
    // A missing marker can describe an existing cache from an older installation or a restore.
    // It does not establish that the cache is empty or was built with the current preference.
    else -> FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE
}
