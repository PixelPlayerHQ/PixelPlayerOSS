package com.lostf1sh.pixelplayeross.data.backup.validation

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentSanitizer @Inject constructor() {

    companion object {
        const val DEFAULT_MAX_LENGTH = 10_000
        // Control characters except tab (\t), newline (\n), carriage return (\r)
        private val CONTROL_CHAR_REGEX = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")
    }

    fun sanitizeString(input: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        var result = input.trim()
        if (result.length > maxLength) {
            result = result.take(maxLength)
        }
        result = CONTROL_CHAR_REGEX.replace(result, "")
        return result
    }

    fun sanitizeUrl(url: String, maxLength: Int = 2000): String {
        val sanitized = sanitizeString(url, maxLength)
        // Only allow http/https URLs
        if (sanitized.isNotEmpty() &&
            !sanitized.startsWith("https://") &&
            !sanitized.startsWith("http://")) {
            return ""
        }
        return sanitized
    }

    fun isValidModuleKey(key: String): Boolean {
        return key.matches(Regex("^[a-z_]+$")) && key.length <= 50
    }
}
