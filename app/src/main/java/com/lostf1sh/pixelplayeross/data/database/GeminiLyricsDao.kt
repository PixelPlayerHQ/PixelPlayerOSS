package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeminiLyricsDao {
    @Query("SELECT * FROM gemini_lyrics_cache WHERE trackKey = :trackKey")
    suspend fun getCache(trackKey: String): GeminiLyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: GeminiLyricsCacheEntity)

    @Query("DELETE FROM gemini_lyrics_cache WHERE trackKey = :trackKey")
    suspend fun deleteCache(trackKey: String)

    @Query("DELETE FROM gemini_lyrics_cache")
    suspend fun clearAll()
}
