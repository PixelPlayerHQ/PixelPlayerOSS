package com.lostf1sh.pixelplayeross.data.navidrome.model

import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Represents authentication credentials for a Navidrome/Subsonic server.
 *
 * Navidrome supports two authentication methods:
 * 1. Password in URL (p=xxx) - not recommended for security
 * 2. Token-based authentication (t=xxx&s=xxx) - recommended
 *
 * @property serverUrl The base URL of the Navidrome server (e.g., "https://music.example.com")
 * @property username The username for authentication
 * @property password The password (stored securely, used to generate tokens)
 * @property clientId The client identifier sent to the server (default: "PixelPlayerOSS")
 */
data class NavidromeCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val clientId: String = "PixelPlayerOSS"
) {
    companion object {
        /**
         * The Subsonic API version supported by this implementation.
         */
        const val API_VERSION = "1.16.1"

        /**
         * Creates an empty credentials object.
         */
        fun empty() = NavidromeCredentials(
            serverUrl = "",
            username = "",
            password = "",
            clientId = "PixelPlayerOSS"
        )
    }

    /**
     * Returns true if the credentials have all required fields populated.
     */
    val isValid: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /**
     * Returns the parsed and normalized server URL, or null if it is invalid.
     */
    val normalizedHttpUrlOrNull: HttpUrl?
        get() = serverUrl.trim().trimEnd('/').toHttpUrlOrNull()

    /**
     * Returns the normalized server URL (without trailing slash).
     */
    val normalizedServerUrl: String
        get() = normalizedHttpUrlOrNull?.toString()?.trimEnd('/') ?: serverUrl.trim().trimEnd('/')

    /**
     * Returns a validation error for connection setup, or null when the URL is acceptable.
     */
    fun connectionValidationError(requireHttps: Boolean = false): String? {
        val httpUrl = normalizedHttpUrlOrNull ?: return "Enter a valid server URL."
        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
            return "Server URL must not include embedded credentials."
        }
        if (requireHttps && !httpUrl.isHttps) {
            return "Use an https:// server URL for Navidrome/Subsonic."
        }
        if (!httpUrl.isHttps && !isHttpAllowedHost(httpUrl.host.lowercase())) {
            return "Use https:// for remote Navidrome/Subsonic servers. HTTP is only allowed for local network addresses."
        }
        return null
    }

    private fun isHttpAllowedHost(host: String): Boolean {
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host.endsWith(".local") ||
            host.endsWith(".ts.net") ||
            !host.contains('.') ||
            CloudStreamSecurity.isPrivateIpv4Literal(host)
    }
}
