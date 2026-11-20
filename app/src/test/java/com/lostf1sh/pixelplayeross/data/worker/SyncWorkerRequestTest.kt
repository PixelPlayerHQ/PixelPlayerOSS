package com.lostf1sh.pixelplayeross.data.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SyncWorkerRequestTest {

    @Test
    fun `startup sync request runs maintenance`() {
        val request = SyncWorker.startUpSyncWork()

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))
    }

    @Test
    fun `default incremental sync request runs maintenance`() {
        val request = SyncWorker.incrementalSyncWork()

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))
    }

    @Test
    fun `incremental sync request can skip maintenance`() {
        val request = SyncWorker.incrementalSyncWork(runMaintenance = false)

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertFalse(request.workSpec.input.getBoolean(SyncWorker.INPUT_FORCE_METADATA, false))
        assertFalse(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, true))
    }

    @Test
    fun `default incremental and startup sync requests keep playback deferral`() {
        listOf(
            SyncWorker.incrementalSyncWork(),
            SyncWorker.incrementalSyncWork(runMaintenance = false)
        ).forEach { request ->
            assertFalse(
                request.workSpec.input.getBoolean(SyncWorker.INPUT_BYPASS_PLAYBACK_DEFERRAL, true)
            )
        }
        // The startup request doesn't set the flag; the worker reads a missing flag as false.
        assertFalse(
            SyncWorker.startUpSyncWork().workSpec.input
                .getBoolean(SyncWorker.INPUT_BYPASS_PLAYBACK_DEFERRAL, false)
        )
    }

    @Test
    fun `user facing incremental sync request bypasses playback deferral`() {
        val request = SyncWorker.incrementalSyncWork(
            runMaintenance = false,
            bypassPlaybackDeferral = true
        )

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertFalse(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, true))
        assertTrue(request.workSpec.input.getBoolean(SyncWorker.INPUT_BYPASS_PLAYBACK_DEFERRAL, false))
    }

    @Test
    fun `only requests that defer during playback carry the deferrable tag`() {
        listOf(
            SyncWorker.startUpSyncWork(),
            SyncWorker.incrementalSyncWork(),
            SyncWorker.incrementalSyncWork(runMaintenance = false)
        ).forEach { request ->
            assertTrue(SyncWorker.TAG_PLAYBACK_DEFERRABLE in request.tags)
        }
        listOf(
            SyncWorker.incrementalSyncWork(runMaintenance = false, bypassPlaybackDeferral = true),
            SyncWorker.fullSyncWork(),
            SyncWorker.rebuildDatabaseWork()
        ).forEach { request ->
            assertFalse(SyncWorker.TAG_PLAYBACK_DEFERRABLE in request.tags)
        }
    }

    @Test
    fun `full sync and rebuild requests run maintenance`() {
        val fullRequest = SyncWorker.fullSyncWork()
        val rebuildRequest = SyncWorker.rebuildDatabaseWork()

        assertEquals(SyncMode.FULL.name, fullRequest.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(fullRequest.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))

        assertEquals(SyncMode.REBUILD.name, rebuildRequest.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(rebuildRequest.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))
    }

    @Test
    fun `full sync builds shallow local rescan unless metadata is requested`() {
        val plan = buildSyncExecutionPlan(
            requestedMode = SyncMode.FULL,
            requestedForceMetadata = false,
            requestedRunMaintenance = true,
            rescanRequired = false,
            directoryRulesChanged = false,
            isFreshInstall = false
        )

        assertEquals(LocalScanMode.LOCAL_RESCAN, plan.localScanMode)
        assertTrue(plan.forceProcessAll)
        assertFalse(plan.forceMetadata)
        assertFalse(plan.resetExistingLocalData)
    }

    @Test
    fun `changed album grouping scans all local songs without destructive rebuild or cloud maintenance`() {
        val plan = buildSyncExecutionPlan(
            requestedMode = SyncMode.INCREMENTAL,
            requestedForceMetadata = false,
            requestedRunMaintenance = false,
            rescanRequired = false,
            directoryRulesChanged = false,
            isFreshInstall = false,
            albumGroupingChanged = true
        )

        assertEquals(LocalScanMode.LOCAL_RESCAN, plan.localScanMode)
        assertEquals(0L, localFetchTimestampSeconds(plan, false, false, 100_000L))
        assertFalse(plan.resetExistingLocalData)
        assertFalse(plan.runMaintenance)
    }

    @Test
    fun `artist settings force deep metadata rescan while directory settings stay shallow`() {
        val artistPlan = buildSyncExecutionPlan(
            requestedMode = SyncMode.INCREMENTAL,
            requestedForceMetadata = false,
            requestedRunMaintenance = false,
            rescanRequired = true,
            directoryRulesChanged = false,
            isFreshInstall = false
        )

        assertEquals(LocalScanMode.DEEP_RESCAN, artistPlan.localScanMode)
        assertTrue(artistPlan.forceProcessAll)
        assertTrue(artistPlan.forceMetadata)
        assertFalse(artistPlan.runMaintenance)

        val directoryPlan = buildSyncExecutionPlan(
            requestedMode = SyncMode.INCREMENTAL,
            requestedForceMetadata = false,
            requestedRunMaintenance = false,
            rescanRequired = false,
            directoryRulesChanged = true,
            isFreshInstall = false
        )

        assertEquals(LocalScanMode.LOCAL_RESCAN, directoryPlan.localScanMode)
        assertTrue(directoryPlan.forceProcessAll)
        assertFalse(directoryPlan.forceMetadata)
    }

    @Test
    fun `tag backfill revisits unchanged tracks without enabling deep audio analysis or clearing the library`() {
        val plan = buildSyncExecutionPlan(
            requestedMode = SyncMode.INCREMENTAL,
            requestedForceMetadata = false,
            requestedRunMaintenance = false,
            rescanRequired = false,
            directoryRulesChanged = false,
            isFreshInstall = false,
            embeddedTagScanRequired = true
        )

        assertEquals(LocalScanMode.LOCAL_RESCAN, plan.localScanMode)
        assertTrue(plan.forceProcessAll)
        assertFalse(plan.forceMetadata)
        assertFalse(plan.resetExistingLocalData)
    }

    @Test
    fun `rebuild resets existing local data and deep scans metadata`() {
        val plan = buildSyncExecutionPlan(
            requestedMode = SyncMode.REBUILD,
            requestedForceMetadata = false,
            requestedRunMaintenance = true,
            rescanRequired = false,
            directoryRulesChanged = false,
            isFreshInstall = false
        )

        assertEquals(LocalScanMode.LOCAL_REBUILD, plan.localScanMode)
        assertTrue(plan.forceProcessAll)
        assertTrue(plan.forceMetadata)
        assertTrue(plan.resetExistingLocalData)
    }

    @Test
    fun `multi value tag formats read embedded artist values during a shallow scan`() {
        listOf("song.mp3", "song.flac", "song.opus", "song.ogg", "song.oga").forEach { fileName ->
            assertTrue(
                shouldReadEmbeddedMetadata(
                    filePath = "/music/$fileName",
                    deepScan = false,
                    rawArtist = "MediaStore Artist",
                    rawAlbum = "MediaStore Album"
                ),
                fileName
            )
        }

        assertFalse(
            shouldReadEmbeddedMetadata(
                filePath = "/music/song.m4a",
                deepScan = false,
                rawArtist = "MediaStore Artist",
                rawAlbum = "MediaStore Album"
            )
        )
    }

    @Test
    fun `incremental timestamp includes overlap`() {
        val lastSyncMs = TimeUnit.SECONDS.toMillis(120)

        assertEquals(119L, incrementalFetchTimestampSeconds(lastSyncMs))
    }

    @Test
    fun `indexed incremental sync keeps the timestamp cutoff`() {
        val lastSyncMs = TimeUnit.SECONDS.toMillis(120)

        assertEquals(
            incrementalFetchTimestampSeconds(lastSyncMs),
            localFetchTimestampSeconds(
                plan = incrementalPlan(),
                hasUnindexedSongs = false,
                metadataCharsetRepairPending = false,
                lastSyncTimestampMs = lastSyncMs
            )
        )
    }

    @Test
    fun `pending charset repair reads MediaStore without a cutoff but keeps the incremental plan`() {
        val plan = incrementalPlan()

        assertEquals(
            0L,
            localFetchTimestampSeconds(
                plan = plan,
                hasUnindexedSongs = false,
                metadataCharsetRepairPending = true,
                lastSyncTimestampMs = TimeUnit.SECONDS.toMillis(120)
            )
        )
        // Only rows whose stored text differs are reprocessed, not every song.
        assertEquals(LocalScanMode.INCREMENTAL_CHANGES, plan.localScanMode)
        assertFalse(plan.forceProcessAll)
        assertFalse(plan.forceMetadata)
    }

    @Test
    fun `unindexed songs or a forced full pass read MediaStore without a cutoff`() {
        val lastSyncMs = TimeUnit.SECONDS.toMillis(120)

        assertEquals(
            0L,
            localFetchTimestampSeconds(
                plan = incrementalPlan(),
                hasUnindexedSongs = true,
                metadataCharsetRepairPending = false,
                lastSyncTimestampMs = lastSyncMs
            )
        )
        assertEquals(
            0L,
            localFetchTimestampSeconds(
                plan = incrementalPlan(directoryRulesChanged = true),
                hasUnindexedSongs = false,
                metadataCharsetRepairPending = false,
                lastSyncTimestampMs = lastSyncMs
            )
        )
    }

    private fun incrementalPlan(directoryRulesChanged: Boolean = false) = buildSyncExecutionPlan(
        requestedMode = SyncMode.INCREMENTAL,
        requestedForceMetadata = false,
        requestedRunMaintenance = false,
        rescanRequired = false,
        directoryRulesChanged = directoryRulesChanged,
        isFreshInstall = false
    )
}
