package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineTrackEntity)

    @Query("SELECT * FROM offline_tracks WHERE source_uri = :sourceUri LIMIT 1")
    fun observeBySourceUri(sourceUri: String): Flow<OfflineTrackEntity?>

    @Query("SELECT * FROM offline_tracks WHERE source_uri = :sourceUri LIMIT 1")
    suspend fun getBySourceUri(sourceUri: String): OfflineTrackEntity?

    @Query("SELECT * FROM offline_tracks WHERE download_id = :downloadId LIMIT 1")
    suspend fun getByDownloadId(downloadId: String): OfflineTrackEntity?

    @Query("SELECT * FROM offline_tracks WHERE state = 'complete' ORDER BY updated_at DESC")
    fun observeCompleted(): Flow<List<OfflineTrackEntity>>

    @Query("SELECT * FROM offline_tracks ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<OfflineTrackEntity>>

    @Query("SELECT * FROM offline_tracks WHERE state = 'complete'")
    suspend fun getCompleted(): List<OfflineTrackEntity>

    @Query(
        """
        UPDATE offline_tracks
        SET state = :state,
            bytes_downloaded = :bytesDownloaded,
            total_bytes = :totalBytes,
            local_path = :localPath,
            error_message = :errorMessage,
            updated_at = :updatedAt
        WHERE download_id = :downloadId AND attempt_id = :attemptId
        """
    )
    suspend fun updateState(
        downloadId: String,
        attemptId: String,
        state: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        localPath: String?,
        errorMessage: String?,
        updatedAt: Long
    ): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM offline_tracks " +
            "WHERE download_id = :downloadId AND attempt_id = :attemptId)"
    )
    suspend fun isCurrentAttempt(downloadId: String, attemptId: String): Boolean

    @Query("DELETE FROM offline_tracks WHERE source_uri = :sourceUri")
    suspend fun deleteBySourceUri(sourceUri: String)

    @Query("DELETE FROM offline_tracks WHERE source_uri = :sourceUri AND attempt_id = :attemptId")
    suspend fun deleteBySourceUriForAttempt(sourceUri: String, attemptId: String): Int

    @Query("DELETE FROM offline_tracks WHERE download_id = :downloadId")
    suspend fun deleteByDownloadId(downloadId: String)
}
