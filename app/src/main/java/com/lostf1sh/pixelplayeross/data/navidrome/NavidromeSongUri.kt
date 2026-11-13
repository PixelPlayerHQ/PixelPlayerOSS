package com.lostf1sh.pixelplayeross.data.navidrome

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Canonical `navidrome://<id>` song URIs.
 *
 * Subsonic ids are opaque strings; servers like Aurral use ids such as `song:a%20b`, so the id is
 * percent-encoded in the URI and decoded when read back. Plain `[A-Za-z0-9_-]` ids are unchanged,
 * and URIs stored before encoding (raw ids) are still read as raw ids.
 */
object NavidromeSongUri {
    private const val PREFIX = "navidrome://"
    private const val UTF_8 = "UTF-8"

    fun build(songId: String): String = PREFIX + encodeId(songId)

    /** The song id of a `navidrome://` URI, or null for any other URI. */
    fun parseId(uri: String?): String? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        val tail = uri.substring(PREFIX.length).takeIf { it.isNotEmpty() } ?: return null
        // Only the form build() writes is decoded; anything else is a raw id stored before encoding.
        return decodeId(tail)?.takeIf { encodeId(it) == tail } ?: tail
    }

    /** Percent-encodes [id] for a URI host or path segment (spaces as %20, never '+'). */
    fun encodeId(id: String): String = URLEncoder.encode(id, UTF_8).replace("+", "%20")

    private fun decodeId(encoded: String): String? =
        runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
}
