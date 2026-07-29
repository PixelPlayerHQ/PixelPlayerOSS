package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListenBrainzDao {

    @Insert
    suspend fun insert(listen: ListenBrainzPendingListenEntity): Long

    /** Oldest-first so the flush preserves listen order. */
    @Query("SELECT * FROM listenbrainz_pending_listens ORDER BY listened_at_ms ASC LIMIT :limit")
    suspend fun oldestPending(limit: Int): List<ListenBrainzPendingListenEntity>

    @Query("DELETE FROM listenbrainz_pending_listens WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE listenbrainz_pending_listens SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun incrementAttempts(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM listenbrainz_pending_listens")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM listenbrainz_pending_listens")
    fun countFlow(): Flow<Int>

    /** Drops the oldest rows beyond the queue cap so the table cannot grow unbounded offline. */
    @Query(
        """
        DELETE FROM listenbrainz_pending_listens
        WHERE id IN (
            SELECT id FROM listenbrainz_pending_listens
            ORDER BY listened_at_ms ASC
            LIMIT :overflow
        )
        """
    )
    suspend fun deleteOldest(overflow: Int)

    @Query("DELETE FROM listenbrainz_pending_listens")
    suspend fun clear()
}
