package com.lostf1sh.pixelplayeross.data.playlist.nlp

import java.util.Locale

/**
 * Offline, rule-based metadata / tagging / mood-analysis helpers backing the NLP engine.
 *
 * These handlers read whatever `<title>`/`<artist>`/`<album>`/`<genre>`/`<duration>`/`<year>`
 * fields the caller happened to include in the prompt and derive a real answer from them,
 * degrading to sensible defaults when a field is missing. [PlaylistIntentEngine] only uses
 * [genreEnergy]; the rest are self-contained, dependency-free helpers kept with the engine.
 */
object LocalMetadataHeuristics {

    // ---- Metadata completion -------------------------------------------------------------

    /** Cleans the provided fields and infers a genre. Returns the METADATA JSON object string. */
    fun completeMetadata(prompt: String): String {
        val title = smartCase(cleanWhitespace(extractField(prompt, "title")))
        val artist = smartCase(cleanWhitespace(extractField(prompt, "artist")))
        val album = smartCase(cleanWhitespace(extractField(prompt, "album")))
        val existingGenre = cleanWhitespace(extractField(prompt, "genre"))

        val genre = existingGenre
            .takeUnless { it.isBlank() || it.equals("music", true) || it.equals("unknown", true) }
            ?: inferGenre(title, artist, album)

        return buildString {
            append("{")
            append("\"title\":\"").append(jsonEscape(title)).append("\",")
            append("\"artist\":\"").append(jsonEscape(artist)).append("\",")
            append("\"album\":\"").append(jsonEscape(album)).append("\",")
            append("\"genre\":\"").append(jsonEscape(genre)).append("\"")
            append("}")
        }
    }

    /** Best-effort genre guess from strong textual signals; falls back to "Unknown". */
    private fun inferGenre(vararg fields: String): String {
        val haystack = NlpText.normalize(fields.joinToString(" "))
        if (haystack.isBlank()) return "Unknown"
        for ((signal, genre) in GENRE_SIGNALS) {
            if (haystack.contains(signal)) return genre
        }
        return "Unknown"
    }

    // ---- Tagging -------------------------------------------------------------------------

    /** Derives 6–10 hyphenated, lowercase descriptor tags from the prompt fields. */
    fun generateTags(prompt: String): String {
        val genre = NlpText.normalize(extractField(prompt, "genre"))
        val durationMs = parseDurationMs(extractField(prompt, "duration"))
        val year = extractField(prompt, "year").toIntOrNull()

        val tags = LinkedHashSet<String>()
        tags.addAll(genreTextureTags(genre))

        when {
            durationMs != null && durationMs < 150_000 -> tags.add("short-form")
            durationMs != null && durationMs > 300_000 -> tags.add("extended")
            else -> tags.add("mid-tempo")
        }

        if (year != null) {
            when {
                year in 1..1979 -> tags.add("vintage")
                year in 1980..1999 -> tags.add("throwback")
                year >= 2015 -> tags.add("contemporary")
            }
        }

        // Pad to a minimum of six with neutral descriptors, cap at ten.
        val filler = listOf("melodic", "clean-mix", "vocal-forward", "layered", "textured")
        var i = 0
        while (tags.size < 6 && i < filler.size) { tags.add(filler[i]); i++ }
        return tags.take(10).joinToString(", ")
    }

    private fun genreTextureTags(normalizedGenre: String): List<String> {
        if (normalizedGenre.isBlank()) return listOf("melodic", "mid-tempo", "clean-vocals")
        for ((signal, tags) in GENRE_TEXTURES) {
            if (normalizedGenre.contains(signal)) return tags
        }
        return listOf("melodic", "mid-tempo", "clean-vocals")
    }

    // ---- Mood analysis -------------------------------------------------------------------

    /**
     * Derives an approximate mood vector from genre + duration + (optional) measured BPM.
     */
    fun analyzeMood(prompt: String): String {
        val genre = NlpText.normalize(extractField(prompt, "genre"))
        val durationMs = parseDurationMs(extractField(prompt, "duration"))
        val bpm = extractField(prompt, "bpm").toFloatOrNull()?.takeIf { it > 0f }

        var (energy, valence, danceability, acousticness) = moodVectorFor(genre)

        // Measured tempo beats a genre guess for energy/danceability: blend it in at half
        // weight so genre still carries the parts BPM can't see (distortion, mood).
        if (bpm != null) {
            val bpmEnergy = ((bpm - 60f) / 120f).toDouble().coerceIn(0.0, 1.0)
            energy = (energy + bpmEnergy) / 2.0
            danceability = (danceability + bpmEnergy) / 2.0
        }

        if (durationMs != null) {
            if (durationMs > 300_000) { energy -= 0.1; acousticness += 0.05 }
            if (durationMs < 150_000) { energy += 0.05 }
        }
        energy = energy.coerceIn(0.0, 1.0)
        valence = valence.coerceIn(0.0, 1.0)
        danceability = danceability.coerceIn(0.0, 1.0)
        acousticness = acousticness.coerceIn(0.0, 1.0)

        val mood = primaryMood(energy, valence, acousticness)
        return "$mood | Energy:${fmt(energy)} | Valence:${fmt(valence)} | " +
            "Danceability:${fmt(danceability)} | Acousticness:${fmt(acousticness)}"
    }

    private data class MoodVector(
        val energy: Double,
        val valence: Double,
        val danceability: Double,
        val acousticness: Double,
    )

    private fun moodVectorFor(normalizedGenre: String): MoodVector {
        if (normalizedGenre.isNotBlank()) {
            for ((signal, vector) in GENRE_MOOD_VECTORS) {
                if (normalizedGenre.contains(signal)) return vector
            }
        }
        return MoodVector(0.5, 0.5, 0.5, 0.4)
    }

    /**
     * Approximate energy (0..1) implied by a normalized genre string, or null when the genre
     * is unrecognized. Delegates to the shared [GenreTaxonomy] so genre coverage (subgenres,
     * spellings, languages) stays in one place; [PlaylistIntentEngine] uses it as the fallback
     * energy signal for songs without measured BPM.
     */
    fun genreEnergy(normalizedGenre: String): Double? = GenreTaxonomy.energyOf(normalizedGenre)

    private fun primaryMood(energy: Double, valence: Double, acousticness: Double): String = when {
        energy >= 0.66 && valence >= 0.6 -> "Radiant"
        energy >= 0.66 && valence < 0.4 -> "Aggressive"
        energy >= 0.66 -> "Intense"
        energy <= 0.4 && valence <= 0.4 -> "Somber"
        energy <= 0.4 && acousticness >= 0.7 -> "Calm"
        valence >= 0.6 -> "Joyful"
        valence <= 0.4 -> "Melancholic"
        else -> "Calm"
    }

    // ---- Shared helpers ------------------------------------------------------------------

    private fun extractField(text: String, tag: String): String {
        val regex = "<$tag>(.*?)</$tag>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun cleanWhitespace(s: String): String = s.trim().replace("\\s+".toRegex(), " ")

    /** Title-cases a value only when it is entirely upper- or lower-case (likely un-styled). */
    private fun smartCase(s: String): String {
        if (s.isBlank()) return s
        val isUniform = s == s.uppercase(Locale.ROOT) || s == s.lowercase(Locale.ROOT)
        if (!isUniform) return s
        return s.split(" ").mapIndexed { index, word ->
            when {
                word.isEmpty() -> word
                index > 0 && word.lowercase(Locale.ROOT) in LOWERCASE_PARTICLES -> word.lowercase(Locale.ROOT)
                else -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }.let { head ->
                    head.take(1) + head.drop(1).lowercase(Locale.ROOT)
                }
            }
        }.joinToString(" ")
    }

    /** Parses a duration string as milliseconds; values under 10000 are treated as seconds. */
    private fun parseDurationMs(raw: String): Long? {
        val value = raw.trim().toLongOrNull() ?: return null
        if (value <= 0) return null
        return if (value < 10_000) value * 1000 else value
    }

    private fun fmt(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private val LOWERCASE_PARTICLES = setOf(
        "of", "the", "and", "a", "an", "to", "in", "on", "for", "de", "la", "el", "y", "feat",
        "feat.", "ft", "ft.", "vs", "vs.",
    )

    // Ordered strongest-signal-first; first containment hit wins.
    private val GENRE_SIGNALS: List<Pair<String, String>> = listOf(
        "reggaeton" to "Reggaeton", "perreo" to "Reggaeton",
        "symphony" to "Classical", "concerto" to "Classical", "sonata" to "Classical",
        "orchestra" to "Classical",
        "unplugged" to "Acoustic", "acoustic" to "Acoustic",
        "freestyle" to "Hip Hop", "hip hop" to "Hip Hop",
        "remix" to "Electronic", "techno" to "Electronic", "house" to "Electronic",
        "edm" to "Electronic",
        "metal" to "Metal", "punk" to "Punk", "jazz" to "Jazz", "blues" to "Blues",
    )

    private val GENRE_TEXTURES: List<Pair<String, List<String>>> = listOf(
        "electronic" to listOf("electronic", "synth-driven", "driving-beat", "danceable"),
        "edm" to listOf("electronic", "synth-driven", "driving-beat", "danceable"),
        "techno" to listOf("electronic", "hypnotic", "driving-beat", "danceable"),
        "house" to listOf("electronic", "groovy", "four-on-the-floor", "danceable"),
        "metal" to listOf("distorted", "high-energy", "aggressive", "guitar-driven"),
        "punk" to listOf("raw", "fast-paced", "energetic", "guitar-driven"),
        "rock" to listOf("guitar-driven", "high-energy", "anthemic", "powerful"),
        "pop" to listOf("catchy", "polished", "vocal-forward", "upbeat"),
        "acoustic" to listOf("acoustic", "organic", "intimate", "warm"),
        "folk" to listOf("acoustic", "storytelling", "intimate", "warm"),
        "jazz" to listOf("smooth", "soulful", "improvisational", "mellow"),
        "blues" to listOf("soulful", "expressive", "raw", "mellow"),
        "soul" to listOf("soulful", "warm", "groovy", "vocal-forward"),
        "classical" to listOf("orchestral", "cinematic", "dynamic", "spacious"),
        "ambient" to listOf("atmospheric", "spacious", "textured", "ethereal"),
        "hip hop" to listOf("rhythmic", "beat-driven", "lyrical", "groovy"),
        "rap" to listOf("rhythmic", "beat-driven", "lyrical", "punchy"),
        "reggae" to listOf("laid-back", "groovy", "warm", "offbeat"),
        "latin" to listOf("rhythmic", "danceable", "vibrant", "percussive"),
    )

    // Ordered most-specific-first: `genreEnergy`/`moodVectorFor` take the first containment hit,
    // so a subgenre must precede any broader family it contains ("lofi hip hop" must resolve to
    // lofi, not hip hop; "post-rock" to its own low energy, not rock). This list is deliberately
    // broad — an *unrecognized* genre returns null energy, which the playlist scorer reads as
    // "no evidence to exclude on", so thin coverage is what let aggressive subgenres (hardcore,
    // deathcore, djent, …) slip into a chillout request.
    private val GENRE_MOOD_VECTORS: List<Pair<String, MoodVector>> = listOf(
        // --- Chill / low-energy families (must precede hip hop / rock / electronic below) ---
        "lo-fi" to MoodVector(0.25, 0.55, 0.45, 0.70),
        "lofi" to MoodVector(0.25, 0.55, 0.45, 0.70),
        "chill" to MoodVector(0.28, 0.55, 0.40, 0.60),
        "downtempo" to MoodVector(0.30, 0.50, 0.45, 0.55),
        "trip hop" to MoodVector(0.40, 0.45, 0.55, 0.40),
        "triphop" to MoodVector(0.40, 0.45, 0.55, 0.40),
        "new age" to MoodVector(0.18, 0.55, 0.15, 0.88),
        "meditation" to MoodVector(0.12, 0.50, 0.10, 0.92),
        "post-rock" to MoodVector(0.48, 0.45, 0.30, 0.45),
        "shoegaze" to MoodVector(0.50, 0.45, 0.35, 0.35),
        // --- Aggressive / high-energy families not caught by the "metal" signal ---
        "deathcore" to MoodVector(0.97, 0.30, 0.50, 0.02),
        "grindcore" to MoodVector(0.97, 0.30, 0.50, 0.02),
        "metalcore" to MoodVector(0.95, 0.35, 0.52, 0.03),
        "breakcore" to MoodVector(0.96, 0.45, 0.70, 0.03),
        "hardcore" to MoodVector(0.95, 0.40, 0.55, 0.03),
        "hardstyle" to MoodVector(0.95, 0.55, 0.85, 0.03),
        "thrash" to MoodVector(0.95, 0.40, 0.50, 0.03),
        "djent" to MoodVector(0.93, 0.40, 0.55, 0.03),
        "screamo" to MoodVector(0.92, 0.35, 0.50, 0.05),
        "industrial" to MoodVector(0.85, 0.40, 0.55, 0.10),
        "dubstep" to MoodVector(0.90, 0.50, 0.80, 0.05),
        "drum and bass" to MoodVector(0.90, 0.55, 0.85, 0.05),
        "drum & bass" to MoodVector(0.90, 0.55, 0.85, 0.05),
        "dnb" to MoodVector(0.90, 0.55, 0.85, 0.05),
        "phonk" to MoodVector(0.80, 0.45, 0.80, 0.10),
        "trap" to MoodVector(0.78, 0.50, 0.85, 0.08),
        "electronic" to MoodVector(0.85, 0.70, 0.90, 0.05),
        "edm" to MoodVector(0.90, 0.75, 0.92, 0.05),
        "techno" to MoodVector(0.85, 0.55, 0.88, 0.05),
        "house" to MoodVector(0.80, 0.70, 0.90, 0.05),
        "metal" to MoodVector(0.92, 0.40, 0.45, 0.05),
        "punk" to MoodVector(0.90, 0.45, 0.50, 0.05),
        "rock" to MoodVector(0.80, 0.50, 0.50, 0.10),
        "pop" to MoodVector(0.70, 0.75, 0.75, 0.20),
        "acoustic" to MoodVector(0.35, 0.50, 0.30, 0.90),
        "folk" to MoodVector(0.40, 0.50, 0.35, 0.85),
        "jazz" to MoodVector(0.45, 0.55, 0.55, 0.60),
        "blues" to MoodVector(0.45, 0.40, 0.45, 0.60),
        "soul" to MoodVector(0.55, 0.60, 0.65, 0.35),
        "classical" to MoodVector(0.35, 0.50, 0.20, 0.85),
        "ambient" to MoodVector(0.25, 0.50, 0.15, 0.80),
        "hip hop" to MoodVector(0.70, 0.55, 0.80, 0.10),
        "rap" to MoodVector(0.72, 0.50, 0.80, 0.10),
        "reggae" to MoodVector(0.60, 0.70, 0.75, 0.25),
        "latin" to MoodVector(0.75, 0.80, 0.85, 0.20),
    )
}
