package com.lostf1sh.pixelplayeross.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedArtworkContentProviderTest {

    @Test
    fun artworkReadAccess_allowsProviderProcess() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 1001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                mediaContentControlResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                isPublishedSessionArtwork = false,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_allowsExplicitUriGrant() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 2001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_GRANTED,
                mediaContentControlResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                isPublishedSessionArtwork = false,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_rejectsUntrustedExternalCaller() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 2001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                mediaContentControlResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                isPublishedSessionArtwork = true,
            )
        ).isFalse()
    }

    @Test
    fun artworkReadAccess_allowsSystemMediaSurfaceForPublishedArtwork() {
        // System UI reads the platform session's artwork URI without connecting as a
        // Media3 controller, so it never receives a per-item grant.
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 10_100,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                mediaContentControlResult = android.content.pm.PackageManager.PERMISSION_GRANTED,
                isPublishedSessionArtwork = true,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_rejectsSystemMediaSurfaceForUnpublishedArtwork() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 10_100,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
                mediaContentControlResult = android.content.pm.PackageManager.PERMISSION_GRANTED,
                isPublishedSessionArtwork = false,
            )
        ).isFalse()
    }

    @Test
    fun publishedSessionArtwork_matchesSongRegardlessOfCacheBustToken() {
        SharedArtworkContentProvider.clearPublishedSessionArtwork()
        SharedArtworkContentProvider.publishSessionArtwork(
            PACKAGE,
            SharedArtworkContentProvider.buildSongUriString(PACKAGE, 42L, cacheBustToken = "1")
        )

        assertThat(
            SharedArtworkContentProvider.isPublishedSessionArtwork(
                SharedArtworkContentProvider.buildSongUriString(PACKAGE, 42L, cacheBustToken = "2"),
                PACKAGE
            )
        ).isTrue()
        assertThat(
            SharedArtworkContentProvider.isPublishedSessionArtwork(
                SharedArtworkContentProvider.buildSongUriString(PACKAGE, 43L),
                PACKAGE
            )
        ).isFalse()
    }

    @Test
    fun publishedSessionArtwork_keepsOnlyRecentTracks() {
        SharedArtworkContentProvider.clearPublishedSessionArtwork()
        val covers = (1..4).map { index ->
            SharedArtworkContentProvider.buildCloudUriString(PACKAGE, "navidrome_cover://al-$index")!!
        }
        covers.forEach { SharedArtworkContentProvider.publishSessionArtwork(PACKAGE, it) }

        assertThat(SharedArtworkContentProvider.isPublishedSessionArtwork(covers.first(), PACKAGE)).isFalse()
        assertThat(covers.drop(1).all { SharedArtworkContentProvider.isPublishedSessionArtwork(it, PACKAGE) })
            .isTrue()
    }

    @Test
    fun buildSongUri_usesDedicatedArtworkAuthority() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.lostf1sh.pixelplayeross",
            songId = 42L
        )

        assertThat(uri).isEqualTo("content://com.lostf1sh.pixelplayeross.artwork/song/42")
    }

    @Test
    fun buildSongUri_preservesCacheBustToken() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.lostf1sh.pixelplayeross",
            songId = 42L,
            cacheBustToken = "1234"
        )

        assertThat(uri)
            .isEqualTo("content://com.lostf1sh.pixelplayeross.artwork/song/42?t=1234")
    }

    @Test
    fun parseSongId_rejectsOtherAuthorities() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://example.com.artwork/song/42",
            packageName = "com.lostf1sh.pixelplayeross"
        )

        assertThat(songId).isNull()
    }

    @Test
    fun parseSongId_readsSharedArtworkSongUri() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://com.lostf1sh.pixelplayeross.artwork/song/42",
            packageName = "com.lostf1sh.pixelplayeross"
        )

        assertThat(songId).isEqualTo(42L)
    }

    @Test
    fun cloudArtworkUri_roundTripsNavidromeArtwork() {
        val rawArtworkUri = "navidrome_cover://album-42"
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.lostf1sh.pixelplayeross",
            rawArtworkUri = rawArtworkUri,
        )

        assertThat(sharedUri).isNotNull()
        assertThat(
            SharedArtworkContentProvider.parseCloudArtworkUri(
                uriString = sharedUri!!,
                packageName = "com.lostf1sh.pixelplayeross",
            )
        ).isEqualTo(rawArtworkUri)
    }

    @Test
    fun cloudArtworkUri_roundTripsJellyfinArtwork() {
        val rawArtworkUri = "jellyfin_cover://item-84"
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.lostf1sh.pixelplayeross",
            rawArtworkUri = rawArtworkUri,
        )

        assertThat(sharedUri).isNotNull()
        assertThat(
            SharedArtworkContentProvider.parseCloudArtworkUri(
                uriString = sharedUri!!,
                packageName = "com.lostf1sh.pixelplayeross",
            )
        ).isEqualTo(rawArtworkUri)
    }

    @Test
    fun cloudArtworkUri_rejectsUnsupportedRemoteArtwork() {
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.lostf1sh.pixelplayeross",
            rawArtworkUri = "https://example.com/cover.jpg",
        )

        assertThat(sharedUri).isNull()
    }

    private companion object {
        const val PACKAGE = "com.lostf1sh.pixelplayeross"
    }
}
