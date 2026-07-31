package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_collections")
data class CachedCollectionEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    @ColumnInfo(name = "collection_type")
    val collectionType: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val title: String,
    val subtitle: String?,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
)

@Entity(
    tableName = "cached_tracks",
    indices = [Index(value = ["cache_key"], unique = true)],
)
data class CachedTrackEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "cache_key")
    val cacheKey: String?,
    @ColumnInfo(name = "song_id")
    val songId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artist_id")
    val artistId: Long,
    val album: String,
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "album_artist")
    val albumArtist: String?,
    val path: String,
    @ColumnInfo(name = "content_uri")
    val contentUri: String,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    val genre: String?,
    @ColumnInfo(name = "track_number")
    val trackNumber: Int,
    @ColumnInfo(name = "disc_number")
    val discNumber: Int?,
    val year: Int,
    @ColumnInfo(name = "date_added")
    val dateAdded: Long,
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    val bitrate: Int?,
    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int?,
    @ColumnInfo(name = "navidrome_id")
    val navidromeId: String?,
    @ColumnInfo(name = "jellyfin_id")
    val jellyfinId: String?,
    @ColumnInfo(name = "is_remote")
    val isRemote: Boolean,
    @ColumnInfo(name = "added_at_ms")
    val addedAtMs: Long,
)

@Entity(
    tableName = "cached_collection_tracks",
    primaryKeys = ["collection_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = CachedCollectionEntity::class,
            parentColumns = ["collection_id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CachedTrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("track_id")],
)
data class CachedCollectionTrackCrossRef(
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

@Dao
interface OfflineMediaDao {
    @Query("SELECT * FROM cached_collections ORDER BY created_at_ms DESC")
    fun observeCollections(): Flow<List<CachedCollectionEntity>>

    @Query("SELECT * FROM cached_tracks")
    fun observeTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_collection_tracks ORDER BY collection_id, sort_order")
    fun observeCrossRefs(): Flow<List<CachedCollectionTrackCrossRef>>

    @Upsert
    suspend fun upsertCollection(collection: CachedCollectionEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<CachedTrackEntity>)

    @Upsert
    suspend fun upsertCrossRefs(crossRefs: List<CachedCollectionTrackCrossRef>)

    @Query("DELETE FROM cached_collections WHERE collection_id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("SELECT track_id FROM cached_collection_tracks WHERE collection_id = :collectionId")
    suspend fun trackIdsForCollection(collectionId: String): List<String>

    @Query(
        "SELECT * FROM cached_tracks WHERE track_id IN (:candidateIds) " +
            "AND track_id NOT IN (SELECT track_id FROM cached_collection_tracks)"
    )
    suspend fun orphanedTracks(candidateIds: List<String>): List<CachedTrackEntity>

    @Query("DELETE FROM cached_tracks WHERE track_id IN (:trackIds)")
    suspend fun deleteTracks(trackIds: List<String>)

    @Query("DELETE FROM cached_collections")
    suspend fun clearCollections()

    @Query("DELETE FROM cached_tracks")
    suspend fun clearTracks()
}
