package com.lostf1sh.pixelplayeross.presentation.settings.search

import android.content.Context
import com.lostf1sh.pixelplayeross.R
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object SettingsFuzzySearchEngine {

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

            if (score >= 0.35f) {
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

        val topMatches = scoredResults.filter { it.score >= 0.70f }
        val relatedMatches = scoredResults.filter { it.score in 0.35f..<0.70f }

        val sections = mutableListOf<SearchResultSection>()
        if (topMatches.isNotEmpty()) {
            sections.add(
                SearchResultSection(
                    titleRes = R.string.settings_search_section_top_matches,
                    items = topMatches
                )
            )
        }
        if (relatedMatches.isNotEmpty()) {
            sections.add(
                SearchResultSection(
                    titleRes = R.string.settings_search_section_related,
                    items = relatedMatches
                )
            )
        }

        return sections
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

        // Exact match
        if (normTitle == query) return 1.0f

        // Title prefix match
        if (normTitle.startsWith(query)) return 0.95f

        // Keyword exact or prefix match
        for (kw in normKeywords) {
            if (kw == query) return 0.92f
            if (kw.startsWith(query)) return 0.88f
        }

        // Substring match in title
        if (normTitle.contains(query)) return 0.85f

        // Substring match in keywords
        for (kw in normKeywords) {
            if (kw.contains(query)) return 0.82f
        }

        // Substring match in subtitle
        if (normSubtitle.contains(query)) return 0.75f

        // Substring match in category
        if (normCategory.contains(query)) return 0.65f

        // Token / Fuzzy score
        var totalTokenScore = 0f
        for (token in queryTokens) {
            var bestTokenMatch = 0f

            // Title tokens match
            val titleTokens = normTitle.split("\\s+".toRegex())
            for (tt in titleTokens) {
                if (tt.startsWith(token)) {
                    bestTokenMatch = max(bestTokenMatch, 0.8f)
                } else if (tt.contains(token)) {
                    bestTokenMatch = max(bestTokenMatch, 0.7f)
                } else {
                    val sim = similarity(token, tt)
                    if (sim >= 0.7f) bestTokenMatch = max(bestTokenMatch, sim * 0.75f)
                }
            }

            // Keyword tokens match
            for (kw in normKeywords) {
                val kwTokens = kw.split("\\s+".toRegex())
                for (kwt in kwTokens) {
                    if (kwt.startsWith(token)) {
                        bestTokenMatch = max(bestTokenMatch, 0.75f)
                    } else {
                        val sim = similarity(token, kwt)
                        if (sim >= 0.7f) bestTokenMatch = max(bestTokenMatch, sim * 0.7f)
                    }
                }
            }

            // Subtitle tokens match
            if (bestTokenMatch < 0.6f && normSubtitle.isNotBlank()) {
                val subTokens = normSubtitle.split("\\s+".toRegex())
                for (st in subTokens) {
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
        val temp = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return temp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").trim()
    }

    private fun similarity(s1: String, s2: String): Float {
        if (s1.isBlank() || s2.isBlank()) return 0f
        if (s1 == s2) return 1.0f
        val distance = levenshteinDistance(s1, s2)
        val maxLength = max(s1.length, s2.length)
        return 1.0f - (distance.toFloat() / maxLength.toFloat())
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
                    min(
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
