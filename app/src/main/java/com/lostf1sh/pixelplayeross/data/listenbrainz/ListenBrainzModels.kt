package com.lostf1sh.pixelplayeross.data.listenbrainz

import com.google.gson.annotations.SerializedName

/**
 * Request body for `POST /1/submit-listens`.
 * Gson omits null fields, which matters: `playing_now` payloads must not carry `listened_at`.
 */
data class ListenBrainzSubmission(
    @SerializedName("listen_type") val listenType: String,
    @SerializedName("payload") val payload: List<ListenBrainzListen>
) {
    companion object {
        const val TYPE_PLAYING_NOW = "playing_now"
        const val TYPE_IMPORT = "import"
    }
}

data class ListenBrainzListen(
    /** Epoch seconds of when the listen started. Null for `playing_now`. */
    @SerializedName("listened_at") val listenedAt: Long? = null,
    @SerializedName("track_metadata") val trackMetadata: ListenBrainzTrackMetadata
)

data class ListenBrainzTrackMetadata(
    @SerializedName("artist_name") val artistName: String,
    @SerializedName("track_name") val trackName: String,
    @SerializedName("release_name") val releaseName: String? = null,
    @SerializedName("additional_info") val additionalInfo: ListenBrainzAdditionalInfo? = null
)

data class ListenBrainzAdditionalInfo(
    @SerializedName("media_player") val mediaPlayer: String? = null,
    @SerializedName("submission_client") val submissionClient: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("recording_mbid") val recordingMbid: String? = null
)

/** Response of `GET /1/validate-token`. */
data class ListenBrainzTokenValidation(
    @SerializedName("valid") val valid: Boolean = false,
    @SerializedName("user_name") val userName: String? = null
)

/** Connection state surfaced on the Accounts screen. */
data class ListenBrainzAccountState(
    val isConnected: Boolean = false,
    val userName: String? = null,
    /** True when the stored token was rejected (HTTP 401); flushing is paused until reconnect. */
    val needsReauth: Boolean = false
)

sealed interface ListenBrainzSubmitResult {
    data object Success : ListenBrainzSubmitResult

    /** Token rejected; queue flushing pauses until the user reconnects. */
    data object AuthFailed : ListenBrainzSubmitResult

    /** The server rejected the payload as invalid (HTTP 400) — permanent for this payload. */
    data object InvalidPayload : ListenBrainzSubmitResult

    /**
     * Rate limited or transient failure — retry later with backoff, but no earlier than
     * [retryAfterSeconds] when the server sent a retry window.
     */
    data class TransientError(val retryAfterSeconds: Long? = null) : ListenBrainzSubmitResult
}
