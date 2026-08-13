package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioBookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: AudioBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<AudioBookmarkEntity>)

    @Query("SELECT * FROM audio_bookmarks ORDER BY created_time DESC")
    suspend fun getAllBookmarksOnce(): List<AudioBookmarkEntity>

    @Query("DELETE FROM audio_bookmarks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(bookmarks: List<AudioBookmarkEntity>) {
        deleteAll()
        insertAll(bookmarks)
    }

    @Query("DELETE FROM audio_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM audio_bookmarks ORDER BY created_time DESC")
    fun getAllBookmarksFlow(): Flow<List<AudioBookmarkEntity>>

    @Query("SELECT * FROM audio_bookmarks WHERE song_id = :songId ORDER BY created_time DESC")
    suspend fun getBookmarksForSong(songId: String): List<AudioBookmarkEntity>

    @Query("SELECT * FROM audio_bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: Long): AudioBookmarkEntity?
}
