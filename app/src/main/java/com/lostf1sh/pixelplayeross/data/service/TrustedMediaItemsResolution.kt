package com.lostf1sh.pixelplayeross.data.service

import androidx.media3.common.MediaItem

internal data class TrustedMediaItemsResolution(
    val mediaItems: MutableList<MediaItem>,
    val trustedArtworkGrantItems: List<MediaItem>,
)

internal suspend fun resolveMediaItemsWithTrustedArtworkGrants(
    requestedItems: List<MediaItem>,
    trustedItemResolver: suspend (String) -> MediaItem?
): TrustedMediaItemsResolution {
    val resolvedItems = ArrayList<MediaItem>(requestedItems.size)
    val trustedArtworkGrantItems = ArrayList<MediaItem>()

    for (requestedItem in requestedItems) {
        val trustedItem = trustedItemResolver(requestedItem.mediaId)
        if (trustedItem != null) {
            resolvedItems += trustedItem
            trustedArtworkGrantItems += trustedItem
        } else {
            // Caller-supplied metadata is untrusted and must never drive provider grants.
            resolvedItems += requestedItem
        }
    }

    return TrustedMediaItemsResolution(
        mediaItems = resolvedItems,
        trustedArtworkGrantItems = trustedArtworkGrantItems
    )
}
