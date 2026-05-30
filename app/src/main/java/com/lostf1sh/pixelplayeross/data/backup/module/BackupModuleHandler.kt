package com.lostf1sh.pixelplayeross.data.backup.module

import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection

interface BackupModuleHandler {
    val section: BackupSection

    /** Serialize current app data for this module into a JSON string. */
    suspend fun export(): String

    /** Count of items in the exported data (for manifest). */
    suspend fun countEntries(): Int

    /** Capture current state as a JSON snapshot for rollback. */
    suspend fun snapshot(): String

    /** Clear existing data and restore from a JSON payload. */
    suspend fun restore(payload: String)

    /** Rollback to a previous snapshot (for transactional restore). */
    suspend fun rollback(snapshot: String)
}
