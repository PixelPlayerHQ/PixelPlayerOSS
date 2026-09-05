package com.lostf1sh.pixelplayeross.data.worker

import java.util.concurrent.TimeUnit

internal enum class LocalScanMode {
    INCREMENTAL_CHANGES,
    LOCAL_RESCAN,
    DEEP_RESCAN,
    LOCAL_REBUILD
}

internal data class SyncExecutionPlan(
    val requestedMode: SyncMode,
    val localScanMode: LocalScanMode,
    val runMaintenance: Boolean,
    val forceMetadata: Boolean,
    val forceProcessAll: Boolean,
    val resetExistingLocalData: Boolean
)

internal fun buildSyncExecutionPlan(
    requestedMode: SyncMode,
    requestedForceMetadata: Boolean,
    requestedRunMaintenance: Boolean,
    rescanRequired: Boolean,
    directoryRulesChanged: Boolean,
    isFreshInstall: Boolean
): SyncExecutionPlan {
    val forceProcessAll = requestedMode != SyncMode.INCREMENTAL ||
        rescanRequired ||
        directoryRulesChanged ||
        isFreshInstall
    val resetExistingLocalData = requestedMode == SyncMode.REBUILD
    // Artist delimiter changes must re-read the physical tags as well as rebuild relationships.
    // MediaStore often exposes only the first ARTIST value, especially for Vorbis comments.
    val effectiveForceMetadata = requestedForceMetadata ||
        requestedMode == SyncMode.REBUILD ||
        rescanRequired
    val localScanMode = when {
        resetExistingLocalData -> LocalScanMode.LOCAL_REBUILD
        effectiveForceMetadata -> LocalScanMode.DEEP_RESCAN
        forceProcessAll -> LocalScanMode.LOCAL_RESCAN
        else -> LocalScanMode.INCREMENTAL_CHANGES
    }

    return SyncExecutionPlan(
        requestedMode = requestedMode,
        localScanMode = localScanMode,
        runMaintenance = requestedRunMaintenance,
        forceMetadata = effectiveForceMetadata,
        forceProcessAll = forceProcessAll,
        resetExistingLocalData = resetExistingLocalData
    )
}

internal fun incrementalFetchTimestampSeconds(
    lastSyncTimestampMs: Long,
    overlapSeconds: Long = INCREMENTAL_TIMESTAMP_OVERLAP_SECONDS
): Long {
    val lastSyncSeconds = TimeUnit.MILLISECONDS.toSeconds(lastSyncTimestampMs)
    return (lastSyncSeconds - overlapSeconds).coerceAtLeast(0L)
}

internal const val INCREMENTAL_TIMESTAMP_OVERLAP_SECONDS = 1L
