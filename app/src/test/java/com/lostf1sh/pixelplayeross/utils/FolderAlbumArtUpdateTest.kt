package com.lostf1sh.pixelplayeross.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Cached covers live in `filesDir` and outlive the process, so the app records the effective
 * folder-cover state each cache was built under — the opt-in *and* the image permission — and
 * reconciles it on launch, on returning to the foreground, and when the preference changes.
 */
class FolderAlbumArtUpdateTest {

    @Test
    fun missingRecordedState_invalidatesPreexistingArtwork() {
        assertThat(resolveFolderAlbumArtUpdate(previous = null, observed = true))
            .isEqualTo(FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE)
        assertThat(resolveFolderAlbumArtUpdate(previous = null, observed = false))
            .isEqualTo(FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE)
    }

    @Test
    fun unchangedState_isIgnored() {
        assertThat(resolveFolderAlbumArtUpdate(previous = true, observed = true))
            .isEqualTo(FolderAlbumArtUpdate.IGNORE)
        assertThat(resolveFolderAlbumArtUpdate(previous = false, observed = false))
            .isEqualTo(FolderAlbumArtUpdate.IGNORE)
    }

    /** Turning the setting on or off, or a backup restore writing straight into DataStore. */
    @Test
    fun preferenceChange_invalidatesCachedArtwork() {
        assertThat(resolveFolderAlbumArtUpdate(previous = false, observed = true))
            .isEqualTo(FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE)
        assertThat(resolveFolderAlbumArtUpdate(previous = true, observed = false))
            .isEqualTo(FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE)
    }

    /**
     * The image permission granted back in system settings while the opt-in never changed. The
     * cache still holds embedded art and "no art" markers written without folder access, which
     * would otherwise keep suppressing folder covers indefinitely.
     */
    @Test
    fun permissionRegrantedWithoutPreferenceChange_invalidatesCachedArtwork() {
        assertThat(resolveFolderAlbumArtUpdate(previous = false, observed = true))
            .isEqualTo(FolderAlbumArtUpdate.MIRROR_AND_INVALIDATE)
    }
}
