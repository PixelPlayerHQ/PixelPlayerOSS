package com.lostf1sh.pixelplayeross.data.preferences

/** What swiping a song row to the right does in playlist, album, artist, genre and mix lists. */
enum class SwipeToQueueAction(val storageKey: String) {
    OFF("off"),

    /** Append the song to the end of the queue. */
    ADD_TO_QUEUE("add_to_queue"),

    /** Insert the song right after the one playing. */
    PLAY_NEXT("play_next");

    companion object {
        val DEFAULT = ADD_TO_QUEUE

        fun fromStorageKey(value: String?): SwipeToQueueAction =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
