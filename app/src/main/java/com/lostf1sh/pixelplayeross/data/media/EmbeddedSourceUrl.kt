package com.lostf1sh.pixelplayeross.data.media

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal fun findEmbeddedSourceUrl(properties: Map<String, Array<String>>): String? =
    sequenceOf("SOURCE", "URL:SOURCE")
        .mapNotNull { key -> properties.entries.firstOrNull { it.key.equals(key, ignoreCase = true) } }
        .flatMap { it.value.asSequence() }
        .firstNotNullOfOrNull(::validatedWebUrl)

private fun validatedWebUrl(value: String): String? {
    val uri = try {
        URI(value.trim())
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if ((scheme != "http" && scheme != "https") || uri.host.isNullOrEmpty() || uri.rawUserInfo != null) {
        return null
    }
    return "$scheme:${uri.toASCIIString().substringAfter(':')}"
}
