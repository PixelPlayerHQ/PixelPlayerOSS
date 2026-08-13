package com.lostf1sh.pixelplayeross.data.playlist.nlp

import java.text.Normalizer

/**
 * Pure, allocation-light text utilities shared by the offline NLP engine.
 *
 * Everything here is deterministic and side-effect free so it can be unit tested in
 * isolation and reused for both query parsing and library indexing. The two invariants
 * that make matching work are:
 *
 *  1. Both the query terms and the library terms are pushed through the exact same
 *     [normalize] + [stem] pipeline, so accents and simple inflections cancel out on
 *     both sides ("melancólico" == "melancolico", "relajantes" ~ "relajante").
 *  2. The stemmer is intentionally conservative — it only strips endings that are
 *     overwhelmingly inflectional in English and Spanish, to avoid collapsing unrelated
 *     words (a classic over-stemming failure).
 */
object NlpText {

    private val COMBINING_MARKS = "\\p{Mn}+".toRegex()

    /** Splits on any run of characters that are not letters, digits, or '#' (for hashtags/decades). */
    private val TOKEN_SPLIT = "[^\\p{L}\\p{Nd}#]+".toRegex()

    /**
     * Lowercases and folds diacritics so accented and unaccented spellings compare equal.
     * Uses NFD decomposition then drops Unicode combining marks: `Ñ` -> `n`, `á` -> `a`.
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
    }

    /**
     * Normalizes then splits into tokens, dropping single-character tokens (they carry no
     * signal and inflate document frequency). Order is preserved; duplicates are kept so
     * callers can decide whether to dedupe.
     */
    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return TOKEN_SPLIT.split(normalize(text))
            .filter { it.length > 1 }
    }

    /** [tokenize] followed by [stem] on each token. */
    fun stemTokens(text: String): List<String> = tokenize(text).map { stem(it) }

    /**
     * Conservative light stemmer for English and Spanish inflection. Applied symmetrically
     * to query and library terms — it does not need to produce linguistically correct
     * lemmas, only *stable, convergent* ones so that different surface forms of the same
     * word reduce to an identical stem.
     *
     * The ordering is deliberate. Spanish pluralizes vowel-ending words with `-s`
     * (relajante -> relajantes) and consonant-ending words with `-es`
     * (canción -> canciones). Dropping a single trailing `s`, then a trailing `e`, then a
     * gender vowel makes both the singular and the plural converge:
     *   relajantes -> relajante -> relajant   ==   relajante -> relajant
     *   canciones  -> cancione  -> cancion    ==   cancion   -> cancion
     *   nuevos/nueva/nuevo/nuevas             ->   nuev
     *
     * No drop ever reduces a stem below [MIN_STEM_LENGTH], which protects short words
     * (love, data, song, rock) from being mangled. Tokens containing digits (decades,
     * years, "mp3") are returned untouched.
     */
    fun stem(token: String): String {
        if (token.length <= MIN_STEM_LENGTH) return token
        if (token.any { it.isDigit() }) return token

        var t = token

        // English gerund / past tense are terminal — a gerund won't also carry a plural.
        if (t.endsWith("ing") && t.length - 3 >= MIN_STEM_LENGTH) return t.dropLast(3)
        if (t.endsWith("ed") && t.length - 2 >= MIN_STEM_LENGTH) return t.dropLast(2)

        // Plural: strip a single trailing `s`. This turns `-es` into `-e`, which the next
        // rule then removes, converging consonant-plurals with their singular.
        if (t.endsWith("s") && t.length - 1 >= MIN_STEM_LENGTH) t = t.dropLast(1)

        // Trailing `e` (bridges vowel-plural singulars and consonant-plural stems).
        if (t.endsWith("e") && t.length - 1 >= MIN_STEM_LENGTH) t = t.dropLast(1)

        // Spanish gender vowel (relajanta/relajanto style; also electronica -> electronic).
        if ((t.endsWith("o") || t.endsWith("a")) && t.length - 1 >= MIN_STEM_LENGTH) {
            t = t.dropLast(1)
        }

        return t
    }

    /** Minimum characters a stem may keep. Below this, inflectional stripping is skipped. */
    const val MIN_STEM_LENGTH = 4
}
