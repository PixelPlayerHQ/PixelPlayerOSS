package com.lostf1sh.pixelplayeross.utils

import android.Manifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FolderArtworkPermissionsTest {
    @Test
    fun `Android 14 requests selected photo permission with full access to avoid compatibility mode`() {
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
            folderArtworkPermissions(34)
        )
    }

    @Test
    fun `Android 13 only requests image access`() {
        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), folderArtworkPermissions(33))
    }

    @Test
    fun `Android 12 requests storage access again if the setup grant was revoked`() {
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), folderArtworkPermissions(32))
    }
}
