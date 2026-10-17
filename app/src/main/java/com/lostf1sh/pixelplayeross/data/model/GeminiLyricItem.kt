package com.lostf1sh.pixelplayeross.data.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiLyricItem(
    @SerializedName("original")
    val original: String,
    @SerializedName("romanized")
    val romanized: String? = null,
    @SerializedName("translated")
    val translated: String? = null
)
