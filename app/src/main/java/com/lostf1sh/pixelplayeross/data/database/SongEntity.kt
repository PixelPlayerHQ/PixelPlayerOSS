package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lostf1sh.pixelplayeross.data.model.ArtistRef
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import com.lostf1sh.pixelplayeross.utils.normalizeMetadataText
import com.lostf1sh.pixelplayeross.utils.normalizeMetadataTextOrEmpty
import org.json.JSONArray
import org.json.JSONObject

/** Integer constants for the `source_type` column — faster than LIKE checks on URI scheme. */
object SourceType {
    const val LOCAL = 0
    const val NAVIDROME = 5
    const val JELLYFIN = 6

    /** Derive source type from a content URI string (fallback for migration / conversion). */
    fun fromContentUri(uri: String): Int = when {
        uri.startsWith("navidrome://") -> NAVIDROME
        uri.startsWith("jellyfin://") -> JELLYFIN
        else -> LOCAL
    }
}

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["album_id"], unique = false),
        Index(value = ["artist_id"], unique = false),
        Index(value = ["artist_name"], unique = false),
        Index(value = ["genre"], unique = false),
        Index(value = ["parent_directory_path"], unique = false),
        Index(value = ["file_path"], unique = false),
        Index(value = ["content_uri_string"], unique = false),
        Index(value = ["date_added"], unique = false),
        Index(value = ["duration"], unique = false),
        Index(value = ["source_type"], unique = false),
        Index(value = ["album_artist_id"], unique = false),
        Index(value = ["parent_directory_path", "source_type", "album_id"], unique = false),
        Index(value = ["parent_directory_path", "source_type", "id"], unique = false)
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE // If an album is deleted, its songs are too
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.SET_NULL // If an artist is deleted, the song's artist_id is set to null
                                          // or you could choose CASCADE if songs shouldn't exist without an artist.
                                          // SET_NULL is more flexible if songs can belong to "Unknown Artist".
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String, // Display string (combined or primary)
    @ColumnInfo(name = "artist_id") val artistId: Long, // Primary artist ID for backward compatibility
    @ColumnInfo(name = "album_artist") val albumArtist: String? = null, // Album artist from metadata
    // Id of the *effective* album artist (album_artist when present, else the primary track
    // artist). Source-independent and computed at sync time so the "Group by Album Artist"
    // Artists tab can collapse on it at runtime without a re-sync. 0 = unresolved.
    @ColumnInfo(name = "album_artist_id", defaultValue = "0") val albumArtistId: Long = 0L,
    @ColumnInfo(name = "album_name") val albumName: String,
    @ColumnInfo(name = "album_id") val albumId: Long, // index = true removed
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "file_path") val filePath: String, // Added filePath
    @ColumnInfo(name = "parent_directory_path") val parentDirectoryPath: String, // Added for directory filtering
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "lyrics", defaultValue = "null") val lyrics: String? = null,
    @ColumnInfo(name = "track_number", defaultValue = "0") val trackNumber: Int = 0,
    @ColumnInfo(name = "disc_number", defaultValue = "null") val discNumber: Int? = null,
    @ColumnInfo(name = "year", defaultValue = "0") val year: Int = 0,
    @ColumnInfo(name = "date_added", defaultValue = "0") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "bitrate") val bitrate: Int? = null, // bits per second
    @ColumnInfo(name = "sample_rate") val sampleRate: Int? = null, // Hz
    @ColumnInfo(name = "artists_json") val artistsJson: String? = null,
    @ColumnInfo(name = "source_type", defaultValue = "0") val sourceType: Int = SourceType.LOCAL,
    @ColumnInfo(name = "media_store_date_added", defaultValue = "0") val mediaStoreDateAdded: Long = 0L,
    @ColumnInfo(name = "media_store_date_modified", defaultValue = "0") val mediaStoreDateModified: Long = 0L,
    @ColumnInfo(name = "title_user_edited", defaultValue = "0") val titleUserEdited: Boolean = false,
    @ColumnInfo(name = "artist_user_edited", defaultValue = "0") val artistUserEdited: Boolean = false,
    @ColumnInfo(name = "album_user_edited", defaultValue = "0") val albumUserEdited: Boolean = false,
    @ColumnInfo(name = "genre_user_edited", defaultValue = "0") val genreUserEdited: Boolean = false
)

private fun SongEntity.toSongInternal(artists: List<ArtistRef>): Song {
    return Song(
        id = this.id.toString(),
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.artistName.normalizeMetadataTextOrEmpty(),
        artistId = this.artistId,
        artists = artists,
        album = this.albumName.normalizeMetadataTextOrEmpty(),
        albumId = this.albumId,
        albumArtist = this.albumArtist?.normalizeMetadataText(),
        path = this.filePath, // Map the file path
        contentUriString = this.contentUriString,
        albumArtUriString = LocalArtworkUri.resolveSongArtworkUri(
            storedUri = this.albumArtUriString,
            songId = this.id,
            contentUriString = this.contentUriString
        ),
        duration = this.duration,
        genre = this.genre.normalizeMetadataText(),
        lyrics = this.lyrics?.normalizeMetadataText(),
        isFavorite = this.isFavorite,
        trackNumber = this.trackNumber,
        discNumber = this.discNumber,
        dateAdded = this.dateAdded,
        year = this.year,
        navidromeId = if (this.contentUriString.startsWith("navidrome://")) {
            this.contentUriString.removePrefix("navidrome://")
        } else null,
        jellyfinId = if (this.contentUriString.startsWith("jellyfin://")) {
            this.contentUriString.removePrefix("jellyfin://")
        } else null,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}

fun SongEntity.toSong(): Song {
    val artists = parseArtistsJson(this.artistsJson)
    return toSongInternal(artists = artists)
}

/**
 * Parses the artists_json column back into a list of ArtistRef.
 */
private fun parseArtistsJson(json: String?): List<ArtistRef> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ArtistRef(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                isPrimary = obj.optBoolean("primary", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Serializes a list of ArtistRef to JSON for storage in artists_json column.
 */
fun serializeArtistRefs(artists: List<ArtistRef>): String {
    val arr = JSONArray()
    artists.forEach { ref ->
        arr.put(JSONObject().apply {
            put("id", ref.id)
            put("name", ref.name)
            put("primary", ref.isPrimary)
        })
    }
    return arr.toString()
}

/**
 * Converts a SongEntity to Song with artists from the junction table.
 */
fun SongEntity.toSongWithArtistRefs(artists: List<ArtistEntity>, crossRefs: List<SongArtistCrossRef>): Song {
    val crossRefByArtistId = crossRefs.associateBy { it.artistId }
    val artistRefs = artists.map { artist ->
        val crossRef = crossRefByArtistId[artist.id]
        ArtistRef(
            id = artist.id,
            name = artist.name.normalizeMetadataTextOrEmpty(),
            isPrimary = crossRef?.isPrimary ?: false
        )
    }.sortedByDescending { it.isPrimary }

    return toSongInternal(artists = artistRefs)
}

fun List<SongEntity>.toSongs(): List<Song> {
    return this.map { it.toSong() }
}

// The Song model uses id as a String, but the entity needs it as a Long (from MediaStore)
// The Song model has no filePath, so it can't be mapped from there directly.
// filePath and parentDirectoryPath are populated from MediaStore in the SyncWorker.
fun Song.toEntity(filePathFromMediaStore: String, parentDirFromMediaStore: String): SongEntity {
    return SongEntity(
        id = this.id.toLong(),
        title = this.title,
        artistName = this.artist,
        artistId = this.artistId,
        albumArtist = this.albumArtist,
        albumName = this.album,
        albumId = this.albumId,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre,
        isFavorite = this.isFavorite,
        lyrics = this.lyrics,
        trackNumber = this.trackNumber,
        discNumber = this.discNumber,
        filePath = filePathFromMediaStore,
        parentDirectoryPath = parentDirFromMediaStore,
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate,
        sourceType = SourceType.fromContentUri(this.contentUriString)
    )
}

/** Lightweight projection for backup song matching. */
data class SongSummary(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_name") val albumName: String,
    val duration: Long
)

// Overload or alternative if the paths aren't available or aren't needed when converting from Model to Entity
// (less likely to be used if the entity always requires the paths)
fun Song.toEntityWithoutPaths(): SongEntity {
    return SongEntity(
        id = this.id.toLong(),
        title = this.title,
        artistName = this.artist,
        artistId = this.artistId,
        albumArtist = this.albumArtist,
        albumName = this.album,
        albumId = this.albumId,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre,
        isFavorite = this.isFavorite,
        lyrics = this.lyrics,
        trackNumber = this.trackNumber,
        discNumber = this.discNumber,
        filePath = "",
        parentDirectoryPath = "",
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate,
        sourceType = SourceType.fromContentUri(this.contentUriString)
    )
}
