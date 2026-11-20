package com.lostf1sh.pixelplayeross.data.worker

import androidx.work.WorkInfo
import java.util.UUID
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
    isFreshInstall: Boolean,
    embeddedTagScanRequired: Boolean = false,
    albumGroupingChanged: Boolean = false
): SyncExecutionPlan {
    val forceProcessAll = requestedMode != SyncMode.INCREMENTAL ||
        rescanRequired ||
        directoryRulesChanged ||
        albumGroupingChanged ||
        isFreshInstall ||
        embeddedTagScanRequired
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

/**
 * MediaStore cutoff, in seconds, for the local fetch. 0 reads every row. Unchanged rows are
 * still skipped by the worker's per-row comparison unless [SyncExecutionPlan.forceProcessAll]
 * is set, so a cutoff of 0 only costs a MediaStore read, not a metadata reprocess.
 */
internal fun localFetchTimestampSeconds(
    plan: SyncExecutionPlan,
    hasUnindexedSongs: Boolean,
    metadataCharsetRepairPending: Boolean,
    lastSyncTimestampMs: Long
): Long = if (!plan.forceProcessAll && !hasUnindexedSongs && !metadataCharsetRepairPending) {
    incrementalFetchTimestampSeconds(lastSyncTimestampMs)
} else {
    0L
}

/**
 * Battery / thermal: background INCREMENTAL syncs wait while music is playing. FULL and REBUILD
 * are user-initiated or needed for first-run population, so they always run. User-facing
 * incremental syncs (pull-to-refresh, refresh, a newly added file) set [bypass], because the
 * user is waiting for the result and they are cheap maintenance-free delta scans.
 */
internal fun shouldDeferSyncForPlayback(
    mode: SyncMode,
    bypass: Boolean,
    playbackActive: Boolean,
    runAttemptCount: Int,
    maxDeferrals: Int = SyncWorker.MAX_PLAYBACK_DEFERRALS
): Boolean = mode == SyncMode.INCREMENTAL &&
    !bypass &&
    playbackActive &&
    runAttemptCount < maxDeferrals

internal enum class FollowUpSyncDecision {
    /** Nothing left to pick up, or the sync did not succeed. */
    NONE,

    /** Schedule a follow-up local sync right away. */
    NOW,

    /** Too many follow-ups in a row: check again once after a backoff. */
    AFTER_BACKOFF
}

/**
 * What to do once the sync work finished, given whether MediaStore changed after it read
 * MediaStore. Only a fully SUCCEEDED run gets a follow-up: a failed scan would just fail again,
 * and cancelled work was replaced on purpose. Past [maxFollowUps] in a row the follow-up waits
 * for a backoff instead of being dropped, so a long copy still gets its last files picked up
 * while a feedback loop costs only one sync per backoff period.
 */
internal fun followUpSyncDecision(
    states: Collection<WorkInfo.State>,
    hasUnscannedChanges: Boolean,
    consecutiveFollowUps: Int,
    maxFollowUps: Int = MAX_CONSECUTIVE_FOLLOW_UP_SYNCS
): FollowUpSyncDecision = when {
    !hasUnscannedChanges ||
        states.isEmpty() ||
        states.any { it != WorkInfo.State.SUCCEEDED } -> FollowUpSyncDecision.NONE
    consecutiveFollowUps < maxFollowUps -> FollowUpSyncDecision.NOW
    else -> FollowUpSyncDecision.AFTER_BACKOFF
}

internal const val MAX_CONSECUTIVE_FOLLOW_UP_SYNCS = 5

/**
 * Ids of the pending sync work when all of it is only waiting out its playback deferral: back
 * in ENQUEUED after a run and tagged [SyncWorker.TAG_PLAYBACK_DEFERRABLE]. Such work can sit in
 * retry backoff for about 15 minutes, so a sync that bypasses the deferral replaces it. Returns
 * an empty list, so the pending work is kept, when anything is running or has not started yet,
 * or when other work was retried, such as an interrupted full rescan or rebuild.
 */
internal fun playbackDeferredSyncWorkIds(work: Collection<WorkInfo>): List<UUID> {
    val pending = work.filterNot { it.state.isFinished }
    val onlyPlaybackDeferred = pending.isNotEmpty() && pending.all {
        it.state == WorkInfo.State.ENQUEUED &&
            it.runAttemptCount > 0 &&
            SyncWorker.TAG_PLAYBACK_DEFERRABLE in it.tags
    }
    return if (onlyPlaybackDeferred) pending.map { it.id } else emptyList()
}

/**
 * The foreground catch-up normally waits [cooldownMs] after the previous one so quick
 * minimize/restore cycles don't pile up work, but a MediaStore change made while the app was
 * in the background always gets a catch-up.
 */
internal fun shouldRunForegroundCatchUp(
    nowMs: Long,
    lastCatchUpMs: Long,
    cooldownMs: Long,
    mediaStoreChangedWhileAway: Boolean
): Boolean = mediaStoreChangedWhileAway || nowMs - lastCatchUpMs >= cooldownMs
