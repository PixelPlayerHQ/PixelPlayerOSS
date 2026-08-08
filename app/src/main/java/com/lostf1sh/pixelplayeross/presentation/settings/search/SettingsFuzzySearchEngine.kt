package com.lostf1sh.pixelplayeross.presentation.settings.search

import android.content.Context
import com.lostf1sh.pixelplayeross.R
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Scores every [SettingSpec] in the registry against a query and splits the survivors into a
 * "top matches" and a "related" section.
 *
 * Matching is diacritic-insensitive and falls back to per-token Levenshtein similarity, so
 * typos and partial words still find a setting. Exact and prefix matches short-circuit to a
 * fixed score rather than going through the token loop.
 */
object SettingsFuzzySearchEngine {

    private const val RELATED_THRESHOLD = 0.35f
    private const val TOP_MATCH_THRESHOLD = 0.70f

    fun search(
        context: Context,
        query: String,
        registry: List<SettingSpec>
    ): List<SearchResultSection> {
        val rawQuery = query.trim()
        if (rawQuery.isBlank()) return emptyList()

        val normalizedQuery = normalize(rawQuery)
        val queryTokens = normalizedQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val scoredResults = registry.mapNotNull { spec ->
            val title = spec.getTitle(context)
            val subtitle = spec.getSubtitle(context) ?: ""
            val categoryTitle = runCatching { context.getString(spec.category.titleRes) }.getOrDefault("")

            val keywordStrings = spec.keywordsRes.mapNotNull { id ->
                runCatching { context.getString(id) }.getOrNull()
            } + spec.keywordsStatic

            val score = calculateScore(
                query = normalizedQuery,
                queryTokens = queryTokens,
                title = title,
                subtitle = subtitle,
                categoryTitle = categoryTitle,
                keywords = keywordStrings
            )

            if (score >= RELATED_THRESHOLD) {
                SearchResultItem(
                    spec = spec,
                    score = score,
                    matchedTitle = title,
                    matchedSubtitle = subtitle.ifBlank { null }
                )
            } else {
                null
            }
        }.sortedByDescending { it.score }

        if (scoredResults.isEmpty()) return emptyList()

        val topMatches = scoredResults.filter { it.score >= TOP_MATCH_THRESHOLD }
        val relatedMatches = scoredResults.filter { it.score < TOP_MATCH_THRESHOLD }

        return buildList {
            if (topMatches.isNotEmpty()) {
                add(SearchResultSection(R.string.settings_search_section_top_matches, topMatches))
            }
            if (relatedMatches.isNotEmpty()) {
                add(SearchResultSection(R.string.settings_search_section_related, relatedMatches))
            }
        }
    }

    private fun calculateScore(
        query: String,
        queryTokens: List<String>,
        title: String,
        subtitle: String,
        categoryTitle: String,
        keywords: List<String>
    ): Float {
        val normTitle = normalize(title)
        val normSubtitle = normalize(subtitle)
        val normCategory = normalize(categoryTitle)
        val normKeywords = keywords.map { normalize(it) }

        if (normTitle == query) return 1.0f
        if (normTitle.startsWith(query)) return 0.95f

        for (kw in normKeywords) {
            if (kw == query) return 0.92f
            if (kw.startsWith(query)) return 0.88f
        }

        if (normTitle.contains(query)) return 0.85f

        for (kw in normKeywords) {
            if (kw.contains(query)) return 0.82f
        }

        if (normSubtitle.contains(query)) return 0.75f
        if (normCategory.contains(query)) return 0.65f

        var totalTokenScore = 0f
        for (token in queryTokens) {
            var bestTokenMatch = 0f

            for (tt in normTitle.split("\\s+".toRegex())) {
                if (tt.startsWith(token)) {
                    bestTokenMatch = max(bestTokenMatch, 0.8f)
                } else if (tt.contains(token)) {
                    bestTokenMatch = max(bestTokenMatch, 0.7f)
                } else {
                    val sim = similarity(token, tt)
                    if (sim >= 0.7f) bestTokenMatch = max(bestTokenMatch, sim * 0.75f)
                }
            }

            for (kw in normKeywords) {
                for (kwt in kw.split("\\s+".toRegex())) {
                    if (kwt.startsWith(token)) {
                        bestTokenMatch = max(bestTokenMatch, 0.75f)
                    } else {
                        val sim = similarity(token, kwt)
                        if (sim >= 0.7f) bestTokenMatch = max(bestTokenMatch, sim * 0.7f)
                    }
                }
            }

            if (bestTokenMatch < 0.6f && normSubtitle.isNotBlank()) {
                for (st in normSubtitle.split("\\s+".toRegex())) {
                    if (st.startsWith(token)) {
                        bestTokenMatch = max(bestTokenMatch, 0.6f)
                    }
                }
            }

            totalTokenScore += bestTokenMatch
        }

        val averageTokenScore = if (queryTokens.isNotEmpty()) totalTokenScore / queryTokens.size else 0f
        return averageTokenScore.coerceIn(0f, 1f)
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").trim()
    }

    private fun similarity(s1: String, s2: String): Float {
        if (s1.isBlank() || s2.isBlank()) return 0f
        if (s1 == s2) return 1.0f
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - (distance.toFloat() / max(s1.length, s2.length).toFloat())
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
