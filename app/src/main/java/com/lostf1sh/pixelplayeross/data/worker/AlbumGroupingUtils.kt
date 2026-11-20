package com.lostf1sh.pixelplayeross.data.worker

import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.LocalAlbumGroupingSource
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import com.lostf1sh.pixelplayeross.utils.normalizeMetadataText
import com.lostf1sh.pixelplayeross.utils.normalizeMetadataTextOrEmpty
import com.lostf1sh.pixelplayeross.utils.splitArtistsByDelimiters

internal data class AlbumGroupingKey(
    val normalizedTitle: String,
    val identity: String
)

internal class LocalAlbumIndex(
    existingAlbums: List<AlbumEntity>,
    private val groupByTitle: Boolean,
    existingFallbackSources: List<LocalAlbumGroupingSource> = emptyList(),
) {
    private val albumIds = mutableMapOf<AlbumGroupingKey, Long>()
    private val usedIds = if (groupByTitle) existingAlbums.mapTo(hashSetOf()) { it.id } else null
    private var nextAvailableId = Long.MAX_VALUE

    init {
        existingAlbums.sortedBy { it.id }.forEach { album ->
            val keys = if (groupByTitle) listOfNotNull(buildTitleOnlyAlbumKey(album.title))
            else buildAlbumGroupingKeys(album)
            keys.forEach { key ->
                albumIds.putIfAbsent(key, album.id)
            }
        }
        if (groupByTitle) {
            existingFallbackSources.groupBy { it.albumId }.toSortedMap().forEach { (albumId, sources) ->
                val key = sources.mapNotNull { source ->
                    if (buildTitleOnlyAlbumKey(source.albumName) != null) return@mapNotNull null
                    buildAlbumGroupingKey(
                        albumName = source.albumName,
                        albumArtist = source.albumArtist,
                        albumArtUriString = null,
                        parentDirectoryPath = source.parentDirectoryPath,
                        fallbackAlbumId = albumId,
                        preferStableLocalIdentity = true,
                    )
                }.distinct().singleOrNull()
                if (key != null) albumIds.putIfAbsent(key, albumId)
            }
        }
    }

    fun idFor(song: SongEntity): Long =
        albumIds.getOrPut(buildAlbumGroupingKey(song, groupByTitle)) {
            if (usedIds == null || usedIds.add(song.albumId)) {
                song.albumId
            } else {
                while (!usedIds.add(nextAvailableId)) nextAvailableId--
                nextAvailableId--
            }
        }
}

internal fun resolveAlbumArtist(
    rawAlbumArtist: String?,
    metadataAlbumArtist: String?
): String? {
    return sequenceOf(metadataAlbumArtist, rawAlbumArtist)
        .mapNotNull { candidate ->
            candidate.normalizeMetadataText()
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        }
        .firstOrNull()
}

internal fun buildAlbumGroupingKey(song: SongEntity, groupByTitle: Boolean = false): AlbumGroupingKey {
    val localMedia = LocalArtworkUri.isLikelyLocalMedia(song.contentUriString)
    if (groupByTitle && localMedia) {
        buildTitleOnlyAlbumKey(song.albumName)?.let { return it }
    }
    return buildAlbumGroupingKey(
        albumName = song.albumName,
        albumArtist = song.albumArtist,
        albumArtUriString = song.albumArtUriString,
        parentDirectoryPath = song.parentDirectoryPath,
        fallbackAlbumId = song.albumId,
        preferStableLocalIdentity = localMedia
    )
}

internal fun buildAlbumGroupingKeys(album: AlbumEntity): List<AlbumGroupingKey> {
    val albumKeys = mutableListOf<AlbumGroupingKey>()

    albumKeys += buildAlbumGroupingKey(
        albumName = album.title,
        albumArtist = album.artistName,
        albumArtUriString = album.albumArtUriString,
        parentDirectoryPath = null,
        fallbackAlbumId = album.id
    )

    albumKeys += AlbumGroupingKey(
        normalizedTitle = album.title.normalizeMetadataTextOrEmpty()
            .ifBlank { "Unknown Album" }
            .lowercase(),
        identity = "media:${album.id}"
    )

    if (!album.albumArtUriString.isNullOrBlank()) {
        albumKeys += AlbumGroupingKey(
            normalizedTitle = album.title.normalizeMetadataTextOrEmpty()
                .ifBlank { "Unknown Album" }
                .lowercase(),
            identity = "art:${album.albumArtUriString.trim()}"
        )
    }

    return albumKeys.distinct()
}

internal fun buildTitleOnlyAlbumKey(albumName: String): AlbumGroupingKey? {
    val title = albumName.normalizeMetadataTextOrEmpty().lowercase()
    return when (title) {
        "", "<unknown>", "unknown", "unknown album" -> null
        else -> AlbumGroupingKey(title, "title")
    }
}

internal fun chooseAlbumDisplayArtist(
    songs: List<SongEntity>,
    preferAlbumArtist: Boolean
): String {
    if (songs.isEmpty()) return "Unknown Artist"

    val albumArtist = mostCommonValue(
        songs.mapNotNull { song ->
            song.albumArtist.normalizeMetadataText()?.takeIf { it.isNotBlank() }
        }
    )
    val trackArtist = mostCommonValue(
        songs.map { song ->
            song.artistName.normalizeMetadataTextOrEmpty()
        }
    )

    return when {
        preferAlbumArtist && albumArtist != null -> albumArtist
        trackArtist != null -> trackArtist
        albumArtist != null -> albumArtist
        else -> "Unknown Artist"
    }
}

internal fun resolveAlbumDisplayArtistId(
    displayArtist: String,
    songs: List<SongEntity>,
    artistNameToId: Map<String, Long>,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList()
): Long {
    artistNameToId[displayArtist.trim()]?.let { return it }

    val primaryArtistName = displayArtist
        .splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
        .firstOrNull()
        ?.trim()
    if (!primaryArtistName.isNullOrEmpty()) {
        artistNameToId[primaryArtistName]?.let { return it }
    }

    return songs.firstOrNull()?.artistId ?: 0L
}

private fun mostCommonValue(values: List<String>): String? {
    return values
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
        ?.key
}

private fun buildAlbumGroupingKey(
    albumName: String,
    albumArtist: String?,
    albumArtUriString: String?,
    parentDirectoryPath: String?,
    fallbackAlbumId: Long,
    preferStableLocalIdentity: Boolean = false
): AlbumGroupingKey {
    val normalizedTitle = albumName.normalizeMetadataTextOrEmpty()
        .ifBlank { "Unknown Album" }
        .lowercase()
    val stableLocalIdentity = when {
        !parentDirectoryPath.isNullOrBlank() -> {
            "dir:${parentDirectoryPath.trim().lowercase()}"
        }
        else -> {
            "media:$fallbackAlbumId"
        }
    }
    val identity = when {
        !albumArtist.isNullOrBlank() -> {
            "artist:${albumArtist.normalizeMetadataTextOrEmpty().lowercase()}"
        }
        preferStableLocalIdentity -> {
            stableLocalIdentity
        }
        !albumArtUriString.isNullOrBlank() -> {
            "art:${albumArtUriString.trim()}"
        }
        !parentDirectoryPath.isNullOrBlank() -> {
            "dir:${parentDirectoryPath.trim().lowercase()}"
        }
        else -> {
            "media:$fallbackAlbumId"
        }
    }

    return AlbumGroupingKey(
        normalizedTitle = normalizedTitle,
        identity = identity
    )
}
