package com.lostf1sh.pixelplayeross.data.jellyfin.model

import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class JellyfinCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val accessToken: String? = null,
    val userId: String? = null
) {
    companion object {
        fun empty() = JellyfinCredentials(
            serverUrl = "",
            username = "",
            password = "",
            accessToken = null,
            userId = null
        )
    }

    val isValid: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() &&
                (password.isNotBlank() || !accessToken.isNullOrBlank())

    val hasToken: Boolean
        get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()

    val normalizedHttpUrlOrNull: HttpUrl?
        get() {
            val trimmed = serverUrl.trim().trimEnd('/')
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)

            val withScheme = if (!hasScheme) {
                val host = "http://$trimmed".toHttpUrlOrNull()?.host?.lowercase()
                val scheme = if (host != null && isHttpAllowedHost(host)) "http" else "https"
                "$scheme://$trimmed"
            } else {
                trimmed
            }
            return withScheme.toHttpUrlOrNull()
        }

    val normalizedServerUrl: String
        get() = normalizedHttpUrlOrNull?.toString()?.trimEnd('/') ?: serverUrl.trim().trimEnd('/')

    fun connectionValidationError(): String? {
        val parsed = normalizedHttpUrlOrNull
            ?: return "Invalid server URL format"

        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return "Server URL must not contain embedded credentials"
        }

        // Warn about cleartext HTTP on public hosts
        if (!parsed.isHttps) {
            val host = parsed.host.lowercase()
            if (!isHttpAllowedHost(host)) {
                return "Use https:// for remote Jellyfin servers. HTTP is only allowed for local network addresses."
            }
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
