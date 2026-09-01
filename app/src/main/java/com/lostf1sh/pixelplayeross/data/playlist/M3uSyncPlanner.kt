package com.lostf1sh.pixelplayeross.data.playlist

import kotlinx.serialization.Serializable

internal enum class M3uSyncAction {
    NONE,
    EXPORT,
    IMPORT,
    CONFLICT,
}

@Serializable
data class M3uSyncCheckpoint(
    val appHash: String,
    val fileHash: String,
)

internal object M3uSyncPlanner {
    fun decide(
        appHash: String?,
        fileHash: String?,
        checkpoint: M3uSyncCheckpoint?,
    ): M3uSyncAction {
        if (appHash == null && fileHash == null) return M3uSyncAction.NONE
        if (appHash != null && fileHash == null) return M3uSyncAction.EXPORT
        if (appHash == null) return M3uSyncAction.IMPORT
        // Independently edited sides can converge on the exact same representation.
        if (appHash == fileHash) return M3uSyncAction.NONE

        if (checkpoint == null) {
            return M3uSyncAction.CONFLICT
        }

        val appChanged = appHash != checkpoint.appHash
        val fileChanged = fileHash != checkpoint.fileHash
        return when {
            appChanged && fileChanged -> M3uSyncAction.CONFLICT
            appChanged -> M3uSyncAction.EXPORT
            fileChanged -> M3uSyncAction.IMPORT
            else -> M3uSyncAction.NONE
        }
    }
}
