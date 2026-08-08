package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_bookmarks")
data class AudioBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "song_title") val songTitle: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_art_uri") val albumArtUri: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "created_time") val createdTime: Long = System.currentTimeMillis()
)
