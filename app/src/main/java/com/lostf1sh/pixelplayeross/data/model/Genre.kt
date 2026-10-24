package com.lostf1sh.pixelplayeross.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Genre(
    val id: String,
    val name: String,
    val iconResId: Int? = null,
    val lightColorHex: String? = null,
    val onLightColorHex: String? = null,
    val darkColorHex: String? = null,
    val onDarkColorHex: String? = null,
    /** Songs tagged with this genre, counting each genre of a multi-genre song. */
    val songCount: Int = 0
)
