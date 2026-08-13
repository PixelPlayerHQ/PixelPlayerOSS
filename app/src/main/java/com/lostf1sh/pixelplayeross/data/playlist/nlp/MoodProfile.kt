package com.lostf1sh.pixelplayeross.data.playlist.nlp

/**
 * How a matched [MoodProfile] influences song scoring. Genre-based profiles reward songs
 * whose genre is in the profile's family; behavioral profiles reward songs by listening
 * signal (favorite flag, play count, recency) instead of genre.
 */
enum class MoodKind {
    /** Reward songs whose genre falls in [MoodProfile.genreFamilies]. */
    GENRE,
    /** Reward songs flagged as favorites. */
    FAVORITES,
    /** Reward never-played songs. */
    DISCOVERY,
    /** Reward the most recently added songs. */
    RECENT,
}

/**
 * Central, structured definition of the offline "mood/intent" lexicon. Each profile carries
 * the vocabulary that triggers it and the genre families / bonus it contributes.
 *
 * Because [NlpText.normalize] folds diacritics and [NlpText.stem] collapses common EN/ES
 * inflections, the raw lists below only need **unaccented, singular base forms** — the
 * derived [triggerStems] handle the rest. This is what lets hand-maintained
 * "melancólico / melancolico / melancólica / …" duplication collapse to a single entry.
 *
 * Matching is two-pronged:
 *  - single-word [synonyms] are stemmed and compared against stemmed query tokens ([triggerStems]);
 *  - multi-word [phrases] are normalized and substring-matched against the normalized query,
 *    so "lift weights", "feel good" or "most played" survive tokenization.
 */
enum class MoodProfile(
    val kind: MoodKind,
    val synonyms: Set<String>,
    val phrases: Set<String> = emptySet(),
    val genreFamilies: Set<String> = emptySet(),
    /** Bonus added when a song matches this profile (genre match, or the behavioral signal). */
    val bonus: Double = 15.0,
    /**
     * Acceptable genre-energy band (0..1, see [LocalMetadataHeuristics.genreEnergy]) for
     * GENRE-kind profiles. Songs whose genre energy falls *outside* the band are actively
     * penalized by the scorer — this is what keeps heavy metal out of a "chillout" request
     * even when the metal songs dominate the user's play counts.
     */
    val minEnergy: Double = 0.0,
    val maxEnergy: Double = 1.0,
    /** Implied tempo target (60–180 BPM scale) used when the query has no explicit BPM. */
    val preferredBpm: Float? = null,
    /**
     * Target acoustic vibe (0..1 each) this mood aims for, when a song has a measured
     * [SongVibe] (audio-derived features supplied by the caller). Null for behavioral
     * profiles (FAVORITES/DISCOVERY/RECENT), which don't have a sonic signature to match.
     * When set, these *replace* the coarse [LocalMetadataHeuristics.genreEnergy] conflict
     * check for any song that has real vibe data — see [PlaylistIntentEngine.vibeAffinity].
     */
    val targetEnergy: Float? = null,
    val targetBrightness: Float? = null,
    val targetDynamics: Float? = null,
    val targetPercussiveness: Float? = null,
) {
    CHILL(
        kind = MoodKind.GENRE,
        synonyms = setOf(
            "chill", "chillout", "relax", "relajar", "relajante", "tranquilo", "calma", "calm",
            "suave", "soft", "study", "estudiar", "sleep", "dormir", "peaceful", "meditation",
            "meditacion", "ambient", "acoustic", "acustico", "lofi", "quiet", "instrumental",
            "piano", "jazz", "paz", "desconectar", "sereno", "mellow", "relajado", "lounge",
            "downtempo", "unwind", "zen",
        ),
        phrases = setOf(
            "lo fi", "lo-fi", "chill out", "wind down", "para dormir", "para estudiar",
            "background music", "calm down",
        ),
        genreFamilies = setOf(
            "classical", "jazz", "lofi", "lo-fi", "acoustic", "ambient", "blues",
            "chillout", "chill", "downtempo", "new age", "trip hop", "triphop",
            "post-rock", "meditation",
        ),
        maxEnergy = 0.65,
        preferredBpm = 80f,
        targetEnergy = 0.2f,
        targetBrightness = 0.3f,
        targetDynamics = 0.5f,
        targetPercussiveness = 0.2f,
    ),
    WORKOUT(
        kind = MoodKind.GENRE,
        synonyms = setOf(
            "workout", "gym", "gimnasio", "run", "correr", "cardio", "energetic", "energico",
            "fast", "hype", "pump", "power", "aggressive", "agresivo", "metal", "electronic",
            "electronica", "dance", "upbeat", "intense", "intenso", "heavy", "entrenar",
            "ejercicio", "entrenamiento", "energia", "motivacion", "adrenalina", "beast",
        ),
        phrases = setOf(
            "lift weights", "lifting weights", "work out", "working out", "hit the gym",
            "levantar pesas", "para entrenar", "para correr", "pump up", "get pumped",
        ),
        genreFamilies = setOf(
            "rock", "metal", "electronic", "edm", "dance", "techno", "house", "pop",
            "hip hop", "rap", "punk", "drum and bass", "dubstep", "hardcore", "deathcore",
            "grindcore", "metalcore", "thrash", "djent", "screamo", "hardstyle", "dnb",
            "trap", "phonk", "industrial", "breakcore",
        ),
        minEnergy = 0.55,
        preferredBpm = 150f,
        targetEnergy = 0.9f,
        targetBrightness = 0.7f,
        targetDynamics = 0.4f,
        targetPercussiveness = 0.8f,
    ),
    SAD(
        kind = MoodKind.GENRE,
        synonyms = setOf(
            "sad", "triste", "melancholy", "melancolia", "melancolico", "crying", "llorar",
            "broken", "roto", "rainy", "rain", "lluvia", "dark", "oscuro", "somber", "blue",
            "depressed", "deprimido", "depre", "slow", "lento", "nostalgia", "nostalgico",
            "bajon", "heartbreak", "lonely", "solitario",
        ),
        phrases = setOf(
            "feel sad", "rainy day", "broken heart", "dia lluvioso", "para llorar",
            "me siento triste",
        ),
        genreFamilies = setOf(
            "acoustic", "folk", "classical", "blues", "soul", "ballad", "slowcore", "sad",
        ),
        maxEnergy = 0.65,
        preferredBpm = 80f,
        targetEnergy = 0.25f,
        targetBrightness = 0.3f,
        targetDynamics = 0.6f,
        targetPercussiveness = 0.25f,
    ),
    HAPPY(
        kind = MoodKind.GENRE,
        synonyms = setOf(
            "happy", "feliz", "joy", "alegria", "alegre", "party", "fiesta", "dance",
            "celebration", "celebracion", "upbeat", "sunny", "bright", "fun", "diversion",
            "summer", "verano", "animado", "contento", "groovy", "vibrante",
        ),
        phrases = setOf(
            "feel good", "good vibes", "feel happy", "buena onda", "buen humor",
            "para bailar", "good mood",
        ),
        genreFamilies = setOf(
            "pop", "dance", "reggae", "funk", "disco", "latin", "salsa", "afrobeat", "house",
        ),
        minEnergy = 0.40,
        preferredBpm = 120f,
        targetEnergy = 0.7f,
        targetBrightness = 0.6f,
        targetDynamics = 0.5f,
        targetPercussiveness = 0.6f,
    ),
    FAVORITES(
        kind = MoodKind.FAVORITES,
        synonyms = setOf(
            "favorite", "favorito", "loved", "liked", "top", "best", "mejor", "preferido",
            "gustar",
        ),
        phrases = setOf(
            "most played", "most listened", "mas escuchada", "me gusta", "my favorites",
            "los mejores", "the best",
        ),
        bonus = 35.0,
    ),
    DISCOVERY(
        kind = MoodKind.DISCOVERY,
        synonyms = setOf(
            "discover", "descubrir", "new", "nuevo", "fresh", "unplayed", "recommendation",
            "recomendacion", "random", "aleatorio", "desconocido", "surprise", "sorpresa",
            "explore", "explorar",
        ),
        phrases = setOf(
            "never heard", "new to me", "something new", "algo nuevo", "no escuchada",
            "surprise me",
        ),
        bonus = 35.0,
    ),
    RECENT(
        kind = MoodKind.RECENT,
        synonyms = setOf(
            "recent", "reciente", "recently", "recientemente", "ultimo", "latest",
            "agregado", "anadido", "nuevo", "fresh", "lanzamiento",
        ),
        phrases = setOf(
            "recently added", "just added", "recien agregada", "recien anadido",
            "last added", "newly added",
        ),
        bonus = 35.0,
    );

    /** Single-word triggers, normalized + stemmed once, for O(1) lookup against query tokens. */
    val triggerStems: Set<String> = synonyms.mapTo(HashSet(synonyms.size)) {
        NlpText.stem(NlpText.normalize(it))
    }

    /** Multi-word triggers, normalized once, for substring matching against the normalized query. */
    val normalizedPhrases: Set<String> = phrases.mapTo(HashSet(phrases.size)) {
        NlpText.normalize(it)
    }

    /** Genre families, normalized once, for the genre containment check in the scorer. */
    val normalizedGenreFamilies: Set<String> = genreFamilies.mapTo(HashSet(genreFamilies.size)) {
        NlpText.normalize(it)
    }

    /** Phrases with spaces removed, so compound spellings ("chillout" vs "chill out") match. */
    val fusedPhrases: Set<String> = normalizedPhrases.mapTo(HashSet(normalizedPhrases.size)) {
        it.replace(" ", "")
    }

    /** True if either a query token stem or the raw normalized query triggers this profile. */
    fun matches(queryTokenStems: Set<String>, normalizedQuery: String): Boolean =
        matchStrength(queryTokenStems, normalizedQuery) > 0.0

    /**
     * How strongly the query triggered this profile, in `[0, 1]`: 0 means no cue matched at
     * all. A multi-word phrase hit ("lift weights") is unambiguous and alone saturates to 1.0;
     * single-word synonym hits are weaker signals and accumulate at 0.5 each toward the same
     * cap, so e.g. two loosely-related words ("energetic" + "fast") read as confidently as one
     * clear phrase. Used to weight and cap this profile's contribution in the scorer instead of
     * always granting its full [bonus], so a query naming several moods can't triple-count.
     */
    fun matchStrength(queryTokenStems: Set<String>, normalizedQuery: String): Double {
        var strength = 0.0

        val singleWordHits = triggerStems.count { it in queryTokenStems }
        strength += singleWordHits * SINGLE_WORD_STRENGTH

        val fusedQuery = normalizedQuery.replace(" ", "")
        val phraseHit = normalizedPhrases.any { normalizedQuery.contains(it) } ||
            fusedPhrases.any { fusedQuery.contains(it) }
        if (phraseHit) strength += PHRASE_STRENGTH

        return strength.coerceIn(0.0, 1.0)
    }

    /** True if [normalizedGenre] belongs to this profile's genre family (bidirectional containment). */
    fun matchesGenre(normalizedGenre: String): Boolean {
        if (normalizedGenre.isEmpty()) return false
        return normalizedGenreFamilies.any { family ->
            normalizedGenre.contains(family) || family.contains(normalizedGenre)
        }
    }

    companion object {
        /** Strength contributed by each single-word synonym hit toward [matchStrength]'s cap. */
        private const val SINGLE_WORD_STRENGTH = 0.5

        /** Strength contributed by a multi-word phrase hit toward [matchStrength]'s cap. */
        private const val PHRASE_STRENGTH = 1.0
    }
}
