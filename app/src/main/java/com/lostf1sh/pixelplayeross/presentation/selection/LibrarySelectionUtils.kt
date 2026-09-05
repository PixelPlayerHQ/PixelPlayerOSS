package com.lostf1sh.pixelplayeross.presentation.selection

import com.lostf1sh.pixelplayeross.data.model.StorageFilter

/** Mirrors the filter used by the visible library when local media is globally hidden. */
internal fun effectiveLibraryStorageFilter(
    selected: StorageFilter,
    hideLocalMedia: Boolean,
): StorageFilter = if (hideLocalMedia) StorageFilter.ONLINE else selected

/** Toggles one item while preserving the order in which categories were selected. */
internal fun <T, K> toggleOrderedSelection(
    current: List<T>,
    item: T,
    keyOf: (T) -> K
): List<T> {
    val itemKey = keyOf(item)
    return if (current.any { keyOf(it) == itemKey }) {
        current.filterNot { keyOf(it) == itemKey }
    } else {
        current + item
    }
}

/** Adds all previously-unselected items in candidate order. */
internal fun <T, K> appendDistinctSelection(
    current: List<T>,
    candidates: Iterable<T>,
    keyOf: (T) -> K
): List<T> {
    val result = current.toMutableList()
    val selectedKeys = current.mapTo(linkedSetOf(), keyOf)
    candidates.forEach { candidate ->
        if (selectedKeys.add(keyOf(candidate))) {
            result += candidate
        }
    }
    return result
}

/** Returns the one-based selection position used by the category selection badges. */
internal fun <T, K> selectionIndex(
    current: List<T>,
    key: K,
    keyOf: (T) -> K
): Int? {
    val index = current.indexOfFirst { keyOf(it) == key }
    return index.takeIf { it >= 0 }?.plus(1)
}

/**
 * Flattens resolved album/artist song groups without performing a destructive action twice when
 * the same song belongs to more than one selected artist.
 */
internal fun <T, K> flattenDistinctGroups(
    groups: Iterable<Iterable<T>>,
    keyOf: (T) -> K
): List<T> {
    val result = mutableListOf<T>()
    val seenKeys = hashSetOf<K>()
    groups.forEach { group ->
        group.forEach { item ->
            if (seenKeys.add(keyOf(item))) {
                result += item
            }
        }
    }
    return result
}
