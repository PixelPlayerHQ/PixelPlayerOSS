package com.lostf1sh.pixelplayeross.presentation.components

import coil.size.Dimension
import coil.size.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OptimizedAlbumArtTest {

    @Test
    fun safeAlbumArtTargetSize_clampsOriginalRequests() {
        val targetSize = safeAlbumArtTargetSize(Size.ORIGINAL)

        assertThat((targetSize.width as Dimension.Pixels).px)
            .isEqualTo(MaxSafeAlbumArtDimensionPx)
        assertThat((targetSize.height as Dimension.Pixels).px)
            .isEqualTo(MaxSafeAlbumArtDimensionPx)
    }

    @Test
    fun safeAlbumArtTargetSize_keepsBoundedRequests() {
        val targetSize = Size(800, 600)

        assertThat(safeAlbumArtTargetSize(targetSize)).isEqualTo(targetSize)
    }
}
