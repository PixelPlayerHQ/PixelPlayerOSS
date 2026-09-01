package com.lostf1sh.pixelplayeross.data.worker

import com.lostf1sh.pixelplayeross.utils.extractArtistsFromTitle
import com.lostf1sh.pixelplayeross.utils.splitArtistsByDelimiters

internal fun collectArtistNames(
    rawArtistName: String,
    title: String,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList(),
    extractFromTitle: Boolean = true
): List<String> {
    return collectArtistNames(
        rawArtistNames = listOf(rawArtistName),
        title = title,
        artistDelimiters = artistDelimiters,
        wordDelimiters = wordDelimiters,
        extractFromTitle = extractFromTitle
    )
}

/**
 * Splits every ARTIST field before case-insensitively de-duplicating the result.
 * The physical tag order is retained, so the first value remains the primary artist.
 */
internal fun collectArtistNames(
    rawArtistNames: List<String>,
    title: String,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList(),
    extractFromTitle: Boolean = true
): List<String> {
    val splitFromArtist = mutableListOf<String>()
    rawArtistNames.forEach { rawArtistName ->
        rawArtistName
            .splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
            .forEach { artistName ->
                if (splitFromArtist.none { it.equals(artistName, ignoreCase = true) }) {
                    splitFromArtist += artistName
                }
            }
    }
    if (!extractFromTitle) {
        return splitFromArtist
    }

    val (_, titleArtists) = title.extractArtistsFromTitle(artistDelimiters, wordDelimiters)
    if (titleArtists.isEmpty()) {
        return splitFromArtist
    }

    val combined = splitFromArtist.toMutableList()
    titleArtists.forEach { titleArtist ->
        if (combined.none { it.equals(titleArtist, ignoreCase = true) }) {
            combined.add(titleArtist)
        }
    }
    return combined
}

internal fun choosePreferredArtistName(
    localArtistName: String,
    mediaStoreArtistName: String,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList()
): String {
    val localTrimmed = localArtistName.trim()
    val mediaTrimmed = mediaStoreArtistName.trim()

    if (localTrimmed.isBlank()) return mediaStoreArtistName
    if (mediaTrimmed.isBlank()) return localArtistName

    val localArtists = localTrimmed.splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
    val mediaArtists = mediaTrimmed.splitArtistsByDelimiters(artistDelimiters, wordDelimiters)

    return when {
        mediaArtists.size > localArtists.size -> mediaStoreArtistName
        localArtists.size > mediaArtists.size -> localArtistName
        mediaTrimmed.length > localTrimmed.length -> mediaStoreArtistName
        localTrimmed.length > mediaTrimmed.length -> localArtistName
        else -> mediaStoreArtistName
    }
}
