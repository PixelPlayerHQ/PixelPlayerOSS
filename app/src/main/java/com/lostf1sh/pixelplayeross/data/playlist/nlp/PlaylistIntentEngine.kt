package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.lostf1sh.pixelplayeross.data.DailyMixManager.SongEngagementStats
import kotlin.random.Random

/**
 * A song's measured acoustic "vibe vector", decoupled from any DB/Android type so the pure
 * `nlp` package stays dependency-free. Optional: this app performs no on-device audio
 * analysis, so callers typically pass no vibes and the engine falls back to genre-derived
 * energy. Only songs with a *complete* vector (no null feature) should get an entry.
 */
data class SongVibe(
    val energy: Float,
    val brightness: Float,
    val dynamics: Float,
    val percussiveness: Float,
)

/**
 * The heart of the offline playlist curator. Turns a free-text query into a ranked list of
 * song ids using a rule-based slot filler ([parse]) plus TF‑IDF-weighted scoring ([generate]).
 *
 * Design goals:
 *  - **Semantic reach without an LLM**: mood/intent is matched via stemmed synonyms and
 *    multi-word phrases ([MoodProfile]), so "tunes to lift weights to" triggers the workout
 *    profile even though the words "workout"/"gym" never appear.
 *  - **Accent/inflection tolerance**: both sides go through [NlpText.normalize] + stemming,
 *    so "melancólico" == "melancolico" and "relajantes" ~ "relajante".
 *  - **Rarity-aware weighting**: text matches are scaled by [LibraryIndex.termWeight] so a
 *    rare artist name outranks a common word like "love" in a title.
 *  - **Graceful degradation**: measured BPM and vibe inputs are optional; without them the
 *    engine scores on genre-derived energy and text/behavioral signals alone.
 */
object PlaylistIntentEngine {

    // Structural weights.
    private const val DECADE_BONUS = 25.0
    private const val GENRE_BONUS = 30.0
    private const val EXACT_ARTIST_BONUS = 40.0

    /** Reward for a song sharing a named genre's super-family without an exact stem match (a
     *  "grunge" track for a "rock" query). Smaller than [GENRE_BONUS], which rewards exact hits. */
    private const val GENRE_FAMILY_BONUS = 18.0

    // Field base weights, multiplied by the term's rarity weight in (0, 1].
    private const val ARTIST_BASE = 15.0
    private const val TITLE_BASE = 10.0
    private const val ALBUM_BASE = 5.0

    // Mood extras that are not simple genre/behavior bonuses.
    private const val CHILL_LONG_DURATION_MS = 200_000L
    private const val CHILL_DURATION_BONUS = 3.0
    private const val WORKOUT_FAMILIARITY_MIN_PLAYS = 5
    private const val WORKOUT_FAMILIARITY_BONUS = 5.0

    private const val RECENT_POOL_SIZE = 20

    // Tempo/energy scoring. Measured BPM (from embedded tags, when available) is trusted with
    // a tight gaussian; genre-derived energy is a coarse fallback with a wider curve and softer
    // weights. Both add a hard penalty when far off target so a query like "chillout @60bpm"
    // actively excludes high-energy songs instead of merely under-scoring them.
    private const val BPM_MATCH_MAX = 30.0
    private const val BPM_SIGMA = 18.0
    private const val BPM_MISMATCH_THRESHOLD = 45.0
    private const val BPM_MISMATCH_PENALTY = -20.0
    private const val GENRE_ENERGY_MATCH_MAX = 15.0
    private const val GENRE_ENERGY_SIGMA = 32.0
    private const val GENRE_ENERGY_MISMATCH_THRESHOLD = 55.0
    private const val GENRE_ENERGY_MISMATCH_PENALTY = -12.0

    // Measured acoustic vibe (optional caller-supplied data): proximity in the 4-feature 0..1
    // space to a matched GENRE mood's target vector. Weighted in the same ballpark as
    // GENRE_ENERGY_MATCH_MAX since it replaces that coarse heuristic whenever a song has real
    // vibe data. Beyond VIBE_MISMATCH_THRESHOLD (Euclidean distance in the 0..1 feature space)
    // it flips to a hard penalty, mirroring bpmAffinity — otherwise the vibe path could only
    // ever *reward*, so a song whose measured vibe is the opposite of the mood would merely
    // score ~0 instead of being excluded, defeating the whole point of measuring it.
    private const val VIBE_MATCH_MAX = 15.0
    private const val VIBE_SIGMA = 0.5
    private const val VIBE_MISMATCH_THRESHOLD = 0.7
    private const val VIBE_MISMATCH_PENALTY = -25.0

    /** Penalty for songs whose genre energy sits outside a matched mood's acceptable band. */
    private const val MOOD_ENERGY_CONFLICT_PENALTY = -25.0

    /**
     * Ceiling on the summed, confidence-weighted mood bonus for a single song. Without a cap a
     * query naming several moods ("happy workout party") would let each matched profile add its
     * full [MoodProfile.bonus], swamping a more specific single-word query. Chosen to comfortably
     * exceed one strong single-mood hit (bonus 15-35 × strength up to 1.0) while still limiting
     * the combined total of several.
     */
    private const val MAX_TOTAL_MOOD_BONUS = 45.0

    /**
     * Penalty applied per negated term a song matches ("rock without metal"). Large enough in
     * magnitude to dominate the strongest positive bonus ([EXACT_ARTIST_BONUS] = 40,
     * [GENRE_BONUS] = 30), so a negated match always loses the argument.
     */
    private const val NEGATION_PENALTY = -60.0

    /**
     * Applied to a "similar to X" seed artist's own songs, so they drop out of the results —
     * the user asked for something *like* the artist, implying not the artist itself.
     */
    private const val SIMILARITY_SEED_EXCLUSION = -50.0

    /** Reward for sharing a genre stem with the resolved "similar to X" seed artist. */
    private const val SIMILARITY_GENRE_BONUS = 30.0

    /**
     * Structured directives a UI may append to the free-text prompt. Parsed into structured
     * slots and stripped so their words never leak into text matching.
     */
    private val TARGET_BPM_DIRECTIVE = "\\[\\s*target\\s+bpm\\s*:\\s*(\\d+)[^\\]]*\\]".toRegex(RegexOption.IGNORE_CASE)
    private val TARGET_LENGTH_DIRECTIVE = "\\[\\s*target\\s+length\\s*:[^\\]]*\\]".toRegex(RegexOption.IGNORE_CASE)

    /** Structured interpretation of a query against a specific library. */
    data class ParsedQuery(
        val normalizedQuery: String,
        val contentStems: Set<String>,
        /** Per-term rarity weight in (0, 1], resolved from the library at parse time. */
        val termWeights: Map<String, Double>,
        val yearRange: IntRange?,
        val matchedGenreStems: Set<String>,
        val moods: Set<MoodProfile>,
        /** Explicit tempo target from a BPM directive, when present in the prompt. */
        val targetBpm: Float? = null,
        /** Per-mood trigger confidence in `[0, 1]`; see [MoodProfile.matchStrength]. */
        val moodStrengths: Map<MoodProfile, Double> = emptyMap(),
        /** Stems the user explicitly negated ("without metal"). Penalized, never rewarded. */
        val negatedStems: Set<String> = emptySet(),
        /** When the user asked for songs *like* an artist: that artist's normalized name. */
        val similarToArtist: String? = null,
        /** Genre stems shared across [similarToArtist]'s catalog, for the similarity bonus. */
        val similarSeedGenreStems: Set<String> = emptySet(),
        /** Genre super-families the user explicitly named ("rock", "phonk"); drives family reward
         *  and cross-family exclusion. Empty for mood/free-text-only queries. */
        val queryGenreFamilies: Set<GenreFamily> = emptySet(),
    ) {
        val hasSelectionSignal: Boolean
            get() = contentStems.isNotEmpty() || yearRange != null

        /** True when the query expresses a tempo/energy preference, explicit or via mood. */
        val hasEnergySignal: Boolean
            get() = targetBpm != null || moods.any { it.kind == MoodKind.GENRE }

        /** The tempo the query is aiming for: explicit directive first, then mood implication. */
        val effectiveTargetBpm: Float?
            get() = targetBpm ?: moods.mapNotNull { it.preferredBpm }.let { prefs ->
                if (prefs.isEmpty()) null else prefs.sum() / prefs.size
            }
    }

    /** Extracts decade, genre, mood, tempo and free-text slots from [query]. Pure and side-effect free. */
    fun parse(query: String, index: LibraryIndex): ParsedQuery {
        // Structured directives come out first so "target"/"bpm"/"60" never pollute the
        // free-text terms (they would otherwise score as ordinary words and break mood detection).
        val targetBpm = TARGET_BPM_DIRECTIVE.find(query)?.groupValues?.get(1)?.toFloatOrNull()
        val cleanQuery = query
            .replace(TARGET_BPM_DIRECTIVE, " ")
            .replace(TARGET_LENGTH_DIRECTIVE, " ")

        val normalizedQuery = NlpText.normalize(cleanQuery).trim()
        if (normalizedQuery.isEmpty()) {
            return ParsedQuery(
                normalizedQuery, emptySet(), emptyMap(), null, emptySet(), emptySet(), targetBpm
            )
        }

        val tokens = NlpText.tokenize(cleanQuery)
        var yearRange: IntRange? = null
        val contentStems = HashSet<String>()

        // Limitation: negation is token-level only — a negated word inside a multi-word mood
        // phrase ("feel good" containing a negated "good") is not suppressed. Out of scope for v1.
        var negateNext = false
        val negatedStems = HashSet<String>()
        for (token in tokens) {
            val decade = decadeRangeOf(token)
            if (decade != null) {
                // Last decade marker wins; not a free-text term.
                yearRange = decade
                negateNext = false
                continue
            }
            if (NlpLexicon.isNegationCue(token)) {
                negateNext = true
                continue
            }
            if (NlpLexicon.isStopWord(token)) continue // stopwords don't consume the negation
            val stem = NlpText.stem(token)
            if (negateNext) {
                negatedStems.add(stem)
                negateNext = false
            } else {
                contentStems.add(stem)
            }
        }

        val termWeights = contentStems.associateWith { index.termWeight(it) }
        val matchedGenreStems = contentStems.intersect(index.knownGenreStems)
        val queryGenreFamilies = GenreTaxonomy.queryFamilies(contentStems, normalizedQuery)
        val moodStrengths = buildMap {
            for (mood in MoodProfile.entries) {
                val strength = mood.matchStrength(contentStems, normalizedQuery)
                if (strength > 0.0) put(mood, strength)
            }
        }
        val moods = moodStrengths.keys

        // "Something like Radiohead": only activates when a seed artist actually resolves in
        // the library, so an ambiguous cue like "like"/"como" in "I like happy songs" leaves
        // the query untouched (half-activating similarity mode would be worse than not trying).
        val seed = resolveSeedArtist(normalizedQuery, index)
        val similarToArtist = seed?.normalizedArtist
        val similarSeedGenreStems: Set<String> = if (seed != null) {
            index.songs.asSequence()
                .filter { it.normalizedArtist == seed.normalizedArtist }
                .flatMapTo(HashSet()) { it.genreStems }
        } else {
            emptySet()
        }

        return ParsedQuery(
            normalizedQuery, contentStems, termWeights, yearRange, matchedGenreStems, moods, targetBpm,
            moodStrengths = moodStrengths,
            negatedStems = negatedStems,
            similarToArtist = similarToArtist,
            similarSeedGenreStems = similarSeedGenreStems,
            queryGenreFamilies = queryGenreFamilies,
        )
    }

    /**
     * Resolves the "seed" artist for a "like X" / "similar to X" query: finds the rightmost
     * similarity cue in [normalizedQuery], takes the substring after it, and matches it against
     * the library's artist names (exact match first, else containment). Returns null when no
     * cue is present or no artist resolves, so the caller can fall back to normal query handling
     * instead of half-activating similarity mode.
     */
    private fun resolveSeedArtist(normalizedQuery: String, index: LibraryIndex): IndexedSong? {
        val cueEnd = NlpLexicon.similarityPhrases
            .mapNotNull { cue ->
                val idx = normalizedQuery.indexOf(cue)
                if (idx < 0) null else idx + cue.length
            }
            .maxOrNull() ?: return null

        val after = normalizedQuery.substring(cueEnd).trim()
        if (after.isEmpty()) return null

        index.songs.firstOrNull { it.normalizedArtist.isNotEmpty() && it.normalizedArtist == after }
            ?.let { return it }

        return index.songs
            .filter {
                it.normalizedArtist.isNotEmpty() &&
                    (it.normalizedArtist.contains(after) || after.contains(it.normalizedArtist))
            }
            .maxByOrNull { it.normalizedArtist.length }
    }

    /**
     * Produces an ordered list of song ids for the query. Deterministic except for the final
     * target-size sampling, which uses [random] (injectable for tests).
     */
    fun generate(
        query: String,
        minLength: Int,
        maxLength: Int,
        index: LibraryIndex,
        engagements: Map<String, SongEngagementStats>,
        random: Random = Random.Default,
        bpmBySongId: Map<String, Float> = emptyMap(),
        vibeBySongId: Map<String, SongVibe> = emptyMap(),
    ): List<String> {
        if (index.songs.isEmpty()) return emptyList()

        val parsed = parse(query, index)

        val recentlyAdded: Set<String> = if (MoodProfile.RECENT in parsed.moods) {
            index.songs.asSequence()
                .sortedByDescending { it.song.dateAdded }
                .take(RECENT_POOL_SIZE)
                .mapTo(HashSet()) { it.song.id }
        } else {
            emptySet()
        }

        // Score every song. Triple = (indexedSong, score, playCount-for-tiebreak).
        val scored = index.songs.map { indexed ->
            val playCount = engagements[indexed.song.id]?.playCount ?: 0
            val score = scoreSong(indexed, parsed, playCount, recentlyAdded, bpmBySongId, vibeBySongId)
            Triple(indexed, score, playCount)
        }

        // A song whose *known* metadata actively contradicts the request must never appear — not
        // through an incidental text bonus, and crucially not as fallback padding. Two independent
        // contradictions: a mood energy/vibe conflict (recognized heavy metal vs a "chillout"
        // request) and a genre-family conflict (a phonk track vs a "rock" request). Songs with no
        // genre/BPM/vibe evidence are left in: offline we can't prove they don't fit, and a
        // tagged/untagged mix would otherwise return nothing. Each helper is a no-op when its
        // signal is absent, so this is safe for pure free-text queries too. (Tagging a song
        // supplies the evidence needed to exclude it.)
        val eligible = scored.filterNot {
            conflictsWithMood(it.first, parsed, bpmBySongId, vibeBySongId) ||
                conflictsWithGenre(it.first, parsed)
        }

        val candidates = if (parsed.hasSelectionSignal || parsed.hasEnergySignal) {
            val matched = eligible.filter { it.second > 0.0 }
            when {
                matched.isNotEmpty() -> matched
                // A mood/tempo query with zero positive matches degrades to the *eligible* songs
                // closest in energy to what was asked — never to raw play counts (how "chillout"
                // once returned the user's most-played heavy metal), and never to conflicting
                // songs (already removed from `eligible` above). If nothing is eligible the mix
                // comes back short or empty, which beats padding it with the opposite mood.
                parsed.hasEnergySignal -> energyRankedFallback(eligible, parsed, bpmBySongId, vibeBySongId, maxLength)
                else -> eligible.sortedByDescending { it.third }.take(maxLength)
            }
        } else {
            eligible
        }

        val sorted = candidates
            .sortedWith(compareByDescending<Triple<IndexedSong, Double, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first.song.id }

        return sorted.take(targetSize(sorted.size, minLength, maxLength, random))
    }

    private fun scoreSong(
        indexed: IndexedSong,
        parsed: ParsedQuery,
        playCount: Int,
        recentlyAdded: Set<String>,
        bpmBySongId: Map<String, Float>,
        vibeBySongId: Map<String, SongVibe> = emptyMap(),
    ): Double {
        val song = indexed.song
        var score = 0.0

        // Decade.
        if (parsed.yearRange != null && song.year in parsed.yearRange) {
            score += DECADE_BONUS
        }

        // Genre slot (stem intersection covers exact + fuzzy singular/plural matches).
        if (parsed.matchedGenreStems.isNotEmpty() &&
            indexed.genreStems.any { it in parsed.matchedGenreStems }
        ) {
            score += GENRE_BONUS
        }

        // Genre super-family match: rewards genre-adjacent songs a named-genre query wants even
        // when the exact word differs ("grunge"/"punk" for a "rock" request). Cross-family songs
        // are already removed in `generate`, so this only ever adds.
        if (parsed.queryGenreFamilies.isNotEmpty() &&
            GenreTaxonomy.familyOf(indexed.normalizedGenre) in parsed.queryGenreFamilies
        ) {
            score += GENRE_FAMILY_BONUS
        }

        // Free-text terms, weighted by rarity so distinctive words dominate common ones.
        for (stem in parsed.contentStems) {
            val weight = parsed.termWeights[stem] ?: 1.0
            if (stem in indexed.artistStems) score += ARTIST_BASE * weight
            if (stem in indexed.titleStems) score += TITLE_BASE * weight
            if (stem in indexed.albumStems) score += ALBUM_BASE * weight
        }

        if (parsed.similarToArtist != null) {
            // Similarity mode: reward the seed artist's genre neighborhood, exclude the seed's
            // own tracks (the user said "like", implying not the same thing), and suppress the
            // literal exact-artist bonus below — it would just re-add the seed artist's songs.
            if (indexed.normalizedArtist == parsed.similarToArtist) {
                score += SIMILARITY_SEED_EXCLUSION
            }
            if (indexed.genreStems.any { it in parsed.similarSeedGenreStems }) {
                score += SIMILARITY_GENRE_BONUS
            }
        } else {
            // Exact / prefix artist match on the whole query.
            val q = parsed.normalizedQuery
            if (q.isNotBlank() && (indexed.normalizedArtist == q || (q.length >= 3 && indexed.normalizedArtist.contains(q)))) {
                score += EXACT_ARTIST_BONUS
            }
        }

        // Mood / profile bonuses, weighted by trigger confidence and capped so a query naming
        // several moods can't out-score one specific, strongly-matched mood.
        var moodBonus = 0.0
        for (mood in parsed.moods) {
            val strength = parsed.moodStrengths[mood] ?: 1.0
            val kindBonus = when (mood.kind) {
                MoodKind.GENRE -> if (mood.matchesGenre(indexed.normalizedGenre)) mood.bonus else 0.0
                MoodKind.FAVORITES -> if (song.isFavorite) mood.bonus else 0.0
                MoodKind.DISCOVERY -> if (playCount == 0) mood.bonus else 0.0
                MoodKind.RECENT -> if (song.id in recentlyAdded) mood.bonus else 0.0
            }
            moodBonus += kindBonus * strength
            // Mood "extras" (a long track reads as chill; a familiar track as a workout staple)
            // are tie-breakers *among songs that already fit the mood* — gate them behind an
            // actual genre-family match. Otherwise a long metal track collects the CHILL bonus,
            // and a beloved ballad collects the WORKOUT one, which is exactly how heavy songs
            // used to leak into a "chillout" request when the library skews heavy.
            if (kindBonus > 0.0) {
                when (mood) {
                    MoodProfile.CHILL -> if (song.duration > CHILL_LONG_DURATION_MS) score += CHILL_DURATION_BONUS
                    MoodProfile.WORKOUT -> if (playCount > WORKOUT_FAMILIARITY_MIN_PLAYS) score += WORKOUT_FAMILIARITY_BONUS
                    else -> Unit
                }
            }
        }
        score += moodBonus.coerceAtMost(MAX_TOTAL_MOOD_BONUS)

        score += energyScore(indexed, parsed, bpmBySongId, vibeBySongId)

        // Negated terms: actively demote songs that match them on any field.
        for (stem in parsed.negatedStems) {
            if (stem in indexed.artistStems || stem in indexed.titleStems ||
                stem in indexed.albumStems || stem in indexed.genreStems
            ) {
                score += NEGATION_PENALTY
            }
        }

        return score
    }

    /**
     * Tempo/energy contribution: proximity to the target BPM (measured beats genre-derived),
     * plus a per-matched-GENRE-mood term that is either a measured-vibe proximity bonus (when
     * the song has real audio features) or the coarse genre-energy conflict penalty (the
     * original heuristic, used as a fallback). Genre energy — not measured tempo — drives the
     * fallback conflict check, because a slow metal track is still not "chill": timbre lives in
     * the genre, tempo in the BPM.
     */
    private fun energyScore(
        indexed: IndexedSong,
        parsed: ParsedQuery,
        bpmBySongId: Map<String, Float>,
        vibeBySongId: Map<String, SongVibe> = emptyMap(),
    ): Double {
        var score = 0.0
        val measuredBpm = bpmBySongId[indexed.song.id]
        val genreEnergy = LocalMetadataHeuristics.genreEnergy(indexed.normalizedGenre)
        val vibe = vibeBySongId[indexed.song.id]

        val targetBpm = parsed.effectiveTargetBpm
        if (targetBpm != null) {
            if (measuredBpm != null) {
                score += bpmAffinity(
                    measuredBpm.toDouble(), targetBpm.toDouble(),
                    BPM_MATCH_MAX, BPM_SIGMA, BPM_MISMATCH_THRESHOLD, BPM_MISMATCH_PENALTY,
                )
            } else if (genreEnergy != null) {
                score += bpmAffinity(
                    genreEnergyToBpm(genreEnergy), targetBpm.toDouble(),
                    GENRE_ENERGY_MATCH_MAX, GENRE_ENERGY_SIGMA,
                    GENRE_ENERGY_MISMATCH_THRESHOLD, GENRE_ENERGY_MISMATCH_PENALTY,
                )
            }
        }

        for (mood in parsed.moods) {
            if (mood.kind != MoodKind.GENRE) continue
            if (vibe != null) {
                // Measured audio beats the coarse genre-string guess: a song can have a wrong
                // or empty genre tag and still be correctly identified as chill by its vibe.
                score += vibeAffinity(vibe, mood)
            } else if (genreEnergy != null) {
                if (genreEnergy < mood.minEnergy || genreEnergy > mood.maxEnergy) {
                    score += MOOD_ENERGY_CONFLICT_PENALTY
                }
            }
        }

        return score
    }

    /**
     * Proximity bonus between a song's measured [vibe] and a matched GENRE [mood]'s target
     * vector: Euclidean distance over whichever features the mood defines, mapped through a
     * gaussian (mirrors [bpmAffinity]'s shape). Returns 0 for moods with no target vector
     * (behavioral profiles never reach here since they're not [MoodKind.GENRE]).
     */
    private fun vibeAffinity(vibe: SongVibe, mood: MoodProfile): Double {
        val distance = vibeDistance(vibe, mood) ?: return 0.0
        var affinity = VIBE_MATCH_MAX * kotlin.math.exp(-(distance / VIBE_SIGMA) * (distance / VIBE_SIGMA))
        if (distance > VIBE_MISMATCH_THRESHOLD) affinity += VIBE_MISMATCH_PENALTY
        return affinity
    }

    /** Euclidean distance between a measured [vibe] and a [mood]'s target vector, over whichever
     *  features the mood defines; null when the mood has no target vector (behavioral profiles). */
    private fun vibeDistance(vibe: SongVibe, mood: MoodProfile): Double? {
        var sumSquares = 0.0
        var dimensions = 0
        mood.targetEnergy?.let { sumSquares += diffSquared(vibe.energy, it); dimensions++ }
        mood.targetBrightness?.let { sumSquares += diffSquared(vibe.brightness, it); dimensions++ }
        mood.targetDynamics?.let { sumSquares += diffSquared(vibe.dynamics, it); dimensions++ }
        mood.targetPercussiveness?.let { sumSquares += diffSquared(vibe.percussiveness, it); dimensions++ }
        if (dimensions == 0) return null
        return kotlin.math.sqrt(sumSquares)
    }

    /**
     * True when the song's *known* evidence directly contradicts a matched GENRE mood, so it must
     * be dropped outright rather than merely under-scored. "Known" is the operative word: a song
     * with no vibe, no measured BPM and an unrecognized/empty genre returns false — offline we
     * cannot prove it doesn't fit. Checks in trust order: measured vibe, then measured BPM against
     * an explicit directive target, then the coarse genre-energy band.
     */
    private fun conflictsWithMood(
        indexed: IndexedSong,
        parsed: ParsedQuery,
        bpmBySongId: Map<String, Float>,
        vibeBySongId: Map<String, SongVibe>,
    ): Boolean {
        val vibe = vibeBySongId[indexed.song.id]
        val genreEnergy = LocalMetadataHeuristics.genreEnergy(indexed.normalizedGenre)

        for (mood in parsed.moods) {
            if (mood.kind != MoodKind.GENRE) continue
            if (vibe != null) {
                val distance = vibeDistance(vibe, mood)
                if (distance != null && distance > VIBE_MISMATCH_THRESHOLD) return true
            } else if (genreEnergy != null &&
                (genreEnergy < mood.minEnergy || genreEnergy > mood.maxEnergy)
            ) {
                return true
            }
        }

        // Explicit directive tempo: a measured tempo far from it is a hard mismatch on its own.
        val targetBpm = parsed.targetBpm
        val measuredBpm = bpmBySongId[indexed.song.id]
        if (targetBpm != null && measuredBpm != null &&
            kotlin.math.abs(measuredBpm - targetBpm) > BPM_MISMATCH_THRESHOLD
        ) {
            return true
        }

        return false
    }

    /**
     * True when the user named a genre and the song's *recognized* genre belongs to a different
     * super-family (a phonk track against a "rock" request). Unknown/empty genres return false —
     * offline we can't place them, so they stay eligible rather than being wrongly excluded.
     */
    private fun conflictsWithGenre(indexed: IndexedSong, parsed: ParsedQuery): Boolean {
        if (parsed.queryGenreFamilies.isEmpty()) return false
        val family = GenreTaxonomy.familyOf(indexed.normalizedGenre) ?: return false
        return family !in parsed.queryGenreFamilies
    }

    private fun diffSquared(actual: Float, target: Float): Double {
        val diff = (actual - target).toDouble()
        return diff * diff
    }

    /** Gaussian proximity bonus plus a hard penalty beyond [mismatchThreshold] BPM away. */
    private fun bpmAffinity(
        bpm: Double,
        target: Double,
        maxBonus: Double,
        sigma: Double,
        mismatchThreshold: Double,
        mismatchPenalty: Double,
    ): Double {
        val distance = kotlin.math.abs(bpm - target)
        var affinity = maxBonus * kotlin.math.exp(-(distance / sigma) * (distance / sigma))
        if (distance > mismatchThreshold) affinity += mismatchPenalty
        return affinity
    }

    /** Maps lexicon energy (0..1) onto the 60–180 BPM scale. */
    private fun genreEnergyToBpm(energy: Double): Double = 60.0 + energy * 120.0

    /**
     * "Least wrong" ordering for mood/tempo queries where nothing scored positive: rank the
     * whole library purely by energy affinity to the target so the result is at least
     * energy-appropriate, instead of surfacing whatever the user plays most.
     */
    private fun energyRankedFallback(
        scored: List<Triple<IndexedSong, Double, Int>>,
        parsed: ParsedQuery,
        bpmBySongId: Map<String, Float>,
        vibeBySongId: Map<String, SongVibe>,
        maxLength: Int,
    ): List<Triple<IndexedSong, Double, Int>> {
        val target = (parsed.effectiveTargetBpm ?: 110f).toDouble()
        return scored
            .map { (indexed, _, playCount) ->
                val affinity = energyScore(indexed, parsed, bpmBySongId, vibeBySongId)
                val proximity = bpmBySongId[indexed.song.id]?.let { -kotlin.math.abs(it - target) }
                    ?: LocalMetadataHeuristics.genreEnergy(indexed.normalizedGenre)
                        ?.let { -kotlin.math.abs(genreEnergyToBpm(it) - target) }
                    ?: -DEFAULT_FALLBACK_DISTANCE
                Triple(indexed, affinity + proximity / 10.0, playCount)
            }
            .sortedWith(
                compareByDescending<Triple<IndexedSong, Double, Int>> { it.second }
                    .thenByDescending { it.third }
            )
            .take(maxLength)
    }

    /** Neutral distance for songs with no tempo or genre signal in the fallback ranking. */
    private const val DEFAULT_FALLBACK_DISTANCE = 40.0

    /** Chooses a final size within [minLength, maxLength], clamped to what is available. */
    private fun targetSize(available: Int, minLength: Int, maxLength: Int, random: Random): Int {
        if (available <= maxLength) return available
        val low = minOf(minLength, maxLength)
        val high = maxOf(minLength, maxLength)
        val size = if (low == high) low else random.nextInt(low, high + 1)
        return size.coerceAtMost(available)
    }

    /**
     * Converts short (`90s`) and full (`1990`) decade markers to a 10-year range: two-digit
     * values 00–30 map to the 2000s, otherwise the 1900s.
     */
    private fun decadeRangeOf(token: String): IntRange? {
        NlpLexicon.SHORT_DECADE.find(token)?.let { match ->
            val shortDecade = match.groupValues[1].toIntOrNull() ?: return null
            val start = if (shortDecade in 0..30) 2000 + shortDecade else 1900 + shortDecade
            return start..(start + 9)
        }
        NlpLexicon.FULL_DECADE.find(token)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            if (year > 1900) return year..(year + 9)
        }
        return null
    }
}
