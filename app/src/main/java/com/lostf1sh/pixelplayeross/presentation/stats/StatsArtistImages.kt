package com.lostf1sh.pixelplayeross.presentation.stats

import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/** Matches no artists row, so lookups fall through to the by-name queries. */
private const val UNKNOWN_ARTIST_ID = -1L

/**
 * The artist pictures the rest of the app shows for stats artist names: the user's custom image
 * or a local folder portrait, otherwise the Deezer picture when external artist images are
 * enabled. Names with no picture are absent. Callers choose the dispatcher.
 */
internal suspend fun resolveStatsArtistImages(
    names: Collection<String>,
    musicRepository: MusicRepository,
    artistImageRepository: ArtistImageRepository
): Map<String, String> = coroutineScope {
    names.distinct().map { name ->
        async {
            val artistId = musicRepository.getArtistIdByName(name) ?: UNKNOWN_ARTIST_ID
            val url = runCatching {
                artistImageRepository.getEffectiveArtistImageUrl(artistId, name)
            }.onFailure { Timber.w(it, "Failed to resolve artist image for %s", name) }
                .getOrNull()
            url?.takeIf { it.isNotBlank() }?.let { name to it }
        }
    }.awaitAll().filterNotNull().toMap()
}
