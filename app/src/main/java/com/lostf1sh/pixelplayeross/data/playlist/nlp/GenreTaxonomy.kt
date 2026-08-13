package com.lostf1sh.pixelplayeross.data.playlist.nlp

/**
 * Coarse genre "super-families". The point is not musicological precision but *compatibility*:
 * two genres in the same family are safe to mix, two in different families generally are not.
 * A "rock" request should never surface a phonk track, and this is the axis that expresses why.
 */
enum class GenreFamily {
    GUITAR,        // rock, metal, punk, alternative, grunge, emo, post-rock…
    URBAN,         // hip hop, rap, trap, phonk, drill, grime…
    RNB_SOUL,      // r&b, soul, funk, gospel, motown…
    ELECTRONIC,    // edm, house, techno, dubstep, dnb, hardstyle…
    POP,           // pop, k-pop, synthpop…
    LATIN,         // reggaeton, cumbia, salsa, bachata, banda…
    JAZZ,          // jazz, blues, swing, bossa nova…
    FOLK_COUNTRY,  // folk, country, acoustic, singer-songwriter…
    CLASSICAL,     // classical, orchestral, piano, soundtrack…
    CHILL_AMBIENT, // ambient, lo-fi, downtempo, new age…
    REGGAE,        // reggae, dancehall, dub, ska…
    WORLD,         // afrobeat, flamenco, amapiano… (heterogeneous catch-all)
}

/**
 * The single source of truth for interpreting a free-form genre tag. Music libraries tag genres
 * inconsistently and in several languages ("Rock & Roll", "Alternative", "Electrónica",
 * "Reggaetón", "Lo-Fi Hip Hop"), so both the mood-energy scorer and the explicit-genre query
 * path resolve through here instead of matching raw strings.
 *
 * Two questions are answered:
 *  - **Song side** ([resolve]/[energyOf]/[familyOf]): given a song's normalized genre string,
 *    which canonical family is it and how energetic is it? Matching is longest-specific-first
 *    substring containment, so "alternative rock" → GUITAR via "rock", "lofi hip hop" → CHILL
 *    via "lofi" (which is listed before "hip hop").
 *  - **Query side** ([queryFamilies]): which genre families did the user actually *name* in a
 *    prompt? Only unambiguous genre nouns count here — broad mood adjectives ("chill", "party")
 *    are deliberately excluded so they stay the job of [MoodProfile]'s energy bands, not a
 *    single-family filter (a "chill" request must still allow jazz, acoustic and ambient).
 *
 * Pure and Android-free, like the rest of this package.
 */
object GenreTaxonomy {

    data class Entry(
        val family: GenreFamily,
        val energy: Double,
        /** Normalized substrings that identify this genre inside a song's genre tag. */
        val songAliases: List<String>,
        /** Genre nouns that, when named in a query, mean "I want this family". Empty = never a
         *  query trigger (used for mood-ish tags like "chill" that [MoodProfile] already owns). */
        val queryAliases: List<String> = emptyList(),
    )

    /**
     * Ordered most-specific-first: [resolve] returns the first entry any of whose [songAliases]
     * is contained in the genre string, so a subgenre must precede every broader family whose
     * text it contains. The ordering constraints that matter are called out inline.
     */
    private val ENTRIES: List<Entry> = listOf(
        // --- Specific forms that must beat a broader family they textually contain -----------
        Entry(GenreFamily.GUITAR, 0.45, listOf("post-rock", "post rock"), listOf("post-rock", "post rock")),
        Entry(GenreFamily.LATIN, 0.78, listOf("trap latino", "trap latin", "latin trap"), listOf("trap latino", "latin trap")), // before "trap"/"rap"
        Entry(GenreFamily.LATIN, 0.80, listOf("reggaeton", "reggaetón", "perreo"), listOf("reggaeton", "perreo")), // before "reggae"
        Entry(GenreFamily.REGGAE, 0.75, listOf("dancehall"), listOf("dancehall")),
        Entry(GenreFamily.CHILL_AMBIENT, 0.25, listOf("lo-fi", "lofi", "lo fi"), listOf("lofi", "lo-fi", "lo fi")), // before "hip hop"
        Entry(GenreFamily.CHILL_AMBIENT, 0.40, listOf("trip hop", "triphop", "trip-hop"), listOf("trip hop", "triphop")),

        // --- Urban -------------------------------------------------------------------------
        Entry(GenreFamily.URBAN, 0.70, listOf("hip hop", "hip-hop", "hiphop"), listOf("hip hop", "hip-hop", "hiphop")),
        Entry(GenreFamily.URBAN, 0.78, listOf("trap"), listOf("trap")), // before "rap"
        Entry(GenreFamily.URBAN, 0.80, listOf("phonk"), listOf("phonk")),
        Entry(GenreFamily.URBAN, 0.80, listOf("drill"), listOf("drill")),
        Entry(GenreFamily.URBAN, 0.80, listOf("grime"), listOf("grime")),
        Entry(GenreFamily.URBAN, 0.72, listOf("rap"), listOf("rap")),

        // --- Guitar (aggressive first so "death metal" etc. keep their energy) --------------
        Entry(GenreFamily.GUITAR, 0.96, listOf("metalcore", "deathcore", "grindcore", "thrash", "djent", "screamo", "mathcore"),
            listOf("metalcore", "deathcore", "grindcore", "thrash", "djent", "screamo")),
        Entry(GenreFamily.GUITAR, 0.92, listOf("metal"), listOf("metal")), // catches nu/black/death/heavy/power metal
        Entry(GenreFamily.GUITAR, 0.95, listOf("hardcore"), listOf("hardcore")),
        Entry(GenreFamily.GUITAR, 0.90, listOf("punk"), listOf("punk")), // catches pop punk / ska punk
        Entry(GenreFamily.GUITAR, 0.75, listOf("grunge"), listOf("grunge")),
        Entry(GenreFamily.GUITAR, 0.70, listOf("emo"), listOf("emo")),
        Entry(GenreFamily.GUITAR, 0.50, listOf("shoegaze"), listOf("shoegaze")),
        Entry(GenreFamily.GUITAR, 0.68, listOf("rock and roll", "rock n roll", "rock & roll", "rock'n'roll", "rockabilly"),
            listOf("rock and roll", "rock n roll", "rock & roll", "rockabilly")), // before "rock"
        Entry(GenreFamily.GUITAR, 0.80, listOf("rock"), listOf("rock")), // catches hard/classic/indie/alt rock, rock en español
        Entry(GenreFamily.GUITAR, 0.60, listOf("alternative", "alt rock", "indie"), listOf("alternative", "indie")),

        // --- Electronic (before "dub"/"reggae": "dubstep" contains "dub") -------------------
        Entry(GenreFamily.ELECTRONIC, 0.90, listOf("dubstep"), listOf("dubstep")),
        Entry(GenreFamily.ELECTRONIC, 0.90, listOf("drum and bass", "drum & bass", "drum n bass", "dnb"),
            listOf("drum and bass", "drum n bass", "dnb")),
        Entry(GenreFamily.ELECTRONIC, 0.95, listOf("hardstyle", "gabber"), listOf("hardstyle")),
        Entry(GenreFamily.ELECTRONIC, 0.96, listOf("breakcore"), listOf("breakcore")),
        Entry(GenreFamily.ELECTRONIC, 0.85, listOf("techno"), listOf("techno")),
        Entry(GenreFamily.ELECTRONIC, 0.80, listOf("house"), listOf("house")),
        Entry(GenreFamily.ELECTRONIC, 0.80, listOf("trance"), listOf("trance")),
        Entry(GenreFamily.ELECTRONIC, 0.60, listOf("synthwave", "darksynth", "retrowave"), listOf("synthwave")),
        Entry(GenreFamily.ELECTRONIC, 0.90, listOf("edm"), listOf("edm")),
        Entry(GenreFamily.ELECTRONIC, 0.85, listOf("industrial"), listOf("industrial")),
        Entry(GenreFamily.ELECTRONIC, 0.85, listOf("electronic", "electronica", "electrónica", "electro"),
            listOf("electronic", "electronica", "electro")),

        // --- Reggae ------------------------------------------------------------------------
        Entry(GenreFamily.REGGAE, 0.50, listOf("dub"), emptyList()), // "dub" as a word is ambiguous; song-side only
        Entry(GenreFamily.REGGAE, 0.60, listOf("reggae"), listOf("reggae")),
        Entry(GenreFamily.REGGAE, 0.75, listOf("ska"), listOf("ska")),

        // --- Pop (specific before generic "pop") -------------------------------------------
        Entry(GenreFamily.POP, 0.65, listOf("synthpop", "synth pop"), listOf("synthpop")),
        Entry(GenreFamily.POP, 0.75, listOf("k-pop", "kpop", "j-pop", "jpop", "c-pop", "cpop"), listOf("kpop", "k-pop", "jpop", "j-pop")),
        Entry(GenreFamily.POP, 0.70, listOf("pop"), listOf("pop")),

        // --- R&B / Soul (before "blues": "rhythm and blues" contains "blues") ---------------
        Entry(GenreFamily.RNB_SOUL, 0.55, listOf("r&b", "rnb", "r and b", "rhythm and blues"), listOf("rnb", "r&b")),
        Entry(GenreFamily.RNB_SOUL, 0.50, listOf("neo soul", "neo-soul"), listOf("neo soul")),
        Entry(GenreFamily.RNB_SOUL, 0.55, listOf("soul", "motown"), listOf("soul", "motown")),
        Entry(GenreFamily.RNB_SOUL, 0.70, listOf("funk"), listOf("funk")),
        Entry(GenreFamily.RNB_SOUL, 0.60, listOf("gospel"), listOf("gospel")),

        // --- Jazz --------------------------------------------------------------------------
        Entry(GenreFamily.JAZZ, 0.45, listOf("blues"), listOf("blues")),
        Entry(GenreFamily.JAZZ, 0.35, listOf("bossa nova", "bossa"), listOf("bossa nova", "bossa")),
        Entry(GenreFamily.JAZZ, 0.55, listOf("swing", "bebop", "big band", "dixieland"), listOf("swing", "bebop")),
        Entry(GenreFamily.JAZZ, 0.45, listOf("jazz"), listOf("jazz")),

        // --- Latin (banda sonora = ES "soundtrack", must beat "banda") ----------------------
        Entry(GenreFamily.CLASSICAL, 0.45, listOf("banda sonora", "banda de sonido"), emptyList()),
        Entry(GenreFamily.LATIN, 0.75, listOf("salsa"), listOf("salsa")),
        Entry(GenreFamily.LATIN, 0.60, listOf("bachata"), listOf("bachata")),
        Entry(GenreFamily.LATIN, 0.78, listOf("merengue"), listOf("merengue")),
        Entry(GenreFamily.LATIN, 0.70, listOf("cumbia"), listOf("cumbia")),
        Entry(GenreFamily.LATIN, 0.82, listOf("dembow"), listOf("dembow")),
        Entry(GenreFamily.LATIN, 0.65, listOf("banda", "norteño", "norteno", "corrido", "corridos", "ranchera", "mariachi", "vallenato", "regional mexican"),
            listOf("banda", "corridos", "corrido", "mariachi", "ranchera", "norteno", "vallenato")),
        Entry(GenreFamily.LATIN, 0.72, listOf("latin", "latino", "latina", "latín"), listOf("latin", "latino")),

        // --- World -------------------------------------------------------------------------
        Entry(GenreFamily.WORLD, 0.60, listOf("flamenco"), listOf("flamenco")),
        Entry(GenreFamily.WORLD, 0.70, listOf("afrobeat", "afrobeats", "afro"), listOf("afrobeat", "afrobeats")),
        Entry(GenreFamily.WORLD, 0.70, listOf("amapiano"), listOf("amapiano")),

        // --- Classical ---------------------------------------------------------------------
        Entry(GenreFamily.CLASSICAL, 0.35, listOf("classical", "clasica", "clásica", "clasico", "orchestr", "orquesta", "symphony", "sinfonia", "concerto", "concierto", "sonata", "opera", "ópera", "baroque", "barroco"),
            listOf("classical", "clasica", "orchestral", "opera")),
        Entry(GenreFamily.CLASSICAL, 0.30, listOf("piano"), listOf("piano")),
        Entry(GenreFamily.CLASSICAL, 0.45, listOf("soundtrack", "score", "cinematic", "banda sonora"), listOf("soundtrack", "cinematic")),

        // --- Chill / ambient (mostly mood-owned: few query aliases) ------------------------
        Entry(GenreFamily.CHILL_AMBIENT, 0.18, listOf("new age"), listOf("new age")),
        Entry(GenreFamily.CHILL_AMBIENT, 0.25, listOf("ambient"), listOf("ambient")),
        Entry(GenreFamily.CHILL_AMBIENT, 0.30, listOf("downtempo"), listOf("downtempo")),
        Entry(GenreFamily.CHILL_AMBIENT, 0.30, listOf("vaporwave"), listOf("vaporwave")),
        Entry(GenreFamily.CHILL_AMBIENT, 0.28, listOf("chillout", "chill out", "chillhop", "chillstep", "chillwave", "chill"), emptyList()),
        Entry(GenreFamily.CHILL_AMBIENT, 0.12, listOf("meditation", "meditacion", "meditación"), emptyList()),

        // --- Folk / country / acoustic -----------------------------------------------------
        Entry(GenreFamily.FOLK_COUNTRY, 0.40, listOf("folk"), listOf("folk")),
        Entry(GenreFamily.FOLK_COUNTRY, 0.55, listOf("country"), listOf("country")),
        Entry(GenreFamily.FOLK_COUNTRY, 0.55, listOf("bluegrass"), listOf("bluegrass")),
        Entry(GenreFamily.FOLK_COUNTRY, 0.50, listOf("americana"), listOf("americana")),
        Entry(GenreFamily.FOLK_COUNTRY, 0.40, listOf("singer-songwriter", "singer songwriter", "cantautor"), listOf("singer-songwriter", "cantautor")),
        Entry(GenreFamily.FOLK_COUNTRY, 0.35, listOf("acoustic", "acustico", "acústico", "unplugged"), listOf("acoustic", "acustico", "unplugged")),
    )

    /** The first entry whose any [Entry.songAliases] is a substring of [normalizedGenre], else null. */
    fun resolve(normalizedGenre: String): Entry? {
        if (normalizedGenre.isBlank()) return null
        for (entry in ENTRIES) {
            if (entry.songAliases.any { normalizedGenre.contains(it) }) return entry
        }
        return null
    }

    /** Coarse energy (0..1) implied by a genre tag, or null when the tag is unrecognized. */
    fun energyOf(normalizedGenre: String): Double? = resolve(normalizedGenre)?.energy

    /** The super-family of a genre tag, or null when unrecognized (i.e. "no evidence"). */
    fun familyOf(normalizedGenre: String): GenreFamily? = resolve(normalizedGenre)?.family

    /**
     * Which genre families the user explicitly named in a prompt. Single-word aliases are matched
     * (stemmed) against the query's content-word stems; multi-word aliases are substring-matched
     * against the normalized query. Returns empty when the prompt names no concrete genre — the
     * signal that the request is mood/free-text only and genre-family filtering must not kick in.
     */
    fun queryFamilies(queryTokenStems: Set<String>, normalizedQuery: String): Set<GenreFamily> {
        if (queryTokenStems.isEmpty() && normalizedQuery.isBlank()) return emptySet()
        val families = HashSet<GenreFamily>()
        for (entry in ENTRIES) {
            val hit = entry.queryAliases.any { alias ->
                if (alias.contains(' ') || alias.contains('&') || alias.contains('-')) {
                    normalizedQuery.contains(alias)
                } else {
                    NlpText.stem(alias) in queryTokenStems
                }
            }
            if (hit) families.add(entry.family)
        }
        return families
    }
}
