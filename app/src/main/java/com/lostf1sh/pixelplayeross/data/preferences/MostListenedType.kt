package com.lostf1sh.pixelplayeross.data.preferences

/** What the Most Listened screen groups plays by. */
enum class MostListenedType(val storageKey: String) {
    SONGS("songs"),
    ALBUMS("albums"),
    ARTISTS("artists");

    companion object {
        fun fromStorageKey(value: String?): MostListenedType =
            entries.firstOrNull { it.storageKey == value } ?: SONGS
    }
}
