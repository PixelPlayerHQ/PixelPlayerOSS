package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gemini_lyrics_cache")
data class GeminiLyricsCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "trackKey")
    val trackKey: String,
    @ColumnInfo(name = "originalTextHash")
    val originalTextHash: String,
    @ColumnInfo(name = "structuredJson")
    val structuredJson: String,
    @ColumnInfo(name = "createdAtEpochMs")
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
