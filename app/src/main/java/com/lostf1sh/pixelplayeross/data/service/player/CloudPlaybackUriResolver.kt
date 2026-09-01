package com.lostf1sh.pixelplayeross.data.service.player

import java.util.Locale

/**
 * Chooses the playable form of a canonical cloud URI without letting a cached proxy URL bypass an
 * app-private offline copy. The generic shape keeps the ordering policy independently testable.
 */
internal suspend fun <T> resolvePreferredCloudPlaybackValue(
    original: T,
    resolveOffline: suspend () -> T?,
    resolveCachedRemote: () -> T?,
    resolveRemote: suspend () -> T?,
    onRemoteResolved: (T) -> Unit = {}
): T {
    resolveOffline()?.let { return it }
    resolveCachedRemote()?.let { return it }
    return resolveRemote()?.also(onRemoteResolved) ?: original
}

private val CANONICAL_CLOUD_SCHEMES = setOf("navidrome", "jellyfin")

/**
 * Keeps queue and persisted snapshot URIs independent from process-local proxy ports and from
 * app-private downloads which can be removed while an item is still queued.
 */
internal fun selectCanonicalCloudPlaybackUri(
    playerUri: String?,
    originalContentUri: String?,
): String? = originalContentUri
    ?.takeIf(::isCanonicalCloudPlaybackUri)
    ?: playerUri

internal fun isCanonicalCloudPlaybackUri(uri: String?): Boolean {
    if (uri.isNullOrBlank()) return false
    val separator = uri.indexOf(':')
    if (separator <= 0) return false
    return uri.substring(0, separator).lowercase(Locale.ROOT) in CANONICAL_CLOUD_SCHEMES
}
