package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.lostf1sh.pixelplayeross.data.model.Song
import kotlin.math.ln

/**
 * A song with its textual fields pre-normalized and pre-stemmed, so the scorer never has to
 * re-tokenize the library on the hot path.
 */
class IndexedSong(
    val song: Song,
    val titleStems: Set<String>,
    val artistStems: Set<String>,
    val albumStems: Set<String>,
    val genreStems: Set<String>,
    val normalizedArtist: String,
    val normalizedGenre: String,
)

/**
 * An inverted-frequency view of the whole library, built once and reused across requests.
 *
 * The index provides the two things the offline scorer needs to move beyond flat, static
 * weights:
 *  - [idf]: an inverse-document-frequency weight so that matching a rare term (an unusual
 *    band name) counts far more than matching a ubiquitous one ("love", "remix").
 *  - [knownGenres]: the set of genres actually present in the user's library, used for the
 *    genre slot in query parsing (we only treat a query token as a genre if the library has it).
 *
 * Building is O(N × tokens); the result is immutable and cached by [signatureOf] so that the
 * common case (library unchanged between requests) skips the build entirely — important for
 * large libraries where the caller may be invoked repeatedly.
 */
class LibraryIndex private constructor(
    val signature: Long,
    val songs: List<IndexedSong>,
    val knownGenres: Set<String>,
    val knownGenreStems: Set<String>,
    private val documentFrequency: Map<String, Int>,
    private val documentCount: Int,
) {

    /** Ceiling weight used for terms absent from the library (treated as maximally rare). */
    private val maxIdf: Double = ln(1.0 + documentCount.coerceAtLeast(1))

    /**
     * Inverse document frequency for a stemmed term: `ln(1 + N / df)`. Common terms trend
     * toward 0; rare/unseen terms trend toward [maxIdf]. Never below a small floor so that a
     * matched term always contributes something.
     */
    fun idf(stem: String): Double {
        val df = documentFrequency[stem] ?: return maxIdf
        return ln(1.0 + documentCount.toDouble() / df).coerceAtLeast(MIN_IDF)
    }

    /**
     * Rarity multiplier in `(0, 1]`, i.e. [idf] normalized by [maxIdf]. Multiply a field's
     * base weight by this so a match on a rare term counts near full weight while a match on
     * a ubiquitous term counts near zero — keeping text scores comparable in magnitude to the
     * structural genre/decade bonuses regardless of library size.
     */
    fun termWeight(stem: String): Double = (idf(stem) / maxIdf).coerceIn(MIN_IDF, 1.0)

    companion object {
        private const val MIN_IDF = 0.05

        /**
         * Cheap fingerprint of the library used to decide whether a cached index can be reused.
         * Folds song count, ids, the max modification timestamp, and — critically — the genre of
         * each song, so that an in-app genre edit rebuilds the index even when it doesn't bump
         * dateModified. Without the genre fold, tagging a song (e.g. "Alternative" -> "Nu Metal")
         * left the mood scorer reading the stale genre, so the edit appeared to do nothing.
         * O(N) but allocation-free.
         */
        fun signatureOf(songs: List<Song>): Long {
            var hash = 1125899906842597L // large prime seed
            var maxModified = 0L
            for (song in songs) {
                hash = 31 * hash + song.id.hashCode()
                hash = 31 * hash + (song.genre?.hashCode() ?: 0)
                if (song.dateModified > maxModified) maxModified = song.dateModified
            }
            return hash * 31 + maxModified + songs.size
        }

        /** Builds the immutable index. Callers should cache the result keyed on [signatureOf]. */
        fun build(songs: List<Song>): LibraryIndex {
            val signature = signatureOf(songs)
            val documentFrequency = HashMap<String, Int>()
            val knownGenres = HashSet<String>()
            val knownGenreStems = HashSet<String>()
            val indexed = ArrayList<IndexedSong>(songs.size)

            for (song in songs) {
                val titleStems = NlpText.stemTokens(song.title).toHashSet()
                val artistStems = NlpText.stemTokens(song.displayArtist).toHashSet()
                val albumStems = NlpText.stemTokens(song.album).toHashSet()
                val normalizedGenre = song.genre?.let { NlpText.normalize(it).trim() }.orEmpty()
                val normalizedArtist = NlpText.normalize(song.displayArtist).trim()
                val genreStems: Set<String> =
                    if (normalizedGenre.isEmpty()) emptySet() else NlpText.stemTokens(normalizedGenre).toHashSet()

                if (normalizedGenre.isNotEmpty()) {
                    knownGenres.add(normalizedGenre)
                    knownGenreStems.addAll(genreStems)
                }

                indexed.add(
                    IndexedSong(
                        song = song,
                        titleStems = titleStems,
                        artistStems = artistStems,
                        albumStems = albumStems,
                        genreStems = genreStems,
                        normalizedArtist = normalizedArtist,
                        normalizedGenre = normalizedGenre,
                    )
                )

                // A term counts once per song toward document frequency.
                val documentTerms = HashSet<String>(
                    titleStems.size + artistStems.size + albumStems.size + genreStems.size + 1
                )
                documentTerms.addAll(titleStems)
                documentTerms.addAll(artistStems)
                documentTerms.addAll(albumStems)
                documentTerms.addAll(genreStems)
                for (term in documentTerms) {
                    documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
                }
            }

            return LibraryIndex(
                signature = signature,
                songs = indexed,
                knownGenres = knownGenres,
                knownGenreStems = knownGenreStems,
                documentFrequency = documentFrequency,
                documentCount = songs.size,
            )
        }
    }
}
