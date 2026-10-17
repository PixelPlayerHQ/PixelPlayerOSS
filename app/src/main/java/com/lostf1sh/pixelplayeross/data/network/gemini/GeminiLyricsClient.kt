package com.lostf1sh.pixelplayeross.data.network.gemini

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.model.GeminiLyricItem
import com.lostf1sh.pixelplayeross.di.FastOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLyricsClient @Inject constructor(
    @FastOkHttpClient private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "GeminiLyricsClient"
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        val PRESET_MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-flash-lite-latest",
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
        )
        private val CANDIDATE_MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Translates and romanizes a list of lyric lines using the Gemini API.
     *
     * @param lines The raw or cleaned lyric lines to process.
     * @param apiKey The user-supplied Gemini API key.
     * @param targetLanguage Target language for translation (default: "English").
     * @param preferredModel Optional model chosen by the user in settings.
     * @return Result containing list of [GeminiLyricItem] ordered 1:1 with input, or failure.
     */
    suspend fun translateAndRomanize(
        lines: List<String>,
        apiKey: String,
        targetLanguage: String = "English",
        preferredModel: String? = null
    ): Result<List<GeminiLyricItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            android.util.Log.e(TAG, "Gemini API key is blank")
            return@withContext Result.failure(IllegalArgumentException("Gemini API key is blank"))
        }
        if (lines.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val prompt = buildPrompt(lines, targetLanguage)
        val requestPayload = buildRequestJson(prompt)

        var lastError: Throwable? = null
        val modelsToTry = buildList {
            val pref = preferredModel?.trim()
            if (!pref.isNullOrEmpty()) {
                add(pref)
            }
            CANDIDATE_MODELS.forEach { model ->
                if (model != pref) add(model)
            }
        }

        for (model in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    if (!response.isSuccessful) {
                        val errorBody = response.body.string()
                        val parsedMessage = extractErrorMessage(errorBody)
                        val fullErrMsg = "HTTP $code: $parsedMessage"
                        android.util.Log.e(TAG, "Gemini API ($model) failed: $fullErrMsg")
                        Timber.tag(TAG).e("Gemini API ($model) failed: %s | raw: %s", fullErrMsg, errorBody)

                        // If 404 (model not supported/found on endpoint), try next candidate model
                        if (code == 404) {
                            lastError = IOException(fullErrMsg)
                            return@use
                        } else {
                            return@withContext Result.failure(IOException(fullErrMsg))
                        }
                    }

                    val responseBody = response.body.string()
                    if (responseBody.isBlank()) {
                        android.util.Log.e(TAG, "Gemini API returned empty response body")
                        return@withContext Result.failure(IOException("Empty response from Gemini API"))
                    }

                    val parsedItems = parseGeminiResponse(responseBody)
                    if (parsedItems != null) {
                        android.util.Log.i(TAG, "Successfully translated ${parsedItems.size} lines using $model")
                        return@withContext Result.success(parsedItems)
                    } else {
                        android.util.Log.e(TAG, "Failed to parse structured lyrics JSON: ${responseBody.take(200)}")
                        return@withContext Result.failure(IOException("Failed to parse structured lyrics from Gemini response"))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Gemini API network call failed ($model): ${e.message}", e)
                Timber.tag(TAG).e(e, "Gemini API network call failed ($model): %s", e.message)
                lastError = e
            }
        }

        Result.failure(lastError ?: IOException("Gemini request failed"))
    }

    private fun extractErrorMessage(errorBody: String): String {
        return try {
            val root = gson.fromJson(errorBody, Map::class.java)
            val errorObj = root["error"] as? Map<*, *>
            val message = errorObj?.get("message") as? String
            val status = errorObj?.get("status") as? String
            when {
                !message.isNullOrBlank() && !status.isNullOrBlank() -> "$status: $message"
                !message.isNullOrBlank() -> message
                errorBody.isNotBlank() -> errorBody.take(160)
                else -> "Unknown error"
            }
        } catch (_: Exception) {
            if (errorBody.isNotBlank()) errorBody.take(160) else "Unknown error"
        }
    }

    private fun buildPrompt(lines: List<String>, targetLanguage: String): String {
        val numberedLines = lines.mapIndexed { index, line ->
            "[$index] $line"
        }.joinToString("\n")

        return """
            You are an expert music lyric translator and phonetic romanizer.
            Given the following numbered lyric lines, provide a JSON array matching the exact same number of lines and sequence.
            For each line provide:
            - "original": the input line text exactly as given (without the numeric prefix)
            - "romanized": accurate phonetic Latin romanization (Romaji for Japanese, Pinyin with tone marks or tone-free for Chinese, Revised Romanization for Korean, Latin transliteration for Cyrillic/Greek/Arabic/Hebrew/Hindi/etc.). If the line is already in Latin script or instrumental, repeat the original line.
            - "translated": natural, lyrical translation into $targetLanguage.

            Input lines:
            $numberedLines
        """.trimIndent()
    }

    private fun buildRequestJson(promptText: String): String {
        val requestMap = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to promptText)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json",
                "temperature" to 0.2
            )
        )
        return gson.toJson(requestMap)
    }

    /**
     * Parses the Gemini API response JSON, extracting the structured [GeminiLyricItem] list.
     */
    internal fun parseGeminiResponse(jsonString: String): List<GeminiLyricItem>? {
        return try {
            val root = gson.fromJson(jsonString, Map::class.java)
            val candidates = root["candidates"] as? List<*> ?: return null
            if (candidates.isEmpty()) return null

            val firstCandidate = candidates[0] as? Map<*, *> ?: return null
            val content = firstCandidate["content"] as? Map<*, *> ?: return null
            val parts = content["parts"] as? List<*> ?: return null
            if (parts.isEmpty()) return null

            val textContent = parts.mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
                .firstOrNull { it.isNotBlank() } ?: return null

            parseStructuredLyricsJson(textContent)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error parsing Gemini API response JSON")
            null
        }
    }

    /**
     * Parses raw JSON text (potentially containing markdown code fence blocks) into a list of [GeminiLyricItem].
     */
    internal fun parseStructuredLyricsJson(text: String): List<GeminiLyricItem>? {
        return try {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            val cleanJson = if (start != -1 && end != -1 && end > start) {
                text.substring(start, end + 1)
            } else {
                text.trim()
                    .removePrefix("```json")
                    .removePrefix("```JSON")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }

            val listType = object : TypeToken<List<GeminiLyricItem>>() {}.type
            gson.fromJson<List<GeminiLyricItem>>(cleanJson, listType)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to deserialize GeminiLyricItem list: ${text.take(200)}", e)
            Timber.tag(TAG).w(e, "Failed to deserialize GeminiLyricItem list: %s", text.take(100))
            null
        }
    }
}
