package com.lostf1sh.pixelplayeross.presentation.viewmodel

/** Identifies an album section across reloads; also the base of its LazyColumn item keys. */
internal fun ArtistAlbumSection.collapseKey(): String = "artist_album_${albumId}_${title}"

/**
 * Whether an album section on the artist page shows its songs.
 *
 * A section the user toggled on this visit keeps that choice ([overrides], by [collapseKey]).
 * Otherwise the "start with albums collapsed" preference decides, except for an artist with a
 * single album: collapsing the only section would just hide every song, so it opens expanded.
 */
internal fun isAlbumSectionExpanded(
    key: String,
    overrides: Map<String, Boolean>,
    collapsedByDefault: Boolean,
    sectionCount: Int
): Boolean = overrides[key] ?: !(collapsedByDefault && sectionCount > 1)

/**
 * Whether any of the artist's album sections, given by their [collapseKey]s, shows its songs:
 * the "Collapse all / Expand all" button collapses while one does.
 */
internal fun isAnyAlbumSectionExpanded(
    sectionKeys: List<String>,
    overrides: Map<String, Boolean>,
    collapsedByDefault: Boolean
): Boolean = sectionKeys.any { key ->
    isAlbumSectionExpanded(key, overrides, collapsedByDefault, sectionCount = sectionKeys.size)
}
