package com.lostf1sh.pixelplayeross.data.model

enum class FolderSource(val storageKey: String, val displayName: String) {
    INTERNAL("internal", "Internal Storage"),
    SD_CARD("sd_card", "SD Card");

    companion object {
        fun fromStorageKey(rawValue: String?): FolderSource =
            entries.firstOrNull { it.storageKey == rawValue } ?: INTERNAL
    }
}
