package com.lostf1sh.pixelplayeross.data.stream

import io.ktor.http.HttpStatusCode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Lightweight validation utilities shared by cloud streaming proxies.
 *
 * The checks are intentionally cheap (string/number validation only) so they
 * can run on every request without adding measurable overhead.
 */
object CloudStreamSecurity {
    const val MAX_STREAM_CONTENT_LENGTH_BYTES: Long = 2L * 1024L * 1024L * 1024L

    private const val MAX_RANGE_HEADER_LENGTH = 64
    private const val MAX_RANGE_VALUE_BYTES = 8L * 1024L * 1024L * 1024L

    private const val MAX_NAVIDROME_SONG_ID_LENGTH = 1024
    private val JELLYFIN_ITEM_ID_REGEX = Regex("^[A-Za-z0-9]{1,100}$")
    private val FORBIDDEN_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]")
    private val LOCAL_SERVER_DNS_SUFFIXES =
        listOf(".local", ".lan", ".home", ".internal", ".home.arpa", ".ts.net")
    private val EXTRA_ALLOWED_AUDIO_TYPES = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/mp4",
        "video/mp4",
        // Containers that media servers commonly report under non-audio/* types even when the
        // stream is audio-only (.ogg/.oga as application/ogg, .webm/.weba/.mka as video/webm or
        // video/x-matroska, .3gp as video/3gpp).
        "application/ogg",
        "video/ogg",
        "video/webm",
        "video/x-matroska",
        "video/3gpp",
        "application/x-flac"
    )

    data class RangeHeaderValidation(
        val isValid: Boolean,
        val normalizedHeader: String? = null,
        val startInclusive: Long? = null,
        val endInclusive: Long? = null,
        val isSuffixRange: Boolean = false
    )

    // Subsonic ids are opaque (Aurral uses e.g. `song:a%20b`); they are always URL-encoded
    // before use, so only reject blank, oversized or control-character ids.
    fun validateNavidromeSongId(songId: String): Boolean =
        songId.length <= MAX_NAVIDROME_SONG_ID_LENGTH &&
            songId.isNotBlank() &&
            songId.none { it.isISOControl() }

    fun validateJellyfinItemId(itemId: String): Boolean = JELLYFIN_ITEM_ID_REGEX.matches(itemId)

    fun validateRangeHeader(rawHeader: String?): RangeHeaderValidation {
        if (rawHeader.isNullOrBlank()) {
            return RangeHeaderValidation(isValid = true)
        }

        val header = rawHeader.trim()
        if (header.length > MAX_RANGE_HEADER_LENGTH) {
            return RangeHeaderValidation(isValid = false)
        }
        if (!header.startsWith("bytes=") || header.contains(",")) {
            return RangeHeaderValidation(isValid = false)
        }

        val payload = header.removePrefix("bytes=")
        val dashIndex = payload.indexOf('-')
        if (dashIndex <= -1 || payload.indexOf('-', dashIndex + 1) != -1) {
            return RangeHeaderValidation(isValid = false)
        }

        val startPart = payload.substring(0, dashIndex).trim()
        val endPart = payload.substring(dashIndex + 1).trim()

        if (startPart.isEmpty() && endPart.isEmpty()) {
            return RangeHeaderValidation(isValid = false)
        }
        if (startPart.isNotEmpty() && !startPart.all(Char::isDigit)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (endPart.isNotEmpty() && !endPart.all(Char::isDigit)) {
            return RangeHeaderValidation(isValid = false)
        }

        val start = startPart.toLongOrNull()
        val end = endPart.toLongOrNull()

        if (start != null && (start < 0 || start > MAX_RANGE_VALUE_BYTES)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (end != null && (end < 0 || end > MAX_RANGE_VALUE_BYTES)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (start != null && end != null && start > end) {
            return RangeHeaderValidation(isValid = false)
        }

        return RangeHeaderValidation(
            isValid = true,
            normalizedHeader = "bytes=$startPart-$endPart",
            startInclusive = start,
            endInclusive = end,
            isSuffixRange = start == null && end != null
        )
    }

    fun isSupportedAudioContentType(contentTypeHeader: String?): Boolean {
        if (contentTypeHeader.isNullOrBlank()) {
            return true
        }
        val normalized = contentTypeHeader.substringBefore(';').trim().lowercase()
        if (normalized.startsWith("audio/")) {
            return true
        }
        return normalized in EXTRA_ALLOWED_AUDIO_TYPES
    }

    fun isAcceptableContentLength(contentLengthHeader: String?): Boolean {
        if (contentLengthHeader.isNullOrBlank()) {
            return true
        }
        val parsed = contentLengthHeader.toLongOrNull() ?: return false
        return parsed in 0..MAX_STREAM_CONTENT_LENGTH_BYTES
    }

    fun isSafeRemoteStreamUrl(
        url: String,
        allowedHostSuffixes: Set<String> = emptySet(),
        allowHttpForAllowedHosts: Boolean = false
    ): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val host = httpUrl.host.lowercase()

        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return false

        if (host in FORBIDDEN_HOSTS) return false

        if (allowedHostSuffixes.isEmpty()) return false
        if (!hostMatchesAllowedSuffix(host, allowedHostSuffixes)) return false

        return when (httpUrl.scheme.lowercase()) {
            "https" -> true
            "http" -> allowHttpForAllowedHosts
            else -> false
        }
    }

    fun mapUpstreamStatusToProxyStatus(code: Int): HttpStatusCode {
        return when (code) {
            401 -> HttpStatusCode.Unauthorized
            403 -> HttpStatusCode.Forbidden
            404 -> HttpStatusCode.NotFound
            408 -> HttpStatusCode.RequestTimeout
            416 -> HttpStatusCode(416, "Range Not Satisfiable")
            429 -> HttpStatusCode(429, "Too Many Requests")
            in 500..599 -> HttpStatusCode.BadGateway
            else -> HttpStatusCode.BadGateway
        }
    }

    private fun hostMatchesAllowedSuffix(host: String, allowedHostSuffixes: Set<String>): Boolean {
        if (allowedHostSuffixes.isEmpty()) return false
        return allowedHostSuffixes.any { suffix ->
            val normalized = suffix.lowercase()
            host == normalized || host.endsWith(".$normalized")
        }
    }

    /**
     * Hosts that can reasonably identify a self-hosted server reached over the local network or a VPN:
     * localhost, local DNS suffixes, private/loopback/CGNAT IPv4 literals, loopback, link-local and
     * unique-local IPv6 literals, and single-label names. `.lan`, `.home` and `.internal` are
     * commonly assigned by home routers, `.home.arpa` is the reserved home-network suffix and
     * `.ts.net` is Tailscale MagicDNS. IPv6 literals may be passed with or without brackets.
     */
    internal fun isLocalServerHost(host: String): Boolean {
        val normalized = host.lowercase().removePrefix("[").removeSuffix("]")
        if (normalized.isEmpty()) return false
        if (normalized == "localhost") return true
        if (LOCAL_SERVER_DNS_SUFFIXES.any { normalized.endsWith(it) }) return true
        if (isPrivateIpv4Literal(normalized)) return true
        // IPv6 literals never contain dots, so they must be classified before the single-label rule.
        if (normalized.contains(':')) return isPrivateIpv6Literal(normalized)
        // Single-label hostnames have no public DNS meaning; they only resolve via the local
        // resolver (router DNS, hosts file, mDNS/NetBIOS).
        return !normalized.contains('.')
    }

    /** Used by login-time server-URL validation to decide whether cleartext HTTP is acceptable. */
    internal fun isPrivateIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false

        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]

        return first == 0 ||
            first == 10 ||
            first == 127 ||
            (first == 100 && second in 64..127) ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    /**
     * Matches loopback (::1), link-local (fe80::/10) and unique-local (fc00::/7) IPv6 literals.
     * Hosts come from [okhttp3.HttpUrl.host], which is lowercase and compressed, so the first group
     * must have all four hex digits to fall in either range ("fc::1" is 00fc::1, a public address).
     */
    private fun isPrivateIpv6Literal(host: String): Boolean {
        if (host == "::1" || host == "0:0:0:0:0:0:0:1") return true
        val firstGroup = host.substringBefore(':')
        if (firstGroup.length != 4 || !firstGroup.all { it in '0'..'9' || it in 'a'..'f' }) return false
        val group = firstGroup.toInt(16)
        return (group and 0xfe00) == 0xfc00 || (group and 0xffc0) == 0xfe80
    }
}
